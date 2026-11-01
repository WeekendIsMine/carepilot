package com.carepilot.mall.dto;

import lombok.Data;

@Data
public class ShipRequest {
    private String carrier;
    private String trackingNo;
}
