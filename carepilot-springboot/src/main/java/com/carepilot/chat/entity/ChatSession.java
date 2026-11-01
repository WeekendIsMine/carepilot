package com.carepilot.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_session")
public class ChatSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionNo;
    private Long userId;
    private Long agentId;

    private String status;
    private String transferReason;
    private Integer priority;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private Integer unreadForAgent;

    private String aiSummary;
    private Long ticketId;
    private Integer version;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
