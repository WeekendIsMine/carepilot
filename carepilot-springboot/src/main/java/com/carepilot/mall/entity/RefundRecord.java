package com.carepilot.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("refund_record")
public class RefundRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String refundNo;
    private String ticketNo;
    private String orderNo;
    private String payNo;
    private Long userId;

    private String channel;
    private BigDecimal amount;

    private String status;
    private LocalDateTime refundedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
