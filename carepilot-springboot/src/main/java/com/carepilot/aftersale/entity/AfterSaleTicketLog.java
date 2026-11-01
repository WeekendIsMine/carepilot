package com.carepilot.aftersale.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("after_sale_ticket_log")
public class AfterSaleTicketLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ticketId;
    private String fromStatus;
    private String toStatus;
    private String action;
    private Long operatorId;
    private String operatorRole;
    private String remark;
    private LocalDateTime createdAt;
}
