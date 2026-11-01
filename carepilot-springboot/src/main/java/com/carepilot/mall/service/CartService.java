package com.carepilot.mall.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carepilot.catalog.entity.Product;
import com.carepilot.catalog.mapper.ProductMapper;
import com.carepilot.common.exception.BizException;
import com.carepilot.mall.entity.CartItem;
import com.carepilot.mall.mapper.CartItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 购物车。加购和改数量都会校验库存，超了直接报错，不做静默截断。
 * 列表每次都要回查商品，保证价格和库存是最新的。
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemMapper cartMapper;
    private final ProductMapper productMapper;

    public record CartLine(Long id, Long productId, String name, String subtitle, BigDecimal price,
                           Integer stock, int quantity, boolean selected, BigDecimal amount, int returnDays) {}

    /** 我的购物车行项目：拼上商品最新价格、库存，算出小计 */
    public List<CartLine> list(Long userId) {
        List<CartItem> items = cartMapper.selectList(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId)
                .orderByDesc(CartItem::getUpdatedAt));
        if (items.isEmpty()) return List.of();

        Map<Long, Product> products = productMapper.selectBatchIds(
                        items.stream().map(CartItem::getProductId).toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));

        List<CartLine> lines = new ArrayList<>();
        for (CartItem it : items) {
            Product p = products.get(it.getProductId());
            if (p == null) continue;
            BigDecimal amount = p.getPrice().multiply(BigDecimal.valueOf(it.getQuantity()));
            lines.add(new CartLine(it.getId(), p.getId(), p.getName(), p.getSubtitle(), p.getPrice(),
                    p.getStock(), it.getQuantity(), it.getSelected() == 1, amount, p.getReturnDays()));
        }
        return lines;
    }

    /** 加购：已有则累加；累加后超过库存就报错，并说明购物车里已有多少件 */
    @Transactional
    public void add(Long userId, Long productId, int quantity) {
        Product p = productMapper.selectById(productId);
        if (p == null || p.getStatus() != 1) throw BizException.notFound("商品不存在或已下架");
        if (quantity < 1) throw BizException.conflict("数量至少为 1");
        int stock = p.getStock() == null ? 0 : p.getStock();
        if (stock <= 0) throw BizException.conflict("「" + p.getName() + "」已售罄");

        CartItem exist = cartMapper.selectOne(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getProductId, productId));

        int wanted = exist == null ? quantity : exist.getQuantity() + quantity;
        if (wanted > stock) {
            throw BizException.conflict("「" + p.getName() + "」库存不足，当前仅剩 " + stock + " 件"
                    + (exist == null ? "" : "（购物车里已有 " + exist.getQuantity() + " 件）"));
        }

        if (exist == null) {
            CartItem c = new CartItem();
            c.setUserId(userId);
            c.setProductId(productId);
            c.setQuantity(quantity);
            c.setSelected(1);
            cartMapper.insert(c);
        } else {
            CartItem patch = new CartItem();
            patch.setId(exist.getId());
            patch.setQuantity(wanted);
            cartMapper.updateById(patch);
        }
    }

    /** 改数量：小于 1 或超过库存都拒绝 */
    @Transactional
    public void updateQuantity(Long userId, Long id, int quantity) {
        CartItem c = require(userId, id);
        Product p = productMapper.selectById(c.getProductId());
        if (quantity < 1) throw BizException.conflict("数量至少为 1");
        if (p == null || p.getStatus() != 1) throw BizException.notFound("商品不存在或已下架");
        int stock = p.getStock() == null ? 0 : p.getStock();
        if (quantity > stock) {
            throw BizException.conflict("「" + p.getName() + "」库存不足，当前仅剩 " + stock + " 件");
        }
        CartItem patch = new CartItem();
        patch.setId(id);
        patch.setQuantity(quantity);
        cartMapper.updateById(patch);
    }

    /** 移除一行 */
    public void remove(Long userId, Long id) {
        require(userId, id);
        cartMapper.deleteById(id);
    }

    /** 取一行并校验归属，找不到按不存在处理 */
    public CartItem require(Long userId, Long id) {
        CartItem c = cartMapper.selectById(id);
        if (c == null || !c.getUserId().equals(userId)) throw BizException.notFound("购物车条目不存在");
        return c;
    }

    /** 结算时取勾选的行 */
    public List<CartItem> selected(Long userId) {
        return cartMapper.selectList(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getSelected, 1));
    }

    /** 下单成功后清掉已勾选的行 */
    public void clear(Long userId, List<Long> ids) {
        if (ids.isEmpty()) return;
        cartMapper.delete(Wrappers.<CartItem>lambdaQuery()
                .eq(CartItem::getUserId, userId)
                .in(CartItem::getId, ids));
    }
}