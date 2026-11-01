package com.carepilot.aftersale.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carepilot.aftersale.entity.AfterSaleTicket;
import com.carepilot.aftersale.entity.AfterSaleTicketLog;
import com.carepilot.aftersale.mapper.AfterSaleTicketLogMapper;
import com.carepilot.aftersale.mapper.AfterSaleTicketMapper;
import com.carepilot.aftersale.TicketStatus;
import com.carepilot.common.api.ResultCode;
import com.carepilot.common.exception.BizException;
import com.carepilot.common.util.Ids;
import com.carepilot.mall.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 售后工单：创建、查询、状态机流转、超时关闭。
 * 状态流转规则收敛在 TicketStatus 里，非法跳转直接报错。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    private final AfterSaleTicketMapper ticketMapper;
    private final RefundService refundService;
    private final AfterSaleTicketLogMapper logMapper;
    private final com.carepilot.mall.service.OrderCommandService orderCommandService;

    /** 我的工单，按创建时间倒序 */
    public List<AfterSaleTicket> listMine(Long userId, int limit) {
        return ticketMapper.selectList(Wrappers.<AfterSaleTicket>lambdaQuery()
                .eq(AfterSaleTicket::getUserId, userId)
                .orderByDesc(AfterSaleTicket::getId)
                .last("limit " + Math.min(limit, 20)));
    }

    /** 按订单号找最近一张工单 */
    public AfterSaleTicket findByOrderNo(Long userId, String orderNo) {
        return ticketMapper.selectOne(Wrappers.<AfterSaleTicket>lambdaQuery()
                .eq(AfterSaleTicket::getUserId, userId)
                .eq(AfterSaleTicket::getOrderNo, orderNo)
                .orderByDesc(AfterSaleTicket::getId)
                .last("limit 1"));
    }

    /** 是否已有处理中的工单（完成/关闭/拒绝都不算） */
    public boolean hasActive(Long userId, String orderNo) {
        AfterSaleTicket t = findByOrderNo(userId, orderNo);
        return t != null && !"DONE".equals(t.getStatus()) && !"CLOSED".equals(t.getStatus())
                && !"REJECTED".equals(t.getStatus());
    }

    /** 按主键取 */
    public AfterSaleTicket findById(Long id) {
        return ticketMapper.selectById(id);
    }

    /** 建单：落库 + 写创建日志，并把订单置为「售后中」 */
    @Transactional
    public AfterSaleTicket create(Long userId, Long sessionId, String orderNo, String type,
                                  String reason, BigDecimal amount, String source) {
        AfterSaleTicket t = new AfterSaleTicket();
        t.setTicketNo(Ids.ticket());
        t.setOrderNo(orderNo);
        t.setUserId(userId);
        t.setSessionId(sessionId);
        t.setType(type);
        // 模型给的原因可能是一整句，按列宽截断，避免 Data too long
        t.setReason(truncate(reason, 60));
        t.setDescription(truncate(reason, 480));
        t.setStatus("PENDING");
        t.setSource(source);
        t.setAmount(amount);
        t.setVersion(0);
        ticketMapper.insert(t);
        appendLog(t.getId(), null, "PENDING", "CREATE", userId, "USER", "用户确认后提交");

        orderCommandService.markAfterSale(orderNo);
        log.info("ticket created no={} order={} type={}", t.getTicketNo(), orderNo, type);
        return t;
    }

    /** 工单分页列表，拼上退款单展示退款进度 */
    public com.baomidou.mybatisplus.core.metadata.IPage<com.carepilot.aftersale.vo.TicketVO> listWithRefund(
            String status, Integer page, Integer size) {
        com.baomidou.mybatisplus.core.metadata.IPage<AfterSaleTicket> p = listByStatus(status, page, size);
        java.util.List<String> nos = p.getRecords().stream().map(AfterSaleTicket::getTicketNo).toList();
        java.util.Map<String, com.carepilot.mall.entity.RefundRecord> refunds = refundService.byTicketNos(nos);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.carepilot.aftersale.vo.TicketVO> out =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        out.setRecords(p.getRecords().stream()
                .map(t -> com.carepilot.aftersale.vo.TicketVO.of(t, refunds.get(t.getTicketNo())))
                .toList());
        return out;
    }

    /** 按状态分页查询 */
    public com.baomidou.mybatisplus.core.metadata.IPage<AfterSaleTicket> listByStatus(
            String status, Integer page, Integer size) {
        return ticketMapper.selectPage(com.carepilot.common.api.Pages.of(page, size),
                Wrappers.<AfterSaleTicket>lambdaQuery()
                        .eq(status != null && !status.isBlank(), AfterSaleTicket::getStatus, status)
                        .orderByDesc(AfterSaleTicket::getId));
    }

    /** 状态流转：校验合法性、写日志，退款动作会真正执行退款 */
    @Transactional
    public AfterSaleTicket transit(Long ticketId, String action, Long operatorId, String role, String remark) {
        AfterSaleTicket t = ticketMapper.selectById(ticketId);
        if (t == null) throw BizException.notFound("工单不存在");
        String to;
        try {
            // 状态机在 TicketStatus 里统一定义，非法流转（比如从 DONE 再退款）直接报错
            to = TicketStatus.target(action, t.getStatus());
        } catch (IllegalStateException e) {
            throw new BizException(ResultCode.CONFLICT, e.getMessage());
        }
        AfterSaleTicket patch = new AfterSaleTicket();
        patch.setId(t.getId());
        patch.setStatus(to);
        ticketMapper.updateById(patch);
        appendLog(t.getId(), t.getStatus(), to, action, operatorId, role, remark);

        // 审批通过后执行退款：退款成功会联动订单状态和库存
        if ("REFUND".equals(action)) {
            AfterSaleTicket current = ticketMapper.selectById(ticketId);
            refundService.refund(current);
        }

        // 工单被拒或关闭要还原订单状态，否则订单永远停在「售后中」
        if ("REJECTED".equals(to) || "CLOSED".equals(to)) {
            orderCommandService.restoreAfterSale(t.getOrderNo());
        }
        return ticketMapper.selectById(ticketId);
    }

    /** 超时未处理的待审工单自动关闭，并还原订单状态 */
    @Transactional
    public int expirePending(int timeoutMinutes) {
        java.time.LocalDateTime deadline = java.time.LocalDateTime.now().minusMinutes(Math.max(1, timeoutMinutes));
        java.util.List<AfterSaleTicket> stale = ticketMapper.selectList(
                Wrappers.<AfterSaleTicket>lambdaQuery()
                        .eq(AfterSaleTicket::getStatus, "PENDING")
                        .lt(AfterSaleTicket::getCreatedAt, deadline)
                        .orderByAsc(AfterSaleTicket::getId)
                        .last("limit 100"));
        int n = 0;
        for (AfterSaleTicket s : stale) {
            try {
                transit(s.getId(), "CLOSE", 0L, "SYSTEM",
                        "超过 " + timeoutMinutes + " 分钟未处理，系统自动关闭");
                n++;
            } catch (Exception e) {
                log.warn("expire ticket {} failed: {}", s.getTicketNo(), e.getMessage());
            }
        }
        if (n > 0) {
            log.info("expired {} pending tickets (timeout={}m)", n, timeoutMinutes);
        }
        return n;
    }

    private static String truncate(String text, int max) {
        if (text == null) return null;
        return text.length() <= max ? text : text.substring(0, max);
    }

    /** 写一条流转日志，审计和排障用 */
    public void appendLog(Long ticketId, String from, String to, String action,
                          Long operatorId, String role, String remark) {
        AfterSaleTicketLog l = new AfterSaleTicketLog();
        l.setTicketId(ticketId);
        l.setFromStatus(from);
        l.setToStatus(to);
        l.setAction(action);
        l.setOperatorId(operatorId);
        l.setOperatorRole(role);
        l.setRemark(remark);
        logMapper.insert(l);
    }
}