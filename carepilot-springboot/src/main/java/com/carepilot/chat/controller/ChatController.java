package com.carepilot.chat.controller;

import com.carepilot.chat.entity.ChatMessage;
import com.carepilot.chat.entity.ChatSession;
import com.carepilot.chat.service.ChatMessageService;
import com.carepilot.chat.service.ChatSessionService;
import com.carepilot.chat.service.SessionContextService;
import com.carepilot.chat.vo.SessionContextVO;
import com.carepilot.chat.transfer.TransferService;
import com.carepilot.chat.vo.MessageVO;
import com.carepilot.chat.vo.SessionVO;
import com.carepilot.common.api.Result;
import com.carepilot.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 顾客侧会话接口：建会话、会话列表、历史消息、转人工、结束会话。
 * WebSocket 只负责实时推帧，历史的读写都走这里。
 */
@Tag(name = "会话")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatSessionService sessionService;
    private final ChatMessageService messageService;
    private final ObjectMapper objectMapper;
    private final TransferService transferService;
    private final SessionContextService sessionContextService;
    private final com.carepilot.chat.ws.WsSessionRegistry wsSessionRegistry;

    /** 新建会话，初始状态是 AI 接待 */
    @Operation(summary = "创建会话")
    @PostMapping("/sessions")
    public Result<SessionVO> create() {
        ChatSession s = sessionService.create(SecurityUtils.userId());
        return Result.ok(SessionVO.of(s));
    }

    /** 我的会话列表，按最近活跃排序 */
    @Operation(summary = "我的会话列表")
    @GetMapping("/sessions")
    public Result<List<SessionVO>> list() {
        return Result.ok(sessionService.listMine(SecurityUtils.userId())
                .stream().map(SessionVO::of).toList());
    }

    /** 会话历史消息，含角色、引用来源等 */
    @Operation(summary = "历史消息")
    @GetMapping("/sessions/{id}/messages")
    public Result<List<MessageVO>> messages(@PathVariable Long id,
                                            @RequestParam(defaultValue = "50") int limit) {
        sessionService.getOwned(SecurityUtils.userId(), id);
        List<ChatMessage> list = messageService.history(id, limit);
        return Result.ok(list.stream().map(m -> MessageVO.of(m, objectMapper)).toList());
    }

    /** 会话上下文：当前订单、工单、排队位置，右侧栏用 */
    @Operation(summary = "会话上下文（客户订单/工单）")
    @GetMapping("/sessions/{id}/context")
    public Result<SessionContextVO> context(@PathVariable Long id) {
        return Result.ok(sessionContextService.forSession(SecurityUtils.userId(), id));
    }

    /** 转人工：置为排队中并按原因算优先级，返回排队位置 */
    @Operation(summary = "转人工")
    @PostMapping("/sessions/{id}/transfer")
    public Result<Map<String, Object>> transfer(@PathVariable Long id,
                                                @RequestParam(defaultValue = "OTHER") String reasonCode,
                                                @RequestParam(required = false) String reason) {
        sessionService.getOwned(SecurityUtils.userId(), id);
        int position = transferService.requestHuman(SecurityUtils.userId(), id,
                reason == null ? "用户主动要求转人工" : reason, reasonCode);
        return Result.ok(Map.of("position", position));
    }

    /** 删除会话（人工服务中不允许删，避免把坐席打断） */
    @Operation(summary = "删除会话")
    @DeleteMapping("/sessions/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.userId();
        sessionService.delete(userId, id);
        messageService.deleteBySession(id);
        return Result.ok();
    }

    /** 用户取消排队，状态回到 AI */
    @Operation(summary = "取消转人工")
    @PostMapping("/sessions/{id}/transfer/cancel")
    public Result<Void> cancelTransfer(@PathVariable Long id) {
        transferService.cancelTransfer(SecurityUtils.userId(), id);
        return Result.ok();
    }

    /** 重新开启已结束的会话：状态回到 AI，历史保留 */
    @Operation(summary = "重新开启已结束的会话")
    @PostMapping("/sessions/{id}/reopen")
    public Result<com.carepilot.chat.entity.ChatSession> reopen(@PathVariable Long id) {
        com.carepilot.chat.entity.ChatSession s = sessionService.reopen(SecurityUtils.userId(), id);
        java.util.Map<String, Object> frame = com.carepilot.chat.ws.WsFrames.of(com.carepilot.chat.ws.WsFrameType.SESSION_REOPENED);
        frame.put("sessionId", id);
        wsSessionRegistry.sendToUser(SecurityUtils.userId(), frame);
        return Result.ok(s);
    }

    /** 用户主动结束人工服务：会话回到 AI，并通知坐席 */
    @Operation(summary = "用户结束人工服务（会话回到 AI）")
    @PostMapping("/sessions/{id}/end-agent")
    public Result<Void> endAgent(@PathVariable Long id) {
        transferService.endByUser(SecurityUtils.userId(), id);
        return Result.ok();
    }

    /** 关闭会话：状态置为已结束，坐席端同步释放 */
    @Operation(summary = "关闭会话")
    @PostMapping("/sessions/{id}/close")
    public Result<Void> close(@PathVariable Long id) {
        sessionService.close(SecurityUtils.userId(), id);
        return Result.ok();
    }
}