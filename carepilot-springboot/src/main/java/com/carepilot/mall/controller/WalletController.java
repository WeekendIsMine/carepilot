package com.carepilot.mall.controller;

import com.carepilot.common.api.PageResult;
import com.carepilot.common.api.Result;
import com.carepilot.mall.entity.Wallet;
import com.carepilot.mall.entity.WalletLedger;
import com.carepilot.mall.service.WalletService;
import com.carepilot.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 余额钱包：查余额、充值、看流水。余额只能通过流水变动。
 */
@Tag(name = "钱包")
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    /** 我的钱包，没有就懒创建一条 */
    @Operation(summary = "我的余额")
    @GetMapping
    public Result<Wallet> mine() {
        return Result.ok(walletService.getOrCreate(SecurityUtils.userId()));
    }

    /** 充值：本地没有接真实支付，直接给余额加钱并记一条流水 */
    @Operation(summary = "充值")
    @PostMapping("/recharge")
    public Result<Wallet> recharge(@RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(String.valueOf(body.get("amount")));
        return Result.ok(walletService.recharge(SecurityUtils.userId(), amount));
    }

    /** 收支流水分页：充值 / 支付 / 退款 */
    @Operation(summary = "余额流水")
    @GetMapping("/ledger")
    public Result<PageResult<WalletLedger>> ledger(@RequestParam(required = false) Integer page,
                                                   @RequestParam(required = false) Integer size) {
        return Result.ok(PageResult.of(walletService.ledger(SecurityUtils.userId(), page, size)));
    }
}