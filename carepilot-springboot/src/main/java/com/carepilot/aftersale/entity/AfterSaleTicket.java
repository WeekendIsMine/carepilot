package com.carepilot.aftersale.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("after_sale_ticket")
public class AfterSaleTicket {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String ticketNo;
    private String orderNo;
    private Long userId;
    private Long sessionId;

    private String type;
    private String reason;
    private String description;

    private String status;

    private String source;
    private BigDecimal amount;
    private String images;
    private Integer version;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
