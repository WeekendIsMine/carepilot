package com.carepilot.agent.approval;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批单：一次工具调用的待确认记录（工具名、参数、状态、过期时间）。
 */
@Data
@TableName("chat_approval")
public class ChatApproval {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String approvalNo;
    private Long sessionId;
    private Long userId;
    private String toolName;

    private String toolArgs;

    private String status;
    private LocalDateTime expireAt;
    private LocalDateTime decidedAt;
    private String result;
    private LocalDateTime createdAt;
}
