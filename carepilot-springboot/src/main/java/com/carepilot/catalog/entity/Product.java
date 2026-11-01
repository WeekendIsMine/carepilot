package com.carepilot.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("catalog_product")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long categoryId;
    private String name;
    private String subtitle;
    private String description;
    private String coverUrl;

    private String gallery;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer stock;
    private Integer returnDays;
    private Integer status;
    @TableLogic
    private Integer deleted;
}