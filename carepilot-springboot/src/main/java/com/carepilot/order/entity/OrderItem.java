package com.carepilot.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("order_item")
public class OrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private String orderNo;
    private Long productId;
    private String productName;
    private String productCover;
    private BigDecimal unitPrice;
    private Integer quantity;

    private Integer returnDays;
    private BigDecimal amount;
}