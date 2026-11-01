package com.carepilot.agent.approval;

import com.carepilot.chat.service.ChatMessageService;
import com.carepilot.chat.service.ChatSessionService;
import com.carepilot.chat.ws.WsFrameType;
import com.carepilot.chat.ws.WsFrames;
import com.carepilot.chat.ws.WsSessionRegistry;
import com.carepilot.common.api.Result;
import com.carepilot.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批接口（HITL）：AI 想执行退款/转人工这类写操作时，先落一条审批单等用户确认。
 */
@Tag(name = "人工审批")
@RestController
@RequestMapping("/api/chat/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;
    private final ChatMessageService messageService;
    private final ChatSessionService sessionService;
    private final WsSessionRegistry wsRegistry;

    @Operation(summary = "确认执行（Human-in-the-loop）")
    @PostMapping("/{approvalNo}/approve")
    public Result<Map<String, Object>> approve(@PathVariable String approvalNo) {
        Long userId = SecurityUtils.userId();
        ChatApproval pending = approvalService.findPending(userId, approvalNo);
        if (pending == null) {
            return Result.fail(com.carepilot.common.api.ResultCode.NOT_FOUND, "审批单不存在");
        }
        Map<String, Object> result = approvalService.approve(userId, approvalNo);

        String notice = "已创建售后工单 " + result.get("ticketNo") + "，我们会尽快处理。";
        messageService.append(pending.getSessionId(), "SYSTEM", notice, null, null, null);
        sessionService.touch(pending.getSessionId(), notice);

        Map<String, Object> frame = WsFrames.of(WsFrameType.APPROVAL_RESULT);
        frame.put("approvalNo", approvalNo);
        frame.put("status", "APPROVED");
        frame.put("notice", notice);
        frame.put("result", result);
        wsRegistry.sendToUser(userId, frame);
        return Result.ok(result);
    }

    @Operation(summary = "取消申请")
    @PostMapping("/{approvalNo}/reject")
    public Result<Void> reject(@PathVariable String approvalNo) {
        Long userId = SecurityUtils.userId();
        ChatApproval pending = approvalService.findPending(userId, approvalNo);
        approvalService.reject(userId, approvalNo);

        Map<String, Object> frame = WsFrames.of(WsFrameType.APPROVAL_RESULT);
        frame.put("approvalNo", approvalNo);
        frame.put("status", "REJECTED");
        frame.put("notice", "已取消本次售后申请");
        wsRegistry.sendToUser(userId, frame);
        if (pending != null) {
            messageService.append(pending.getSessionId(), "SYSTEM", "用户取消了本次售后申请", null, null, null);
        }
        return Result.ok();
    }

    @Operation(summary = "会话内待确认的审批单（刷新页面后用于恢复审批卡）")
    @GetMapping("/sessions/{sessionId}/pending")
    public Result<List<Map<String, Object>>> pendingBySession(@PathVariable Long sessionId) {
        return Result.ok(approvalService.pendingBySession(SecurityUtils.userId(), sessionId));
    }

    @Operation(summary = "查询审批单")
    @GetMapping("/{approvalNo}")
    public Result<Map<String, Object>> get(@PathVariable String approvalNo) {
        ChatApproval a = approvalService.findPending(SecurityUtils.userId(), approvalNo);
        if (a == null) return Result.fail(com.carepilot.common.api.ResultCode.NOT_FOUND, "审批单不存在");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("approvalNo", a.getApprovalNo());
        m.put("status", a.getStatus());
        m.put("toolName", a.getToolName());
        m.put("toolArgs", a.getToolArgs());
        m.put("expireAt", a.getExpireAt());
        return Result.ok(m);
    }
}