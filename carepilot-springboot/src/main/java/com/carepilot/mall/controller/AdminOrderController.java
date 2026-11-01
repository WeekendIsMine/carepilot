package com.carepilot.mall.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carepilot.common.api.Result;
import com.carepilot.mall.dto.ShipRequest;
import com.carepilot.mall.service.OrderCommandService;
import com.carepilot.order.entity.OrderMain;
import com.carepilot.order.mapper.OrderMainMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 后台订单管理：列表（可按状态筛）和发货。
 */
@Tag(name = "后台订单")
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final OrderMainMapper orderMapper;
    private final OrderCommandService orderCommandService;

    /** 订单分页列表，status 为空表示全部 */
    @Operation(summary = "订单列表")
    @GetMapping
    public Result<com.carepilot.common.api.PageResult<OrderMain>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return Result.ok(com.carepilot.common.api.PageResult.of(orderMapper.selectPage(
                com.carepilot.common.api.Pages.of(page, size),
                Wrappers.<OrderMain>lambdaQuery()
                        .eq(status != null, OrderMain::getStatus, status)
                        .orderByDesc(OrderMain::getId))));
    }

    /** 发货：只有待发货订单可以发货，写入快递公司与运单号 */
    @Operation(summary = "发货")
    @PostMapping("/{orderNo}/ship")
    public Result<OrderMain> ship(@PathVariable String orderNo, @RequestBody(required = false) ShipRequest req) {
        return Result.ok(orderCommandService.ship(orderNo, req));
    }
}