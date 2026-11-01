package com.carepilot.agent.approval;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carepilot.aftersale.entity.AfterSaleTicket;
import com.carepilot.aftersale.service.TicketService;
import com.carepilot.agent.approval.mapper.ChatApprovalMapper;
import com.carepilot.common.api.ResultCode;
import com.carepilot.common.exception.BizException;
import com.carepilot.common.util.Ids;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审批单管理：创建、查询、确认/拒绝、过期清理。
 * 审批通过才真正执行工具，避免模型擅自退款。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalService {

    public static final String TOOL_SUBMIT_AFTER_SALE = "submitAfterSaleRequest";
    private static final int EXPIRE_MINUTES = 15;

    private final ChatApprovalMapper approvalMapper;
    private final TicketService ticketService;
    private final ObjectMapper objectMapper;

    @Transactional
    public ChatApproval request(Long userId, Long sessionId, String toolName, Map<String, Object> args) {
        ChatApproval a = new ChatApproval();
        a.setApprovalNo(Ids.approval());
        a.setSessionId(sessionId);
        a.setUserId(userId);
        a.setToolName(toolName);
        a.setToolArgs(writeJson(args));
        a.setStatus("PENDING");
        a.setExpireAt(LocalDateTime.now().plusMinutes(EXPIRE_MINUTES));
        approvalMapper.insert(a);
        log.info("approval requested no={} tool={} args={}", a.getApprovalNo(), toolName, args);
        return a;
    }

    @Transactional
    public Map<String, Object> approve(Long userId, String approvalNo) {
        ChatApproval a = requirePending(userId, approvalNo);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("approvalNo", a.getApprovalNo());

        if (TOOL_SUBMIT_AFTER_SALE.equals(a.getToolName())) {
            Map<String, Object> args = readJson(a.getToolArgs());
            String orderNo = (String) args.get("orderNo");
            String type = (String) args.get("type");
            String reason = (String) args.get("reason");
            Object amount = args.get("amount");

            AfterSaleTicket t = ticketService.create(
                    a.getUserId(), a.getSessionId(), orderNo, type.isEmpty() ? "RETURN" : type,
                    reason == null ? "" : reason,
                    amount instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : BigDecimal.ZERO,
                    "AI");
            result.put("ticketNo", t.getTicketNo());
            result.put("ticketId", t.getId());
            result.put("status", t.getStatus());
        } else {
            throw new BizException(ResultCode.BAD_REQUEST, "未知的审批动作：" + a.getToolName());
        }

        approveRow(a, "APPROVED", result);
        return result;
    }

    @Transactional
    public void reject(Long userId, String approvalNo) {
        ChatApproval a = requirePending(userId, approvalNo);
        approveRow(a, "REJECTED", Map.of("reason", "用户取消"));
    }

    public java.util.List<java.util.Map<String, Object>> pendingBySession(Long userId, Long sessionId) {
        java.util.List<ChatApproval> rows = approvalMapper.selectList(Wrappers.<ChatApproval>lambdaQuery()
                .eq(ChatApproval::getUserId, userId)
                .eq(ChatApproval::getSessionId, sessionId)
                .eq(ChatApproval::getStatus, "PENDING")
                .orderByAsc(ChatApproval::getId));
        java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        for (ChatApproval a : rows) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("approvalNo", a.getApprovalNo());
            m.put("tool", a.getToolName());
            m.put("args", readJson(a.getToolArgs()));
            m.put("expireAt", a.getExpireAt() == null ? null : a.getExpireAt().toString());
            out.add(m);
        }
        return out;
    }

    public ChatApproval findPending(Long userId, String approvalNo) {
        return approvalMapper.selectOne(Wrappers.<ChatApproval>lambdaQuery()
                .eq(ChatApproval::getApprovalNo, approvalNo)
                .eq(ChatApproval::getUserId, userId));
    }

    private ChatApproval requirePending(Long userId, String approvalNo) {
        ChatApproval a = findPending(userId, approvalNo);
        if (a == null) throw BizException.notFound("审批单不存在");
        if (!"PENDING".equals(a.getStatus())) throw BizException.conflict("审批单已处理");
        if (a.getExpireAt().isBefore(LocalDateTime.now())) {
            approveRow(a, "EXPIRED", Map.of("reason", "已过期"));
            throw BizException.conflict("审批单已过期，请重新发起");
        }
        return a;
    }

    private void approveRow(ChatApproval a, String status, Map<String, Object> result) {
        ChatApproval patch = new ChatApproval();
        patch.setId(a.getId());
        patch.setStatus(status);
        patch.setDecidedAt(LocalDateTime.now());
        patch.setResult(writeJson(result));
        approvalMapper.updateById(patch);
    }

    private String writeJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJson(String raw) {
        try {
            return raw == null ? Map.of() : objectMapper.readValue(raw, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}