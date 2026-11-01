package com.carepilot.catalog.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductForm {
    @NotBlank(message = "商品名不能为空")
    private String name;
    private String subtitle;
    private String description;
    @NotNull(message = "类目不能为空")
    private Long categoryId;
    @NotNull(message = "价格不能为空")
    private BigDecimal price;
    private BigDecimal originalPrice;
    @Min(0)
    private Integer stock = 0;
    @Min(1)
    private Integer returnDays = 7;
    private Integer status = 1;

    private String coverUrl;
}