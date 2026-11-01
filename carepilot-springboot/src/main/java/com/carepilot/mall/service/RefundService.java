package com.carepilot.mall.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carepilot.aftersale.entity.AfterSaleTicket;
import com.carepilot.common.util.Ids;
import com.carepilot.mall.entity.PayRecord;
import com.carepilot.mall.entity.RefundRecord;
import com.carepilot.order.mapper.OrderMainMapper;
import com.carepilot.mall.mapper.RefundRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退款：按原支付渠道退回，成功后写退款单、改订单状态、回补库存。
 * 同一个工单只会成功退一次，靠退款单状态保证幂等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundRecordMapper refundMapper;
    private final PaymentService paymentService;
    private final WalletService walletService;
    private final com.carepilot.mall.service.OrderCommandService orderCommandService;
    private final OrderMainMapper orderMapper;

    /** 按工单号查退款单 */
    public RefundRecord byTicket(String ticketNo) {
        return refundMapper.selectOne(Wrappers.<RefundRecord>lambdaQuery()
                .eq(RefundRecord::getTicketNo, ticketNo)
                .orderByDesc(RefundRecord::getId)
                .last("limit 1"));
    }

    /** 批量查退款单，工单列表展示退款进度用 */
    public java.util.Map<String, RefundRecord> byTicketNos(java.util.List<String> ticketNos) {
        if (ticketNos == null || ticketNos.isEmpty()) return java.util.Map.of();
        return refundMapper.selectList(Wrappers.<RefundRecord>lambdaQuery()
                        .in(RefundRecord::getTicketNo, ticketNos)
                        .orderByDesc(RefundRecord::getId))
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        RefundRecord::getTicketNo, r -> r, (a, b) -> a));
    }

    /** 执行退款：算金额、走渠道、落单、改订单、回补库存 */
    @Transactional
    public RefundRecord refund(AfterSaleTicket ticket) {
        // 幂等入口：这张工单已经退成功过就直接返回，避免重复打款
        RefundRecord exist = byTicket(ticket.getTicketNo());
        if (exist != null && "SUCCESS".equals(exist.getStatus())) return exist;

        PayRecord pay = paymentService.findSuccess(ticket.getOrderNo());
        BigDecimal amount = ticket.getAmount() != null && ticket.getAmount().compareTo(BigDecimal.ZERO) > 0
                ? ticket.getAmount()
                : (pay != null ? pay.getAmount() : BigDecimal.ZERO);
        // 渠道只做记录：目前支付只有余额一种，退款一律加回钱包
        String channel = pay != null ? pay.getChannel() : PaymentService.CHANNEL_BALANCE;

        RefundRecord refund = new RefundRecord();
        refund.setRefundNo(Ids.refund());
        refund.setTicketNo(ticket.getTicketNo());
        refund.setOrderNo(ticket.getOrderNo());
        refund.setPayNo(pay == null ? null : pay.getPayNo());
        refund.setUserId(ticket.getUserId());
        refund.setChannel(channel);
        refund.setAmount(amount);
        refund.setStatus("PENDING");
        refundMapper.insert(refund);

        walletService.refund(ticket.getUserId(), amount, refund.getRefundNo(), "售后退款 " + ticket.getTicketNo());

        RefundRecord patch = new RefundRecord();
        patch.setId(refund.getId());
        patch.setStatus("SUCCESS");
        patch.setRefundedAt(LocalDateTime.now());
        refundMapper.updateById(patch);

        // 退款成功要联动两件事：订单置「已退款」、库存回补
        orderCommandService.markRefunded(ticket.getOrderNo());
        orderCommandService.restoreStockForRefund(ticket.getOrderNo());

        log.info("refund done no={} ticket={} order={} channel={} amount={}",
                refund.getRefundNo(), ticket.getTicketNo(), ticket.getOrderNo(), channel, amount);
        return refundMapper.selectById(refund.getId());
    }
}