package com.carepilot.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("wallet_ledger")
public class WalletLedger {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;

    private String changeType;

    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String bizNo;
    private String remark;
    private LocalDateTime createdAt;
}
