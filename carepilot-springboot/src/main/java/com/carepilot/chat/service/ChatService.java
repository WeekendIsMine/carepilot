package com.carepilot.chat.service;

import com.carepilot.chat.entity.ChatMessage;
import com.carepilot.chat.entity.ChatSession;
import com.carepilot.chat.reply.ReplyContext;
import com.carepilot.chat.reply.ReplyEngine;
import com.carepilot.chat.reply.ReplyResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.carepilot.chat.transfer.TransferService;
import com.carepilot.chat.ws.FrameSink;
import com.carepilot.chat.ws.WsSessionRegistry;
import com.carepilot.chat.ws.WsFrameType;
import com.carepilot.chat.ws.WsFrames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 一条用户消息的处理入口（WebSocket 收到 SEND 后进来）。
 * 幂等落库 → ACK → 交给 ReplyEngine 流式回复 → 落库并推 AI_DONE。
 * 人工服务中不跑 AI，只把消息转给坐席；已结束的会话会自动重开。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionService sessionService;
    private final ChatMessageService messageService;
    private final ReplyEngine replyEngine;
    private final ObjectMapper objectMapper;
    private final TransferService transferService;
    private final WsSessionRegistry wsRegistry;

    /** 处理一条用户消息：幂等去重、分流（AI / 排队 / 人工）、异常兜底 */
    public void handleSend(Long userId, Long sessionId, String clientMsgId, String content, FrameSink sink) {

        org.slf4j.MDC.put(com.carepilot.common.trace.TraceIdFilter.MDC_KEY, "ws-" + sessionId);
        long start = System.currentTimeMillis();
        try {
            ChatSession session = sessionService.getOwned(userId, sessionId);
            // 已结束的会话再发消息：自动重开，历史不变，相当于「继续上次的对话」
            if ("CLOSED".equals(session.getStatus())) {
                session = sessionService.reopen(userId, sessionId);
                sink.send(WsFrames.of(WsFrameType.SESSION_REOPENED));
            }

            // 幂等：前端重发同一条消息（clientMsgId 相同）时只落库一次
            ChatMessage existing = messageService.findUserMessage(sessionId, clientMsgId);
            if (existing == null) {
                messageService.append(sessionId, "USER", content, clientMsgId, null, null);
            }
            sessionService.touch(sessionId, content);

            // 人工服务中 AI 闭嘴：只把消息转发给坐席，不走大模型
            if ("AGENT".equals(session.getStatus())) {
                Map<String, Object> ackFrame = WsFrames.of(WsFrameType.ACK, "clientMsgId", clientMsgId);
                ackFrame.put("sessionId", sessionId);
                sink.send(ackFrame);
                Map<String, Object> relay = WsFrames.of(WsFrameType.AGENT_MESSAGE);
                relay.put("sessionId", sessionId);
                relay.put("from", "USER");
                relay.put("content", content);
                relay.put("userId", userId);
                if (session.getAgentId() != null) {
                    wsRegistry.sendToUser(session.getAgentId(), relay);
                }
                return;
            }

            // 排队中也不让 AI 接话，只回一个排队位置，避免用户以为已经在和机器人聊
            if ("WAITING".equals(session.getStatus())) {
                Map<String, Object> ackFrame = WsFrames.of(WsFrameType.ACK, "clientMsgId", clientMsgId);
                ackFrame.put("sessionId", sessionId);
                sink.send(ackFrame);
                Map<String, Object> q = WsFrames.of(WsFrameType.QUEUE_STATUS);
                q.put("position", transferService.queuePosition(session.getPriority(), sessionId));
                q.put("reason", session.getTransferReason());
                sink.send(q);
                return;
            }

            Map<String, Object> ack = WsFrames.of(WsFrameType.ACK, "clientMsgId", clientMsgId);
            ack.put("sessionId", sessionId);
            sink.send(ack);

            List<ChatMessage> history = messageService.history(sessionId, 20);

            ReplyResult result = replyEngine.reply(ReplyContext.builder()
                    .sessionId(sessionId)
                    .userId(userId)
                    .userContent(content)
                    .history(history)
                    .sink(sink)
                    .build());

            String answer = result.text();
            String citationsJson = result.citations().isEmpty()
                    ? null : objectMapper.writeValueAsString(result.citations());
            ChatMessage ai = messageService.append(sessionId, "AI", answer, null, null, citationsJson);
            ai.setLatencyMs((int) (System.currentTimeMillis() - start));

            Map<String, Object> done = WsFrames.of(WsFrameType.AI_DONE);
            done.put("messageId", ai.getId());
            done.put("sessionId", sessionId);
            done.put("content", answer);
            done.put("citations", result.citations());
            done.put("latencyMs", ai.getLatencyMs());
            sink.send(done);
            sessionService.touch(sessionId, answer);
        } catch (Exception e) {
            log.error("handleSend failed session={}", sessionId, e);

            // 模型调用失败不要把原始异常抛给用户：翻成中文提示，并作为一条 AI 消息落库
            String friendly = com.carepilot.chat.reply.LlmErrors.friendly(e);
            try {
                ChatMessage ai = messageService.append(sessionId, "AI", friendly, null, null, null);
                Map<String, Object> done = WsFrames.of(WsFrameType.AI_DONE);
                done.put("messageId", ai.getId());
                done.put("sessionId", sessionId);
                done.put("content", friendly);
                done.put("citations", java.util.List.of());
                done.put("degraded", true);
                sink.send(done);
            } catch (Exception inner) {
                log.error("failed to persist friendly error message", inner);
                sink.send(WsFrames.error(50000, "AI 服务暂时不可用，请稍后重试或转人工"));
            }
        } finally {
            org.slf4j.MDC.remove(com.carepilot.common.trace.TraceIdFilter.MDC_KEY);
        }
    }
}