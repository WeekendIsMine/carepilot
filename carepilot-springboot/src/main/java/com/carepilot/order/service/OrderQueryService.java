package com.carepilot.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carepilot.catalog.entity.Product;
import com.carepilot.catalog.mapper.ProductMapper;
import com.carepilot.order.entity.LogisticsTrace;
import com.carepilot.order.entity.OrderItem;
import com.carepilot.order.entity.OrderMain;
import com.carepilot.order.mapper.LogisticsTraceMapper;
import com.carepilot.order.mapper.OrderItemMapper;
import com.carepilot.order.mapper.OrderMainMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 订单读操作：状态名、归属校验、分页、明细、物流，以及售后资格判定。
 */
@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderMainMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final LogisticsTraceMapper logisticsMapper;
    private final ProductMapper productMapper;

    public static final String[] STATUS_NAMES = {"待付款", "待发货", "已发货", "已完成", "已取消", "售后中", "已退款"};

    /** 数字状态转中文名 */
    public String statusName(Integer status) {
        if (status == null || status < 0 || status >= STATUS_NAMES.length) return "未知";
        return STATUS_NAMES[status];
    }

    /** 取订单并校验归属 */
    public OrderMain findOwned(Long userId, String orderNo) {
        if (orderNo == null) return null;
        OrderMain o = orderMapper.selectOne(Wrappers.<OrderMain>lambdaQuery()
                .eq(OrderMain::getOrderNo, orderNo.trim().toUpperCase()));
        if (o == null || !o.getUserId().equals(userId)) {
            return null;
        }
        return o;
    }

    /** 我的订单分页 */
    public com.baomidou.mybatisplus.core.metadata.IPage<OrderMain> pageMine(Long userId, Integer page, Integer size) {
        return orderMapper.selectPage(com.carepilot.common.api.Pages.of(page, size),
                Wrappers.<OrderMain>lambdaQuery()
                        .eq(OrderMain::getUserId, userId)
                        .orderByDesc(OrderMain::getId));
    }

    /** 我的订单列表（不分页） */
    public List<OrderMain> listMine(Long userId, int limit) {
        return orderMapper.selectList(Wrappers.<OrderMain>lambdaQuery()
                .eq(OrderMain::getUserId, userId)
                .orderByDesc(OrderMain::getId)
                .last("limit " + Math.min(limit, 20)));
    }

    /** 订单明细快照 */
    public List<OrderItem> items(Long orderId) {
        return itemMapper.selectList(Wrappers.<OrderItem>lambdaQuery().eq(OrderItem::getOrderId, orderId));
    }

    /** 物流轨迹 */
    public LogisticsTrace logistics(String orderNo) {
        return logisticsMapper.selectOne(Wrappers.<LogisticsTrace>lambdaQuery()
                .eq(LogisticsTrace::getOrderNo, orderNo));
    }

    /** 明细里对应的商品 */
    public Product product(Long productId) {
        return productId == null ? null : productMapper.selectById(productId);
    }

    @Data
    @Builder
    public static class PolicyCheck {
        private boolean allowed;
        private String reason;
        private String orderNo;
        private String orderStatus;
        private String productName;
        private BigDecimal refundAmount;
        private LocalDateTime signedAt;
        private Integer returnDays;
        private long daysLeft;
    }

    /** 判断这单能不能申请售后：状态、期限、是否已有工单 */
    public PolicyCheck evaluate(Long userId, String orderNo) {
        OrderMain o = findOwned(userId, orderNo);
        if (o == null) {
            return PolicyCheck.builder().allowed(false).orderNo(orderNo)
                    .reason("没有查询到该订单，请确认订单号是否正确").build();
        }
        List<OrderItem> items = items(o.getId());
        String productName = items.isEmpty() ? "-" : items.get(0).getProductName();

        Integer snapshot = items.isEmpty() ? null : items.get(0).getReturnDays();
        int returnDays;
        if (snapshot != null) {
            returnDays = snapshot;
        } else {
            Product p = items.isEmpty() ? null : product(items.get(0).getProductId());
            returnDays = p == null || p.getReturnDays() == null ? 7 : p.getReturnDays();
        }

        LocalDateTime signedAt = signedTime(o);
        PolicyCheck.PolicyCheckBuilder b = PolicyCheck.builder()
                .orderNo(o.getOrderNo())
                .orderStatus(statusName(o.getStatus()))
                .productName(productName)
                .refundAmount(o.getPayAmount())
                .signedAt(signedAt)
                .returnDays(returnDays);

        if (o.getStatus() == 4) return b.allowed(false).reason("订单已取消，无需申请售后").build();
        if (o.getStatus() == 5) return b.allowed(false).reason("该订单已有处理中的售后申请").build();
        if (o.getStatus() == 0) return b.allowed(false).reason("订单未付款，无法申请售后").build();
        if (o.getStatus() == 1) return b.allowed(true).reason("订单尚未发货，可直接申请退款").build();
        if (o.getStatus() == 2) return b.allowed(true).reason("订单运输中，可申请退款或拒收").build();

        if (signedAt == null) return b.allowed(false).reason("未查到签收时间，请联系人工核实").build();
        long days = ChronoUnit.DAYS.between(signedAt, LocalDateTime.now());
        long left = returnDays - days;
        if (left < 0) {
            return b.allowed(false).daysLeft(0)
                    .reason("已签收 " + days + " 天，超过 " + returnDays + " 天无理由退货期限").build();
        }
        return b.allowed(true).daysLeft(left)
                .reason("已签收 " + days + " 天，仍在 " + returnDays + " 天期限内，剩余 " + left + " 天").build();
    }

    private LocalDateTime signedTime(OrderMain o) {
        LogisticsTrace t = logistics(o.getOrderNo());
        if (t != null && t.getSignedTime() != null) return t.getSignedTime();
        return o.getReceiveTime();
    }
}