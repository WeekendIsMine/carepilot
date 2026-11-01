package com.carepilot.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("logistics_trace")
public class LogisticsTrace {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private String carrier;
    private String trackingNo;
    private Integer status;
    private String currentInfo;

    private String traces;
    private LocalDateTime signedTime;
    private LocalDateTime updatedAt;
}
