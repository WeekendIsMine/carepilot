package com.carepilot.chat.controller;

import com.carepilot.aftersale.entity.AfterSaleTicket;
import com.carepilot.aftersale.entity.AfterSaleTicketLog;
import com.carepilot.aftersale.mapper.AfterSaleTicketLogMapper;
import com.carepilot.aftersale.service.TicketService;
import com.carepilot.chat.entity.ChatMessage;
import com.carepilot.chat.entity.ChatSession;
import com.carepilot.chat.service.ChatMessageService;
import com.carepilot.chat.service.ChatSessionService;
import com.carepilot.chat.transfer.TransferService;
import com.carepilot.chat.vo.MessageVO;
import com.carepilot.chat.vo.SessionVO;
import com.carepilot.chat.ws.WsFrameType;
import com.carepilot.chat.ws.WsFrames;
import com.carepilot.chat.ws.WsSessionRegistry;
import com.carepilot.common.api.Result;
import com.carepilot.security.SecurityUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 坐席工作台：排队列表、接管、会话摘要、回复、工单流程。
 * 只允许 AGENT / ADMIN 角色访问。
 */
@Tag(name = "客服工作台")
@RestController
@RequestMapping("/api/workbench")
@RequiredArgsConstructor
public class WorkbenchController {

    private final TransferService transferService;
    private final TicketService ticketService;
    private final ChatMessageService messageService;
    private final ChatSessionService sessionService;
    private final AfterSaleTicketLogMapper ticketLogMapper;
    private final WsSessionRegistry wsRegistry;
    private final ObjectMapper objectMapper;

    /** 坐席心跳：刷新在线状态，工作台每几秒调一次 */
    @Operation(summary = "坐席心跳")
    @PostMapping("/heartbeat")
    public Result<Void> heartbeat() {
        transferService.heartbeat(SecurityUtils.userId());
        return Result.ok();
    }

    /** 排队中的会话，按优先级和时间排序 */
    @Operation(summary = "排队中的会话")
    @GetMapping("/sessions/waiting")
    public Result<List<SessionVO>> waiting() {
        return Result.ok(transferService.waiting().stream().map(SessionVO::of).toList());
    }

    /** 我正在接待的会话 */
    @Operation(summary = "我正在服务的会话")
    @GetMapping("/sessions/mine")
    public Result<List<SessionVO>> mine() {
        return Result.ok(transferService.mySessions(SecurityUtils.userId()).stream().map(SessionVO::of).toList());
    }

    /** 接管会话：状态置为人工服务，并异步生成交接摘要 */
    @Operation(summary = "接管会话")
    @PostMapping("/sessions/{id}/accept")
    public Result<Map<String, Object>> accept(@PathVariable Long id) {
        ChatSession s = transferService.accept(SecurityUtils.userId(), id);
        return Result.ok(Map.of("sessionId", s.getId(), "status", s.getStatus(),
                "summary", s.getAiSummary() == null ? "" : s.getAiSummary()));
    }

    /** 拉取已生成的交接摘要，刷新页面时用 */
    @Operation(summary = "交接摘要（接管后异步生成，刷新页面可重新拉取）")
    @GetMapping("/sessions/{id}/summary")
    public Result<Map<String, Object>> summary(@PathVariable Long id) {
        ChatSession s = transferService.summaryOf(id);
        return Result.ok(s == null || s.getAiSummary() == null ? Map.of() : Map.of("raw", s.getAiSummary()));
    }

    /** 结束会话，并通知用户 */
    @Operation(summary = "结束会话")
    @PostMapping("/sessions/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        transferService.close(SecurityUtils.userId(), id);
        return Result.ok();
    }

    /** 会话消息；只允许当事人或管理员查看 */
    @Operation(summary = "会话消息")
    @GetMapping("/sessions/{id}/messages")
    public Result<List<MessageVO>> messages(@PathVariable Long id) {
        ChatSession s = sessionService.getById(id);
        if (s == null) throw com.carepilot.common.exception.BizException.notFound("会话不存在");
        String role = com.carepilot.security.SecurityUtils.current().getRole();
        Long me = com.carepilot.security.SecurityUtils.userId();

        if (!"ADMIN".equals(role) && s.getAgentId() != null && !s.getAgentId().equals(me)) {
            throw com.carepilot.common.exception.BizException.conflict("该会话由其他坐席接待");
        }
        List<ChatMessage> list = messageService.history(id, 200);
        return Result.ok(list.stream().map(m -> MessageVO.of(m, objectMapper)).toList());
    }

    /** 坐席回复：校验会话状态和归属后落库并推给用户 */
    @Operation(summary = "坐席回复")
    @PostMapping("/sessions/{id}/messages")
    public Result<MessageVO> reply(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            throw new com.carepilot.common.exception.BizException(com.carepilot.common.api.ResultCode.BAD_REQUEST, "内容不能为空");
        }
        ChatSession s = sessionService.getById(id);
        if (s == null) throw com.carepilot.common.exception.BizException.notFound("会话不存在");
        if ("CLOSED".equals(s.getStatus())) {
            throw com.carepilot.common.exception.BizException.conflict("会话已结束，不能再回复");
        }
        if (!"AGENT".equals(s.getStatus())) {
            throw com.carepilot.common.exception.BizException.conflict("会话不在人工服务中");
        }
        Long me = com.carepilot.security.SecurityUtils.userId();
        String role = com.carepilot.security.SecurityUtils.current().getRole();
        if (s.getAgentId() != null && !s.getAgentId().equals(me) && !"ADMIN".equals(role)) {
            throw com.carepilot.common.exception.BizException.conflict("该会话由其他坐席接待");
        }
        ChatMessage m = messageService.append(id, "AGENT", content, null, null, null);
        sessionService.touch(id, content);
        wsRegistry.sendToUser(s.getUserId(), WsFrames.of(WsFrameType.AGENT_MESSAGE, "content", content));
        return Result.ok(MessageVO.of(m, objectMapper));
    }

    /** 工单分页列表，带退款进度 */
    @Operation(summary = "工单列表")
    @GetMapping("/tickets")
    public Result<com.carepilot.common.api.PageResult<com.carepilot.aftersale.vo.TicketVO>> tickets(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return Result.ok(com.carepilot.common.api.PageResult.of(ticketService.listWithRefund(status, page, size)));
    }

    /** 工单流转：APPROVE / REJECT / REFUND / FINISH / CLOSE */
    @Operation(summary = "工单流转：APPROVE/REJECT/REFUND/FINISH/CLOSE")
    @PostMapping("/tickets/{id}/{action}")
    public Result<AfterSaleTicket> transit(@PathVariable Long id, @PathVariable String action,
                                           @RequestParam(required = false) String remark) {
        return Result.ok(ticketService.transit(id, action.toUpperCase(),
                SecurityUtils.userId(), SecurityUtils.current().getRole(), remark));
    }

    /** 工单流转日志 */
    @Operation(summary = "工单流转日志")
    @GetMapping("/tickets/{id}/logs")
    public Result<List<AfterSaleTicketLog>> logs(@PathVariable Long id) {
        return Result.ok(ticketLogMapper.selectList(Wrappers.<AfterSaleTicketLog>lambdaQuery()
                .eq(AfterSaleTicketLog::getTicketId, id)
                .orderByAsc(AfterSaleTicketLog::getId)));
    }
}