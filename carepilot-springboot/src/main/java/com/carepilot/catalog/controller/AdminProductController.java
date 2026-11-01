package com.carepilot.catalog.controller;

import com.carepilot.catalog.dto.ProductForm;
import com.carepilot.catalog.entity.Product;
import com.carepilot.catalog.service.ProductAdminService;
import com.carepilot.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 后台商品管理：增删改、上下架、主图与图集上传。
 */
@Tag(name = "后台商品管理")
@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductAdminService productAdminService;

    /** 商品分页列表，支持标题搜索和状态筛选 */
    @Operation(summary = "商品列表（含下架）")
    @GetMapping
    public Result<com.carepilot.common.api.PageResult<Product>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return Result.ok(com.carepilot.common.api.PageResult.of(productAdminService.list(keyword, page, size)));
    }

    /** 新建商品 */
    @Operation(summary = "新建商品")
    @PostMapping
    public Result<Product> create(@Valid @RequestBody ProductForm form) {
        return Result.ok(productAdminService.create(form));
    }

    /** 修改商品，改完清掉商品缓存 */
    @Operation(summary = "编辑商品")
    @PutMapping("/{id}")
    public Result<Product> update(@PathVariable Long id, @Valid @RequestBody ProductForm form) {
        return Result.ok(productAdminService.update(id, form));
    }

    /** 上架 / 下架 */
    @Operation(summary = "上架/下架")
    @PostMapping("/{id}/status")
    public Result<Product> toggle(@PathVariable Long id) {
        return Result.ok(productAdminService.toggleStatus(id));
    }

    /** 删除商品；已有订单的拒绝删除，要求改下架 */
    @Operation(summary = "删除商品")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productAdminService.delete(id);
        return Result.ok();
    }

    /** 上传主图：校验文件头真实格式，写 OSS 后返回 URL */
    @Operation(summary = "上传商品图（不绑定商品，新建时使用）")
    @PostMapping("/image")
    public Result<Map<String, String>> image(@RequestPart("file") MultipartFile file) {
        return Result.ok(Map.of("url", productAdminService.uploadImage(file)));
    }

    /** 换主图，同时删掉 OSS 上的旧图 */
    @Operation(summary = "上传主图（OSS）")
    @PostMapping("/{id}/cover")
    public Result<Map<String, String>> cover(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return Result.ok(Map.of("url", productAdminService.uploadCover(id, file)));
    }

    /** 追加图集 */
    @Operation(summary = "上传详情图（OSS）")
    @PostMapping("/{id}/gallery")
    public Result<List<String>> gallery(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return Result.ok(productAdminService.addGallery(id, file));
    }

    /** 从图集里删一张 */
    @Operation(summary = "删除详情图")
    @DeleteMapping("/{id}/gallery")
    public Result<List<String>> removeGallery(@PathVariable Long id, @RequestParam String url) {
        return Result.ok(productAdminService.removeGallery(id, url));
    }
}