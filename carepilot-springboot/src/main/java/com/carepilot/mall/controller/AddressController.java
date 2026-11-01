package com.carepilot.mall.controller;

import com.carepilot.common.api.Result;
import com.carepilot.mall.dto.AddressRequest;
import com.carepilot.mall.entity.MallAddress;
import com.carepilot.mall.service.AddressService;
import com.carepilot.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 收货地址增删改查。省市区由用户自己填写，不预设地区。
 */
@Tag(name = "收货地址")
@RestController
@RequestMapping("/api/mall/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    /** 我的地址列表，默认地址排最前 */
    @Operation(summary = "地址列表")
    @GetMapping
    public Result<List<MallAddress>> list() {
        return Result.ok(addressService.list(SecurityUtils.userId()));
    }

    /** 新增地址；第一条自动设为默认 */
    @Operation(summary = "新增地址")
    @PostMapping
    public Result<MallAddress> create(@Valid @RequestBody AddressRequest req) {
        return Result.ok(addressService.create(SecurityUtils.userId(), req));
    }

    /** 修改地址，可顺带设为默认 */
    @Operation(summary = "修改地址")
    @PutMapping("/{id}")
    public Result<MallAddress> update(@PathVariable Long id, @Valid @RequestBody AddressRequest req) {
        return Result.ok(addressService.update(SecurityUtils.userId(), id, req));
    }

    /** 设为默认：先清掉其它默认再置位，保证全局唯一 */
    @Operation(summary = "设为默认")
    @PostMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        addressService.setDefault(SecurityUtils.userId(), id);
        return Result.ok();
    }

    /** 删除地址，删的是当前默认就从列表里消失 */
    @Operation(summary = "删除地址")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        addressService.delete(SecurityUtils.userId(), id);
        return Result.ok();
    }
}