package com.carepilot.chat.vo;

import com.carepilot.chat.entity.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MessageVO {
    private Long id;
    private String role;
    private String content;
    private Object toolCalls;
    private Object citations;
    private String clientMsgId;
    private Integer latencyMs;
    private LocalDateTime createdAt;

    public static MessageVO of(ChatMessage m, ObjectMapper om) {
        return MessageVO.builder()
                .id(m.getId())
                .role(m.getRole())
                .content(m.getContent())
                .toolCalls(readJson(m.getToolCalls(), om))
                .citations(readJson(m.getCitations(), om))
                .clientMsgId(m.getClientMsgId())
                .latencyMs(m.getLatencyMs())
                .createdAt(m.getCreatedAt())
                .build();
    }

    private static Object readJson(String raw, ObjectMapper om) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return om.readValue(raw, Object.class);
        } catch (Exception e) {
            return raw;
        }
    }
}
