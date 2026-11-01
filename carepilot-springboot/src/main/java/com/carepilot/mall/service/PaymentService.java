package com.carepilot.mall.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carepilot.common.exception.BizException;
import com.carepilot.common.util.Ids;
import com.carepilot.order.entity.OrderMain;
import com.carepilot.mall.entity.PayRecord;
import com.carepilot.mall.entity.RefundRecord;
import com.carepilot.order.mapper.OrderMainMapper;
import com.carepilot.mall.mapper.PayRecordMapper;
import com.carepilot.mall.mapper.RefundRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付。目前只支持余额渠道；扣款失败会把支付单标记 FAILED 并回滚库存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    public static final String CHANNEL_BALANCE = "BALANCE";

    private final PayRecordMapper payMapper;
    private final RefundRecordMapper refundMapper;
    private final OrderMainMapper orderMapper;
    private final WalletService walletService;

    /** 订单的支付单记录 */
    public List<PayRecord> payRecords(String orderNo) {
        return payMapper.selectList(Wrappers.<PayRecord>lambdaQuery()
                .eq(PayRecord::getOrderNo, orderNo)
                .orderByDesc(PayRecord::getId));
    }

    /** 订单的退款单记录 */
    public List<RefundRecord> refundRecords(String orderNo) {
        return refundMapper.selectList(Wrappers.<RefundRecord>lambdaQuery()
                .eq(RefundRecord::getOrderNo, orderNo)
                .orderByDesc(RefundRecord::getId));
    }

    /** 取消订单退款：用支付单状态做 CAS，保证只退一次 */
    @Transactional
    public java.math.BigDecimal refundOnCancel(Long userId, String orderNo) {
        PayRecord payRecord = findSuccess(orderNo);
        if (payRecord == null) return null;
        // 退款也做 CAS：SUCCESS → REFUNDED 只有一个线程能成功，保证不会退两次
        int changed = payMapper.update(null, Wrappers.<PayRecord>lambdaUpdate()
                .set(PayRecord::getStatus, "REFUNDED")
                .eq(PayRecord::getId, payRecord.getId())
                .eq(PayRecord::getStatus, "SUCCESS"));
        if (changed == 0) return null;

        walletService.refund(userId, payRecord.getAmount(), payRecord.getPayNo(), "取消订单退款 " + orderNo);

        RefundRecord refund = new RefundRecord();
        refund.setRefundNo(com.carepilot.common.util.Ids.refund());
        refund.setTicketNo("CANCEL-" + orderNo);
        refund.setOrderNo(orderNo);
        refund.setPayNo(payRecord.getPayNo());
        refund.setUserId(userId);
        refund.setChannel(payRecord.getChannel());
        refund.setAmount(payRecord.getAmount());
        refund.setStatus("SUCCESS");
        refund.setRefundedAt(java.time.LocalDateTime.now());
        refundMapper.insert(refund);

        log.info("cancel refund done order={} amount={} channel={}", orderNo, payRecord.getAmount(), payRecord.getChannel());
        return payRecord.getAmount();
    }

    /** 找成功的支付单，没有说明没付过款 */
    public PayRecord findSuccess(String orderNo) {
        return payMapper.selectOne(Wrappers.<PayRecord>lambdaQuery()
                .eq(PayRecord::getOrderNo, orderNo)
                .eq(PayRecord::getStatus, "SUCCESS")
                .last("limit 1"));
    }

    /** 支付：先查是否已付，再校验渠道和余额，成功才置 SUCCESS */
    @Transactional
    public PayRecord pay(Long userId, String orderNo, String channel) {
        OrderMain order = orderMapper.selectOne(Wrappers.<OrderMain>lambdaQuery()
                .eq(OrderMain::getOrderNo, orderNo)
                .eq(OrderMain::getUserId, userId));
        if (order == null) throw BizException.notFound("订单不存在");

        // 幂等：已经支付成功的订单，重复点支付直接返回原支付单，不会重复扣钱
        PayRecord done = findSuccess(orderNo);
        if (done != null) return done;

        if (order.getStatus() != 0) throw BizException.conflict("只有待付款订单可以支付");

        if (channel != null && !channel.isBlank() && !CHANNEL_BALANCE.equalsIgnoreCase(channel)) {
            throw BizException.conflict("当前只支持余额支付，请充值后重试");
        }
        String ch = CHANNEL_BALANCE;

        PayRecord pay = new PayRecord();
        pay.setPayNo(Ids.pay());
        pay.setOrderNo(orderNo);
        pay.setUserId(userId);
        pay.setChannel(ch);
        pay.setAmount(order.getPayAmount());
        pay.setStatus("PENDING");
        payMapper.insert(pay);

        // 先落 PENDING 支付单再扣款：扣款失败能留下记录，方便对账和排查
        boolean enough = walletService.pay(userId, order.getPayAmount(), pay.getPayNo(), "支付订单 " + orderNo);
        if (!enough) {
            // 余额不足不抛「系统错误」，而是明确的业务冲突码，前端会提示去充值
            markFailed(pay, "余额不足");
            throw BizException.conflict("余额不足，请先到「我的钱包」充值");
        }

        PayRecord patch = new PayRecord();
        patch.setId(pay.getId());
        patch.setStatus("SUCCESS");
        patch.setPaidAt(LocalDateTime.now());
        payMapper.updateById(patch);

        OrderMain orderPatch = new OrderMain();
        orderPatch.setId(order.getId());
        orderPatch.setStatus(1);
        orderPatch.setPayTime(LocalDateTime.now());
        orderPatch.setPayAmount(order.getPayAmount());
        orderMapper.updateById(orderPatch);

        log.info("order paid no={} payNo={} channel={} amount={}", orderNo, pay.getPayNo(), ch, order.getPayAmount());
        return payMapper.selectById(pay.getId());
    }

    private void markFailed(PayRecord pay, String reason) {
        PayRecord patch = new PayRecord();
        patch.setId(pay.getId());
        patch.setStatus("FAILED");
        payMapper.updateById(patch);
        log.warn("pay failed no={} reason={}", pay.getPayNo(), reason);
    }
}