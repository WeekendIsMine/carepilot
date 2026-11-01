package com.carepilot.mall.controller;

import com.carepilot.common.api.Result;
import com.carepilot.common.idempotency.IdempotencyService;
import com.carepilot.mall.entity.PayRecord;
import com.carepilot.mall.service.PaymentService;
import com.carepilot.mall.dto.OrderCreateRequest;
import com.carepilot.mall.service.OrderCommandService;
import com.carepilot.order.entity.OrderMain;
import com.carepilot.order.service.OrderQueryService;
import com.carepilot.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 顾客订单接口：下单、列表、详情、支付、取消、确认收货。
 * 所有方法都按 userId 校验归属，越权访问一律按「不存在」处理。
 */
@Tag(name = "我的订单")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderCommandService orderCommandService;
    private final OrderQueryService orderQueryService;
    private final IdempotencyService idempotencyService;
    private final PaymentService paymentService;

    /** 下单：扣库存、生成订单与明细快照，事务失败整体回滚 */
    @Operation(summary = "下单")
    @PostMapping
    public Result<OrderMain> create(@Valid @RequestBody OrderCreateRequest req,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Long userId = SecurityUtils.userId();
        String scope = "order:" + userId;

        java.util.Optional<String> replay = idempotencyService.tryAcquire(scope, idempotencyKey);
        if (replay.isPresent()) {

            return Result.ok(orderQueryService.findOwned(userId, replay.get()));
        }
        try {
            OrderMain order = orderCommandService.create(userId, req);
            idempotencyService.complete(scope, idempotencyKey, order.getOrderNo());
            return Result.ok(order);
        } catch (RuntimeException e) {
            idempotencyService.release(scope, idempotencyKey);
            throw e;
        }
    }

    /** 我的订单分页列表 */
    @Operation(summary = "我的订单")
    @GetMapping
    public Result<com.carepilot.common.api.PageResult<OrderMain>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return Result.ok(com.carepilot.common.api.PageResult.of(
                orderQueryService.pageMine(SecurityUtils.userId(), page, size)));
    }

    /** 订单详情：主单 + 明细 + 物流轨迹 + 支付/退款记录 */
    @Operation(summary = "订单详情")
    @GetMapping("/{orderNo}")
    public Result<OrderCommandService.OrderDetail> detail(@PathVariable String orderNo) {
        return Result.ok(orderCommandService.detail(SecurityUtils.userId(), orderNo));
    }

    /** 支付订单；目前只允许余额渠道，扣款成功才置为已支付 */
    @Operation(summary = "订单支付")
    @PostMapping("/{orderNo}/pay")
    public Result<PayRecord> pay(@PathVariable String orderNo,
                                 @RequestBody(required = false) java.util.Map<String, String> body) {
        String channel = body == null ? null : body.get("channel");
        return Result.ok(paymentService.pay(SecurityUtils.userId(), orderNo, channel));
    }

    /** 订单的支付单与退款单记录，用于详情页展示 */
    @Operation(summary = "订单的支付与退款记录")
    @GetMapping("/{orderNo}/payments")
    public Result<java.util.Map<String, Object>> payments(@PathVariable String orderNo) {
        Long userId = SecurityUtils.userId();
        orderQueryService.findOwned(userId, orderNo);
        return Result.ok(java.util.Map.of(
                "payments", paymentService.payRecords(orderNo),
                "refunds", paymentService.refundRecords(orderNo)));
    }

    /** 取消订单：回补库存；已付款的再原路退回余额 */
    @Operation(summary = "取消订单")
    @PostMapping("/{orderNo}/cancel")
    public Result<OrderMain> cancel(@PathVariable String orderNo) {
        return Result.ok(orderCommandService.cancel(SecurityUtils.userId(), orderNo));
    }

    /** 确认收货：只有已发货可确认，售后中/已退款会被拒绝 */
    @Operation(summary = "确认收货")
    @PostMapping("/{orderNo}/receive")
    public Result<OrderMain> receive(@PathVariable String orderNo) {
        return Result.ok(orderCommandService.receive(SecurityUtils.userId(), orderNo));
    }
}