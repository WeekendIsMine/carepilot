package com.carepilot.mall.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carepilot.catalog.entity.Product;
import com.carepilot.catalog.mapper.ProductMapper;
import com.carepilot.common.exception.BizException;
import com.carepilot.common.util.Ids;
import com.carepilot.mall.dto.OrderCreateRequest;
import com.carepilot.mall.dto.ShipRequest;
import com.carepilot.mall.entity.CartItem;
import com.carepilot.mall.entity.MallAddress;
import com.carepilot.order.entity.LogisticsTrace;
import com.carepilot.order.entity.OrderItem;
import com.carepilot.order.entity.OrderMain;
import com.carepilot.order.mapper.LogisticsTraceMapper;
import com.carepilot.order.mapper.OrderItemMapper;
import com.carepilot.order.mapper.OrderMainMapper;
import com.carepilot.order.service.OrderQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单写操作：下单、取消、收货、发货，以及售后带来的状态流转。
 * 库存用条件更新（stock >= 数量）保证并发下不会超卖。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCommandService {

    private final OrderMainMapper orderMapper;
    private final PaymentService paymentService;
    private final OrderItemMapper itemMapper;
    private final LogisticsTraceMapper logisticsMapper;
    private final ProductMapper productMapper;
    private final CartService cartService;
    private final AddressService addressService;
    private final OrderQueryService orderQueryService;
    private final com.carepilot.catalog.service.ProductCache productCache;

    public record OrderDetail(OrderMain order, List<OrderItem> items, LogisticsTrace logistics, String statusName) {}

    /** 下单：逐行原子扣库存并写明细快照，任何一行不够就整体失败 */
    @Transactional
    public OrderMain create(Long userId, OrderCreateRequest req) {
        MallAddress addr = addressService.require(userId, req.getAddressId());

        List<OrderCreateRequest.Line> lines = new ArrayList<>();
        List<Long> cartIds = new ArrayList<>();
        if (req.getItems() != null && !req.getItems().isEmpty()) {
            lines.addAll(req.getItems());
        } else {
            // 购物车结算和「立即购买」走同一个入口：传了 items 就直接买，没传就用购物车里勾选的
            List<CartItem> selected = cartService.selected(userId);
            if (selected.isEmpty()) throw BizException.conflict("购物车里没有勾选的商品");
            for (CartItem c : selected) {
                OrderCreateRequest.Line l = new OrderCreateRequest.Line();
                l.setProductId(c.getProductId());
                l.setQuantity(c.getQuantity());
                lines.add(l);
                cartIds.add(c.getId());
            }
        }

        BigDecimal total = BigDecimal.ZERO;
        List<OrderItem> snapshots = new ArrayList<>();
        for (OrderCreateRequest.Line line : lines) {
            if (line.getQuantity() <= 0) throw BizException.conflict("数量必须大于 0");
            Product p = productMapper.selectById(line.getProductId());
            if (p == null || p.getStatus() != 1) throw BizException.notFound("商品已下架：" + line.getProductId());

            // 关键点：库存用「条件更新」扣减，SQL 里带 stock >= 数量
            // 并发下单时只有库存够的那个事务能更新成功，影响行数为 0 就说明被别人抢完了
            int rows = productMapper.update(null, Wrappers.<Product>lambdaUpdate()
                    .setSql("stock = stock - " + line.getQuantity())
                    .eq(Product::getId, p.getId())
                    .ge(Product::getStock, line.getQuantity()));
            if (rows == 0) {
                throw BizException.conflict("「" + p.getName() + "」库存不足，当前仅剩 "
                        + (p.getStock() == null ? 0 : p.getStock()) + " 件，请调整数量");
            }
            // 库存变了缓存必须失效，否则前台还会看到旧库存
            productCache.evict(p.getId());

            BigDecimal amount = p.getPrice().multiply(BigDecimal.valueOf(line.getQuantity()));
            total = total.add(amount);

            OrderItem item = new OrderItem();
            item.setProductId(p.getId());
            item.setProductName(p.getName());
            item.setProductCover(p.getCoverUrl());
            item.setUnitPrice(p.getPrice());
            item.setQuantity(line.getQuantity());
            item.setReturnDays(p.getReturnDays());
            item.setAmount(amount);
            snapshots.add(item);
        }

        OrderMain order = new OrderMain();
        order.setOrderNo(Ids.of("CP"));
        order.setUserId(userId);
        order.setTotalAmount(total);
        order.setPayAmount(total);
        order.setStatus(0);
        order.setReceiverName(addr.getReceiverName());
        order.setReceiverPhone(addr.getReceiverPhone());
        order.setReceiverAddress(addr.fullAddress());
        order.setRemark(req.getRemark());
        orderMapper.insert(order);

        for (OrderItem item : snapshots) {
            item.setOrderId(order.getId());
            item.setOrderNo(order.getOrderNo());
            itemMapper.insert(item);
        }
        cartService.clear(userId, cartIds);

        log.info("order created no={} user={} amount={} items={}",
                order.getOrderNo(), userId, total, snapshots.size());
        return order;
    }

    /** 取消：条件更新抢占状态防并发重复回补；已付款的再原路退款 */
    @Transactional
    public OrderMain cancel(Long userId, String orderNo) {
        OrderMain o = requireOwned(userId, orderNo);
        if (o.getStatus() != 0 && o.getStatus() != 1 && o.getStatus() != 4) {
            throw BizException.conflict("已发货的订单请走售后，不能直接取消");
        }

        int changed = orderMapper.update(null, Wrappers.<OrderMain>lambdaUpdate()
                .set(OrderMain::getStatus, 4)
                .eq(OrderMain::getId, o.getId())
                .in(OrderMain::getStatus, 0, 1));
        // 条件更新抢占状态：并发点两次取消，只有一个能把 0/1 改成 4
        // 抢不到的直接报错，避免库存被重复回补
        if (changed == 0) {
            throw BizException.conflict("订单状态已变更，请刷新后重试");
        }
        restoreStock(o.getId());

        // 取消的是已付款订单，钱要原路退回；未付款订单不涉及退款
        if (o.getStatus() == 1) {
            java.math.BigDecimal refunded = paymentService.refundOnCancel(userId, orderNo);
            log.info("order {} canceled, refund={}", orderNo, refunded);
        }
        return orderMapper.selectById(o.getId());
    }

    /** 确认收货：售后中/已退款会被拒绝 */
    @Transactional
    public OrderMain receive(Long userId, String orderNo) {
        OrderMain o = requireOwned(userId, orderNo);
        // 售后中/已退款都不允许确认收货，否则会和退款流程打架
        if (o.getStatus() == 5) throw BizException.conflict("该订单正在售后处理中，不能确认收货");
        if (o.getStatus() == 6) throw BizException.conflict("该订单已退款，无需确认收货");
        if (o.getStatus() != 2) throw BizException.conflict("只有已发货订单可以确认收货");
        OrderMain patch = new OrderMain();
        patch.setId(o.getId());
        patch.setStatus(3);
        patch.setReceiveTime(LocalDateTime.now());
        orderMapper.updateById(patch);
        return orderMapper.selectById(o.getId());
    }

    /** 售后建单：订单进入「售后中」，待发货/已发货/已完成都适用 */
    @Transactional
    public void markAfterSale(String orderNo) {
        orderMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<OrderMain>lambdaUpdate()
                .set(OrderMain::getStatus, 5)
                .eq(OrderMain::getOrderNo, orderNo)
                .in(OrderMain::getStatus, java.util.List.of(1, 2, 3)));
    }

    /**
     * 售后被拒或关闭：把订单还原到售后前的状态。
     * 用发货、收货时间反推：没发货 → 待发货(1)，发了没收回 → 已发货(2)，收了 → 已完成(3)。
     */
    @Transactional
    public void restoreAfterSale(String orderNo) {
        orderMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<OrderMain>lambdaUpdate()
                .setSql("status = CASE WHEN receive_time IS NOT NULL THEN 3 " +
                        "WHEN ship_time IS NOT NULL THEN 2 ELSE 1 END")
                .eq(OrderMain::getOrderNo, orderNo)
                .eq(OrderMain::getStatus, 5));
    }

    /** 退款成功：订单置为「已退款」 */
    @Transactional
    public void markRefunded(String orderNo) {
        orderMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<OrderMain>lambdaUpdate()
                .set(OrderMain::getStatus, 6)
                .eq(OrderMain::getOrderNo, orderNo));
    }

    /** 后台发货，写入快递信息并置为已发货 */
    @Transactional
    public OrderMain ship(String orderNo, ShipRequest req) {
        OrderMain o = orderMapper.selectOne(Wrappers.<OrderMain>lambdaQuery().eq(OrderMain::getOrderNo, orderNo));
        if (o == null) throw BizException.notFound("订单不存在");
        if (o.getStatus() != 1) {
            String[] names = {"待付款", "待发货", "已发货", "已完成", "已取消", "售后中", "已退款"};
            String current = o.getStatus() >= 0 && o.getStatus() < names.length ? names[o.getStatus()] : String.valueOf(o.getStatus());
            throw BizException.conflict("只有待发货订单可以发货，该订单当前是「" + current + "」");
        }

        OrderMain patch = new OrderMain();
        patch.setId(o.getId());
        patch.setStatus(2);
        patch.setShipTime(LocalDateTime.now());
        orderMapper.updateById(patch);

        LogisticsTrace t = new LogisticsTrace();
        t.setOrderNo(o.getOrderNo());
        t.setCarrier(req == null || req.getCarrier() == null ? "顺丰速运" : req.getCarrier());
        t.setTrackingNo(req == null || req.getTrackingNo() == null ? "SF" + System.currentTimeMillis() : req.getTrackingNo());
        t.setStatus(1);
        t.setCurrentInfo("商家已发货，等待揽收");
        t.setTraces("[]");
        logisticsMapper.insert(t);

        return orderMapper.selectById(o.getId());
    }

    /** 订单详情：主单 + 明细 + 物流 */
    public OrderDetail detail(Long userId, String orderNo) {
        OrderMain o = requireOwned(userId, orderNo);
        return new OrderDetail(o, orderQueryService.items(o.getId()),
                orderQueryService.logistics(o.getOrderNo()),
                orderQueryService.statusName(o.getStatus()));
    }

    /** 退款成功回补库存，并清商品缓存 */
    @Transactional
    public void restoreStockForRefund(String orderNo) {
        OrderMain o = orderMapper.selectOne(Wrappers.<OrderMain>lambdaQuery().eq(OrderMain::getOrderNo, orderNo));
        if (o == null) return;
        restoreStock(o.getId());
        log.info("stock restored after refund order={}", orderNo);
    }

    private void restoreStock(Long orderId) {
        for (OrderItem item : orderQueryService.items(orderId)) {
            productMapper.update(null, Wrappers.<Product>lambdaUpdate()
                    .setSql("stock = stock + " + item.getQuantity())
                    .eq(Product::getId, item.getProductId()));

            productCache.evict(item.getProductId());
        }
    }

    private OrderMain requireOwned(Long userId, String orderNo) {
        OrderMain o = orderQueryService.findOwned(userId, orderNo);
        if (o == null) throw BizException.notFound("订单不存在");
        return o;
    }
}