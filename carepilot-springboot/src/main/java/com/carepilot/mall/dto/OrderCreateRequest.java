package com.carepilot.mall.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class OrderCreateRequest {
    @NotNull
    private Long addressId;
    private String remark;

    private List<Line> items;

    @Data
    public static class Line {
        private Long productId;
        private int quantity;
    }
}
