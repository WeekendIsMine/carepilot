package com.carepilot.chat.ws;

import com.carepilot.chat.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * WebSocket 主处理器：握手后按帧类型分发（SEND / APPROVAL / PING），
 * 发送与接收都在这里和 ChatService、审批服务对接。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final ChatService chatService;
    private final WsSessionRegistry registry;
    @Qualifier("chatExecutor")
    private final Executor chatExecutor;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = userId(session);
        registry.register(userId, session);
        FrameSink sink = sink(session);
        Map<String, Object> ready = WsFrames.of(WsFrameType.READY);
        ready.put("userId", userId);
        ready.put("role", session.getAttributes().get("role"));
        sink.send(ready);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {

        String traceId = "ws-" + session.getId().substring(0, Math.min(8, session.getId().length()));
        org.slf4j.MDC.put(com.carepilot.common.trace.TraceIdFilter.MDC_KEY, traceId);
        try {
            doHandle(session, message);
        } finally {
            org.slf4j.MDC.remove(com.carepilot.common.trace.TraceIdFilter.MDC_KEY);
        }
    }

    private void doHandle(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = userId(session);
        FrameSink sink = sink(session);

        Map<String, Object> frame;
        try {
            frame = objectMapper.readValue(message.getPayload(), Map.class);
        } catch (Exception e) {
            sink.send(WsFrames.error(40000, "帧格式错误"));
            return;
        }
        String type = String.valueOf(frame.get("type"));

        if ("PING".equalsIgnoreCase(type)) {
            sink.send(WsFrames.of(WsFrameType.PONG));
            return;
        }
        if (!"SEND".equalsIgnoreCase(type)) {
            sink.send(WsFrames.error(40000, "未知帧类型：" + type));
            return;
        }

        Object sid = frame.get("sessionId");
        String content = (String) frame.get("content");
        if (sid == null || content == null || content.isBlank()) {
            sink.send(WsFrames.error(40000, "sessionId 与 content 不能为空"));
            return;
        }
        Long sessionId = Long.valueOf(String.valueOf(sid));
        String clientMsgId = (String) frame.getOrDefault("clientMsgId", "c-" + System.nanoTime());

        chatExecutor.execute(() -> chatService.handleSend(userId, sessionId, clientMsgId, content, sink));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.unregister(userId(session), session);
    }

    private Long userId(WebSocketSession session) {
        return (Long) session.getAttributes().get("userId");
    }

    private FrameSink sink(WebSocketSession session) {
        return frame -> {
            try {
                String json = objectMapper.writeValueAsString(frame);
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(json));
                    }
                }
            } catch (Exception e) {
                log.warn("ws send failed sid={}: {}", session.getId(), e.getMessage());
            }
        };
    }
}