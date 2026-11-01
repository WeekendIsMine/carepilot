package com.carepilot.catalog.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carepilot.catalog.dto.ProductForm;
import com.carepilot.catalog.entity.Product;
import com.carepilot.catalog.mapper.ProductMapper;
import com.carepilot.common.exception.BizException;
import com.carepilot.common.storage.OssStorage;
import com.carepilot.mall.entity.CartItem;
import com.carepilot.mall.mapper.CartItemMapper;
import com.carepilot.order.entity.OrderItem;
import com.carepilot.order.mapper.OrderItemMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 后台商品管理：增删改查、上下架、主图与图集上传。
 * 图片按文件头魔数校验真实格式，不信任客户端 Content-Type。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductAdminService {

    private static final long MAX_IMAGE = 5 * 1024 * 1024L;
    private static final int MAX_GALLERY = 6;

    private final ProductMapper productMapper;
    private final CartItemMapper cartMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductCache productCache;
    private final OssStorage oss;
    private final ObjectMapper objectMapper;

    /** 分页列表，可按标题搜索和状态过滤 */
    public com.baomidou.mybatisplus.core.metadata.IPage<Product> list(String keyword, Integer page, Integer size) {
        return productMapper.selectPage(
                com.carepilot.common.api.Pages.of(page, size),
                Wrappers.<Product>lambdaQuery()
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(Product::getName, keyword)
                                .or().like(Product::getSubtitle, keyword))
                        .orderByAsc(Product::getId));
    }

    /** 新建商品 */
    @Transactional
    public Product create(ProductForm form) {
        Product p = new Product();
        apply(p, form);
        p.setStatus(form.getStatus() == null ? 1 : form.getStatus());
        productMapper.insert(p);
        return p;
    }

    /** 修改商品并清缓存 */
    @Transactional
    public Product update(Long id, ProductForm form) {
        Product p = require(id);
        apply(p, form);
        if (form.getStatus() != null) p.setStatus(form.getStatus());
        productMapper.updateById(p);
        productCache.evict(id);
        return productMapper.selectById(id);
    }

    /** 上下架 */
    @Transactional
    public Product toggleStatus(Long id) {
        Product p = require(id);
        Product patch = new Product();
        patch.setId(id);
        patch.setStatus(p.getStatus() != null && p.getStatus() == 1 ? 0 : 1);
        productMapper.updateById(patch);
        productCache.evict(id);
        return productMapper.selectById(id);
    }

    /** 删除；有订单的拒绝删除，提示改下架 */
    @Transactional
    public void delete(Long id) {
        Product p = require(id);

        Long ordered = orderItemMapper.selectCount(Wrappers.<OrderItem>lambdaQuery()
                .eq(OrderItem::getProductId, id));
        if (ordered != null && ordered > 0) {
            throw BizException.conflict("该商品已产生 " + ordered + " 笔订单，请改为下架而不是删除");
        }

        cartMapper.delete(Wrappers.<CartItem>lambdaQuery().eq(CartItem::getProductId, id));
        productMapper.deleteById(id);
        productCache.evict(id);

        if (p.getCoverUrl() != null) {
            oss.delete(p.getCoverUrl());
        }
        for (String url : parseGallery(p.getGallery())) {
            oss.delete(url);
        }
        log.info("product deleted: {} ({})", id, p.getName());
    }

    /** 上传图片到 OSS，返回 URL */
    public String uploadImage(MultipartFile file) {
        byte[] bytes = readImage(file);
        String type = sniff(bytes);
        String key = "products/cover-" + UUID.randomUUID().toString().substring(0, 8) + extOf(type);
        String url = oss.uploadBytes(bytes, key, type);
        if (url == null) throw BizException.conflict("图片上传失败，请检查 OSS 配置");
        return url;
    }

    /** 换主图并删除 OSS 旧图 */
    public String uploadCover(Long id, MultipartFile file) {
        Product p = require(id);
        byte[] bytes = readImage(file);
        String type = sniff(bytes);
        String key = "products/" + id + "/cover-" + UUID.randomUUID().toString().substring(0, 8) + extOf(type);
        String url = oss.uploadBytes(bytes, key, type);
        if (url == null) throw BizException.conflict("图片上传失败，请检查 OSS 配置");

        String old = p.getCoverUrl();
        Product patch = new Product();
        patch.setId(id);
        patch.setCoverUrl(url);
        productMapper.updateById(patch);
        if (old != null && old.startsWith("http")) {
            oss.delete(old);
        }
        log.info("product {} cover updated -> {}", id, url);
        return url;
    }

    /** 追加图集 */
    public List<String> addGallery(Long id, MultipartFile file) {
        Product p = require(id);
        List<String> gallery = parseGallery(p.getGallery());
        if (gallery.size() >= MAX_GALLERY) throw BizException.conflict("最多 " + MAX_GALLERY + " 张详情图");

        byte[] bytes = readImage(file);
        String type = sniff(bytes);
        String key = "products/" + id + "/gallery-" + UUID.randomUUID().toString().substring(0, 8) + extOf(type);
        String url = oss.uploadBytes(bytes, key, type);
        if (url == null) throw BizException.conflict("图片上传失败，请检查 OSS 配置");

        gallery.add(url);
        Product patch = new Product();
        patch.setId(id);
        patch.setGallery(writeGallery(gallery));
        productMapper.updateById(patch);
        return gallery;
    }

    /** 从图集删除一张 */
    public List<String> removeGallery(Long id, String url) {
        Product p = require(id);
        List<String> gallery = parseGallery(p.getGallery());
        if (gallery.remove(url)) {
            Product patch = new Product();
            patch.setId(id);
            patch.setGallery(writeGallery(gallery));
            productMapper.updateById(patch);
            oss.delete(url);
        }
        return gallery;
    }

    private void apply(Product p, ProductForm f) {
        if (f.getCoverUrl() != null) {
            p.setCoverUrl(f.getCoverUrl());
        }
        p.setName(f.getName());
        p.setSubtitle(f.getSubtitle());
        p.setDescription(f.getDescription());
        p.setCategoryId(f.getCategoryId());
        p.setPrice(f.getPrice());
        p.setOriginalPrice(f.getOriginalPrice());
        p.setStock(f.getStock() == null ? 0 : f.getStock());
        p.setReturnDays(f.getReturnDays() == null ? 7 : f.getReturnDays());
    }

    private Product require(Long id) {
        Product p = productMapper.selectById(id);
        if (p == null) throw BizException.notFound("商品不存在");
        return p;
    }

    private byte[] readImage(MultipartFile file) {
        if (file == null || file.isEmpty()) throw BizException.conflict("请选择图片");
        if (file.getSize() > MAX_IMAGE) throw BizException.conflict("图片不能超过 5MB");
        try {
            byte[] bytes = file.getBytes();
            if (sniff(bytes) == null) throw BizException.conflict("只支持 jpg / png / webp 图片");
            return bytes;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw BizException.conflict("读取图片失败");
        }
    }

    private String sniff(byte[] b) {
        if (b == null || b.length < 12) return null;
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        return null;
    }

    private String extOf(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    /** 图集字段（JSON 字符串）与列表互转 */
    public List<String> parseGallery(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return new ArrayList<>(objectMapper.readValue(json, new TypeReference<List<String>>() {}));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String writeGallery(List<String> urls) {
        try {
            return objectMapper.writeValueAsString(urls);
        } catch (Exception e) {
            return "[]";
        }
    }
}