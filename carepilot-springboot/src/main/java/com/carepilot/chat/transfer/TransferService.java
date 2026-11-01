package com.carepilot.chat.transfer;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carepilot.chat.entity.ChatMessage;
import com.carepilot.chat.entity.ChatSession;
import com.carepilot.chat.mapper.ChatSessionMapper;
import com.carepilot.chat.service.ChatMessageService;
import com.carepilot.chat.ws.WsFrameType;
import com.carepilot.chat.ws.WsFrames;
import com.carepilot.chat.ws.WsSessionRegistry;
import com.carepilot.common.api.ResultCode;
import com.carepilot.common.exception.BizException;
import com.carepilot.user.entity.SysUser;
import com.carepilot.user.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 转人工：排队、取消排队、坐席接管、结束会话，以及交接摘要。
 * 排队按「优先级 + 等待时间」排序；接管要校验坐席的最大并发数。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private static final String ONLINE_KEY = "carepilot:agent:online:";
    private static final Duration HEARTBEAT_TTL = Duration.ofSeconds(90);

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageService messageService;
    private final SysUserMapper userMapper;
    private final WsSessionRegistry wsRegistry;
    private final StringRedisTemplate redis;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public static final Map<String, Integer> PRIORITY = Map.of(
            "COMPLAINT", 100,
            "EMOTION", 80,
            "REFUND", 50,
            "POLICY", 30,
            "OTHER", 10);

    private static String truncate(String text, int max) {
        if (text == null) return null;
        return text.length() <= max ? text : text.substring(0, max);
    }

    @Transactional
    public int requestHuman(Long userId, Long sessionId, String reason, String reasonCode) {
        ChatSession s = sessionMapper.selectById(sessionId);
        if (s == null || !s.getUserId().equals(userId)) throw BizException.notFound("会话不存在");
        if ("AGENT".equals(s.getStatus())) return 0;

        // 转人工优先级由原因决定（投诉 > 退款 > 其它），排队时按优先级 + 时间排序
        String code = reasonCode == null ? "OTHER" : reasonCode.trim().toUpperCase();
        int priority = PRIORITY.getOrDefault(code, 10);
        // 原因是大模型写的一整句话，可能很长，库里字段放不下会直接报 SQL 错
        String safeReason = truncate(reason, 200);
        ChatSession patch = new ChatSession();
        patch.setId(sessionId);
        patch.setStatus("WAITING");
        patch.setTransferReason(safeReason);
        patch.setPriority(priority);
        sessionMapper.updateById(patch);

        int position = queuePosition(priority, sessionId);
        Map<String, Object> frame = WsFrames.of(WsFrameType.QUEUE_STATUS);
        frame.put("position", position);
        frame.put("reason", safeReason);
        frame.put("priority", priority);
        wsRegistry.sendToUser(userId, frame);
        notifyQueueChanged();
        log.info("session {} queued for human, reason={} priority={} pos={}", sessionId, safeReason, priority, position);
        return position;
    }

    @Transactional
    public void cancelTransfer(Long userId, Long sessionId) {
        ChatSession s = sessionMapper.selectById(sessionId);
        if (s == null || !s.getUserId().equals(userId)) throw BizException.notFound("会话不存在");
        // 已结束的会话不能再取消排队，给出明确提示而不是「当前不在排队中」
        if ("CLOSED".equals(s.getStatus())) throw BizException.conflict("会话已结束，无法取消排队");
        if (!"WAITING".equals(s.getStatus())) throw BizException.conflict("当前不在排队中");

        sessionMapper.update(null, Wrappers.<ChatSession>lambdaUpdate()
                .set(ChatSession::getStatus, "AI")
                .set(ChatSession::getTransferReason, null)
                .set(ChatSession::getPriority, 0)
                .eq(ChatSession::getId, sessionId)
                .eq(ChatSession::getStatus, "WAITING"));

        wsRegistry.sendToUser(userId, WsFrames.of(WsFrameType.QUEUE_CANCELED));
        notifyQueueChanged();
        log.info("session {} canceled transfer", sessionId);
    }

    public int queuePosition(int priority, Long sessionId) {
        Long ahead = sessionMapper.selectCount(Wrappers.<ChatSession>lambdaQuery()
                .eq(ChatSession::getStatus, "WAITING")
                .and(w -> w.gt(ChatSession::getPriority, priority)
                        .or(x -> x.eq(ChatSession::getPriority, priority)
                                .lt(ChatSession::getId, sessionId))));
        return ahead == null ? 1 : ahead.intValue() + 1;
    }

    public List<ChatSession> waiting() {
        return sessionMapper.selectList(Wrappers.<ChatSession>lambdaQuery()
                .eq(ChatSession::getStatus, "WAITING")
                .orderByDesc(ChatSession::getPriority)
                .orderByAsc(ChatSession::getId)
                .last("limit 50"));
    }

    public List<ChatSession> mySessions(Long agentId) {
        return sessionMapper.selectList(Wrappers.<ChatSession>lambdaQuery()
                .eq(ChatSession::getAgentId, agentId)
                .eq(ChatSession::getStatus, "AGENT")
                .orderByDesc(ChatSession::getUpdatedAt)
                .last("limit 20"));
    }

    @Transactional
    public ChatSession accept(Long agentId, Long sessionId) {
        SysUser agent = userMapper.selectById(agentId);
        if (agent == null) throw BizException.notFound("坐席不存在");

        ChatSession s = sessionMapper.selectById(sessionId);
        if (s == null) throw BizException.notFound("会话不存在");
        if ("AGENT".equals(s.getStatus())) throw BizException.conflict("会话已被其他坐席接管");
        if (!"WAITING".equals(s.getStatus())) throw BizException.conflict("会话不在排队中");

        // 坐席有最大并发接待数，超了不允许再接管
        Long serving = sessionMapper.selectCount(Wrappers.<ChatSession>lambdaQuery()
                .eq(ChatSession::getAgentId, agentId)
                .eq(ChatSession::getStatus, "AGENT"));
        int max = agent.getMaxConcurrent() == null ? 5 : agent.getMaxConcurrent();
        if (serving != null && serving >= max) {
            throw new BizException(ResultCode.CONFLICT, "已达到最大接待数 " + max);
        }

        ChatSession patch = new ChatSession();
        patch.setId(sessionId);
        patch.setStatus("AGENT");
        patch.setAgentId(agentId);
        patch.setUnreadForAgent(0);
        sessionMapper.updateById(patch);

        Map<String, Object> frame = WsFrames.of(WsFrameType.AGENT_JOINED);
        frame.put("agentId", agentId);
        frame.put("agentName", agent.getNickname());
        wsRegistry.sendToUser(s.getUserId(), frame);

        notifyQueueChanged();
        log.info("agent {} accepted session {}", agentId, sessionId);

        Long sid = sessionId;
        Long aid = agentId;
        // 交接摘要要调大模型、很慢，所以异步生成：接管接口立即返回，算完再推给坐席
        java.util.concurrent.CompletableFuture.runAsync(() -> {

            java.util.Map<String, String> mdc = org.slf4j.MDC.getCopyOfContextMap();
            if (mdc != null) org.slf4j.MDC.setContextMap(mdc);
            try {
                String summary = buildHandoffSummary(sid);
                ChatSession sum = new ChatSession();
                sum.setId(sid);
                sum.setAiSummary(writeJson(Map.of("summary", summary, "at", LocalDateTime.now().toString())));
                sessionMapper.updateById(sum);

                Map<String, Object> f = WsFrames.of(WsFrameType.SESSION_SUMMARY);
                f.put("sessionId", sid);
                f.put("summary", summary);
                wsRegistry.sendToUser(aid, f);
                log.info("handoff summary ready session={} len={}", sid, summary.length());
            } catch (Exception e) {
                log.warn("handoff summary failed session={}: {}", sid, e.getMessage());
            } finally {
                org.slf4j.MDC.clear();
            }
        });

        return sessionMapper.selectById(sessionId);
    }

    public String buildHandoffSummary(Long sessionId) {
        List<ChatMessage> history = messageService.history(sessionId, 8);
        if (history.isEmpty()) return "无历史对话";
        StringBuilder raw = new StringBuilder();
        for (ChatMessage m : history) {
            raw.append("USER".equals(m.getRole()) ? "用户：" : "客服：")
               .append(m.getContent() == null ? "" : m.getContent().replace("\n", " "))
               .append("\n");
        }
        try {
            String prompt = """
                    下面是一段电商售后对话。请用 3 句话总结，供人工客服接手时快速了解情况。
                    要求：包含用户诉求、涉及订单/商品、AI 已做了什么、还差什么。不要寒暄。
                    对话：
                    %s
                    """.formatted(raw);
            String out = chatModel.call(prompt);
            return out == null || out.isBlank() ? raw.toString() : out.trim();
        } catch (Exception e) {
            log.warn("handoff summary failed: {}", e.getMessage());
            return raw.toString();
        }
    }

    public ChatSession summaryOf(Long sessionId) {
        return sessionMapper.selectById(sessionId);
    }

    public void heartbeat(Long agentId) {
        redis.opsForValue().set(ONLINE_KEY + agentId, "1", HEARTBEAT_TTL);
        SysUser patch = new SysUser();
        patch.setId(agentId);
        patch.setAgentStatus("ONLINE");
        userMapper.updateById(patch);
    }

    public boolean isOnline(Long agentId) {
        return Boolean.TRUE.equals(redis.hasKey(ONLINE_KEY + agentId));
    }

    @Transactional
    public void endByUser(Long userId, Long sessionId) {
        ChatSession s = sessionMapper.selectById(sessionId);
        if (s == null || !s.getUserId().equals(userId)) throw BizException.notFound("会话不存在");
        if (!"AGENT".equals(s.getStatus())) throw BizException.conflict("当前不在人工服务中");

        Long agentId = s.getAgentId();
        sessionMapper.update(null, Wrappers.<ChatSession>lambdaUpdate()
                .set(ChatSession::getStatus, "AI")
                .set(ChatSession::getAgentId, null)
                .set(ChatSession::getTransferReason, null)
                .set(ChatSession::getPriority, 0)
                .set(ChatSession::getUnreadForAgent, 0)
                .eq(ChatSession::getId, sessionId));

        messageService.append(sessionId, "SYSTEM", "用户已结束人工服务，继续由 AI 接待", null, null, null);

        Map<String, Object> toUser = WsFrames.of(WsFrameType.AGENT_LEFT);
        toUser.put("sessionId", sessionId);
        wsRegistry.sendToUser(userId, toUser);

        if (agentId != null) {
            Map<String, Object> toAgent = WsFrames.of(WsFrameType.AGENT_LEFT);
            toAgent.put("sessionId", sessionId);
            toAgent.put("by", "user");
            wsRegistry.sendToUser(agentId, toAgent);
        }
        notifyQueueChanged();
        log.info("user {} ended agent service on session {}", userId, sessionId);
    }

    @Transactional
    public void close(Long agentId, Long sessionId) {
        ChatSession s = sessionMapper.selectById(sessionId);
        if (s == null) throw BizException.notFound("会话不存在");
        if ("CLOSED".equals(s.getStatus())) {
            return;
        }

        sessionMapper.update(null, Wrappers.<ChatSession>lambdaUpdate()
                .set(ChatSession::getStatus, "CLOSED")
                .set(ChatSession::getTransferReason, null)
                .set(ChatSession::getPriority, 0)
                .eq(ChatSession::getId, sessionId));
        Map<String, Object> frame = WsFrames.of(WsFrameType.SESSION_CLOSED);
        frame.put("sessionId", sessionId);
        frame.put("by", "agent");
        wsRegistry.sendToUser(s.getUserId(), frame);
        notifyQueueChanged();
        log.info("agent {} closed session {}", agentId, sessionId);
    }

    private void notifyQueueChanged() {
        Map<String, Object> f = WsFrames.of(WsFrameType.QUEUE_CHANGED);
        f.put("at", java.time.LocalDateTime.now().toString());
        wsRegistry.broadcast(f);
    }

    private String writeJson(Object o) {
        try { return objectMapper.writeValueAsString(o); } catch (Exception e) { return "{}"; }
    }
}