package com.carepilot.chat.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carepilot.chat.entity.ChatSession;
import com.carepilot.chat.mapper.ChatSessionMapper;
import com.carepilot.common.exception.BizException;
import com.carepilot.common.util.Ids;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话的生命周期：创建、查询、改状态（AI / 排队 / 人工 / 已结束）、重开、关闭。
 * 所有对外方法都带 userId 判断，避免越权读别人的会话。
 */
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionMapper sessionMapper;
    private final com.carepilot.chat.ws.WsSessionRegistry wsRegistry;

    /** 新建会话，初始状态 AI */
    public ChatSession create(Long userId) {
        ChatSession s = new ChatSession();
        s.setSessionNo(Ids.session());
        s.setUserId(userId);
        s.setStatus("AI");
        s.setPriority(0);
        s.setUnreadForAgent(0);
        s.setVersion(0);
        s.setLastMessageAt(LocalDateTime.now());
        sessionMapper.insert(s);
        return s;
    }

    /** 按 id 取，不校验归属，内部流程用 */
    public ChatSession getById(Long sessionId) {
        ChatSession s = sessionMapper.selectById(sessionId);
        if (s == null) throw BizException.notFound("会话不存在");
        return s;
    }

    /** 取会话并校验归属，对不上按不存在处理 */
    public ChatSession getOwned(Long userId, Long sessionId) {
        ChatSession s = sessionMapper.selectById(sessionId);
        if (s == null) {
            throw BizException.notFound("会话不存在");
        }
        if (!s.getUserId().equals(userId)) {
            throw BizException.forbidden("无权访问该会话");
        }
        return s;
    }

    /** 我的会话列表 */
    public List<ChatSession> listMine(Long userId) {
        return sessionMapper.selectList(Wrappers.<ChatSession>lambdaQuery()
                .eq(ChatSession::getUserId, userId)
                .orderByDesc(ChatSession::getLastMessageAt)
                .last("limit 50"));
    }

    /** 更新最后一条消息与活跃时间，会话列表排序用 */
    public void touch(Long sessionId, String lastMessage) {
        ChatSession patch = new ChatSession();
        patch.setId(sessionId);
        patch.setLastMessage(truncate(lastMessage, 200));
        patch.setLastMessageAt(LocalDateTime.now());
        sessionMapper.updateById(patch);
    }

    /** 删除会话；人工服务中不允许删 */
    public void delete(Long userId, Long sessionId) {
        ChatSession s = getOwned(userId, sessionId);
        if ("AGENT".equals(s.getStatus())) {
            throw BizException.conflict("会话正在人工服务中，暂不能删除");
        }
        sessionMapper.deleteById(s.getId());
    }

    /** 已结束的会话重新开启：状态回 AI，历史保留 */
    @Transactional
    public ChatSession reopen(Long userId, Long sessionId) {
        ChatSession s = getOwned(userId, sessionId);
        if (!"CLOSED".equals(s.getStatus())) {
            return s;
        }
        sessionMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<ChatSession>lambdaUpdate()
                .set(ChatSession::getStatus, "AI")
                .set(ChatSession::getTransferReason, null)
                .set(ChatSession::getPriority, 0)
                .eq(ChatSession::getId, sessionId));
        return sessionMapper.selectById(sessionId);
    }

    /** 关闭会话：置为已结束并通知正在服务的坐席 */
    public void close(Long userId, Long sessionId) {
        ChatSession s = getOwned(userId, sessionId);
        if ("CLOSED".equals(s.getStatus())) return;
        ChatSession patch = new ChatSession();
        patch.setId(s.getId());
        patch.setStatus("CLOSED");
        patch.setTransferReason(null);
        patch.setPriority(0);
        sessionMapper.updateById(patch);

        // 用户关闭会话时，正在服务的坐席要立刻收到通知，否则他还在对着空会话打字
        if (s.getAgentId() != null) {
            java.util.Map<String, Object> f = com.carepilot.chat.ws.WsFrames.of(com.carepilot.chat.ws.WsFrameType.SESSION_CLOSED);
            f.put("sessionId", sessionId);
            f.put("by", "user");
            wsRegistry.sendToUser(s.getAgentId(), f);
        }
        java.util.Map<String, Object> qc = com.carepilot.chat.ws.WsFrames.of(com.carepilot.chat.ws.WsFrameType.QUEUE_CHANGED);
        qc.put("at", java.time.LocalDateTime.now().toString());
        wsRegistry.broadcast(qc);
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}