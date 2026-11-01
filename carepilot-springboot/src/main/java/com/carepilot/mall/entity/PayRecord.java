package com.carepilot.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("pay_record")
public class PayRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String payNo;
    private String orderNo;
    private Long userId;

    private String channel;
    private BigDecimal amount;

    private String status;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
