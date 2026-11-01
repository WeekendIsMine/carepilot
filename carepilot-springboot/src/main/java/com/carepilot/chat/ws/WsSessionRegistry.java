package com.carepilot.chat.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 在线连接表：用户/坐席 id → WebSocket 会话。用于把消息推给指定的人（比如转人工通知坐席）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsSessionRegistry {

    private final ObjectMapper objectMapper;
    private final Map<Long, Set<WebSocketSession>> byUser = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        byUser.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
        log.debug("ws open user={} sid={} total={}", userId, session.getId(), count(userId));
    }

    public void unregister(Long userId, WebSocketSession session) {
        Set<WebSocketSession> set = byUser.get(userId);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) {
                byUser.remove(userId);
            }
        }
        log.debug("ws close user={} sid={}", userId, session.getId());
    }

    public int count(Long userId) {
        Set<WebSocketSession> set = byUser.get(userId);
        return set == null ? 0 : set.size();
    }

    public boolean isOnline(Long userId) {
        return count(userId) > 0;
    }

    public void broadcast(Map<String, Object> frame) {
        try {
            String json = objectMapper.writeValueAsString(frame);
            for (Map.Entry<Long, Set<WebSocketSession>> e : byUser.entrySet()) {
                for (WebSocketSession s : e.getValue()) {
                    if (!s.isOpen()) continue;
                    try {
                        synchronized (s) {
                            s.sendMessage(new TextMessage(json));
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            log.warn("broadcast failed: {}", e.getMessage());
        }
    }

    public void sendToUser(Long userId, Map<String, Object> frame) {
        Set<WebSocketSession> sessions = byUser.get(userId);
        if (sessions == null || sessions.isEmpty()) return;
        try {
            String json = objectMapper.writeValueAsString(frame);
            for (WebSocketSession s : sessions) {
                if (!s.isOpen()) continue;
                synchronized (s) {
                    s.sendMessage(new TextMessage(json));
                }
            }
        } catch (Exception e) {
            log.warn("push to user {} failed: {}", userId, e.getMessage());
        }
    }
}