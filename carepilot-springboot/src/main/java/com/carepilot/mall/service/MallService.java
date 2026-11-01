package com.carepilot.mall.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carepilot.catalog.entity.Category;
import com.carepilot.catalog.entity.Product;
import com.carepilot.catalog.mapper.CategoryMapper;
import com.carepilot.catalog.mapper.ProductMapper;
import com.carepilot.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 商城只读查询：搜索、详情、类目。读商品走缓存。
 */
@Service
@RequiredArgsConstructor
public class MallService {

    private final ProductMapper productMapper;
    private final com.carepilot.catalog.service.ProductCache productCache;
    private final CategoryMapper categoryMapper;

    /** 关键词 + 类目搜索，只返回上架商品 */
    public List<Product> search(String keyword, Long categoryId) {
        return productMapper.selectList(Wrappers.<Product>lambdaQuery()
                .eq(Product::getStatus, 1)
                .eq(categoryId != null, Product::getCategoryId, categoryId)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Product::getName, keyword)
                        .or().like(Product::getSubtitle, keyword))
                .orderByAsc(Product::getId)
                .last("limit 60"));
    }

    /** 商品详情，缓存优先 */
    public Product detail(Long id) {
        Product p = productCache.get(id);
        if (p == null || p.getStatus() == null || p.getStatus() != 1) {
            throw BizException.notFound("商品不存在或已下架");
        }
        return p;
    }

    /** 类目列表，按 sort 排序 */
    public List<Category> categories() {
        return categoryMapper.selectList(Wrappers.<Category>lambdaQuery()
                .eq(Category::getParentId, 0L)
                .orderByAsc(Category::getSort));
    }
}