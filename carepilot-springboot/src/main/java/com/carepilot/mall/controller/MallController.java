package com.carepilot.mall.controller;

import com.carepilot.catalog.entity.Category;
import com.carepilot.catalog.entity.Product;
import com.carepilot.mall.dto.CartAddRequest;
import com.carepilot.mall.service.CartService;
import com.carepilot.mall.service.MallService;
import com.carepilot.common.api.Result;
import com.carepilot.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 商城前台接口：商品浏览、类目、购物车。都要求已登录，数据按当前用户隔离。
 */
@Tag(name = "商城")
@RestController
@RequestMapping("/api/mall")
@RequiredArgsConstructor
public class MallController {

    private final MallService mallService;
    private final CartService cartService;

    /** 商品列表，支持关键词模糊搜索和类目筛选；走商品缓存 */
    @Operation(summary = "商品列表 / 搜索")
    @GetMapping("/products")
    public Result<List<Product>> products(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Long categoryId) {
        return Result.ok(mallService.search(keyword, categoryId));
    }

    /** 商品详情，读缓存失败再回源并回填 */
    @Operation(summary = "商品详情")
    @GetMapping("/products/{id}")
    public Result<Product> product(@PathVariable Long id) {
        return Result.ok(mallService.detail(id));
    }

    /** 类目列表，商城顶部的分类筛选按钮 */
    @Operation(summary = "类目")
    @GetMapping("/categories")
    public Result<List<Category>> categories() {
        return Result.ok(mallService.categories());
    }

    /** 当前用户购物车，返回行项目、金额和库存快照 */
    @Operation(summary = "购物车")
    @GetMapping("/cart")
    public Result<List<CartService.CartLine>> cart() {
        return Result.ok(cartService.list(SecurityUtils.userId()));
    }

    /** 加入购物车；超过库存直接报错，不做静默截断 */
    @Operation(summary = "加入购物车")
    @PostMapping("/cart")
    public Result<Void> add(@Valid @RequestBody CartAddRequest req) {
        cartService.add(SecurityUtils.userId(), req.getProductId(), req.getQuantity());
        return Result.ok();
    }

    /** 修改购物车某行的数量，同样校验库存 */
    @Operation(summary = "修改数量")
    @PutMapping("/cart/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        cartService.updateQuantity(SecurityUtils.userId(), id, body.getOrDefault("quantity", 1));
        return Result.ok();
    }

    /** 从购物车移除一行 */
    @Operation(summary = "移出购物车")
    @DeleteMapping("/cart/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        cartService.remove(SecurityUtils.userId(), id);
        return Result.ok();
    }
}
