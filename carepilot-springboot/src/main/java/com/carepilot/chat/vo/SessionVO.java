package com.carepilot.chat.vo;

import com.carepilot.chat.entity.ChatSession;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SessionVO {
    private Long id;
    private String sessionNo;
    private String status;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private Integer unreadForAgent;
    private String transferReason;
    private Integer priority;

    public static SessionVO of(ChatSession s) {
        return SessionVO.builder()
                .id(s.getId())
                .sessionNo(s.getSessionNo())
                .status(s.getStatus())
                .lastMessage(s.getLastMessage())
                .lastMessageAt(s.getLastMessageAt())
                .unreadForAgent(s.getUnreadForAgent())
                .transferReason(s.getTransferReason())
                .priority(s.getPriority())
                .build();
    }
}