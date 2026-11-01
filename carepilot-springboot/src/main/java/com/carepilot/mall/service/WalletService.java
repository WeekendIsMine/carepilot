package com.carepilot.mall.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carepilot.common.api.Pages;
import com.carepilot.common.exception.BizException;
import com.carepilot.mall.entity.Wallet;
import com.carepilot.mall.entity.WalletLedger;
import com.carepilot.mall.mapper.WalletLedgerMapper;
import com.carepilot.mall.mapper.WalletMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 余额钱包。余额只能通过流水变动，每笔都记 balance_after 快照。
 * 扣款用 balance >= amount 的条件更新，避免并发扣成负数。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletMapper walletMapper;
    private final WalletLedgerMapper ledgerMapper;

    /** 取钱包，没有就建一个余额为 0 的 */
    public Wallet getOrCreate(Long userId) {
        Wallet w = walletMapper.selectOne(Wrappers.<Wallet>lambdaQuery().eq(Wallet::getUserId, userId));
        if (w != null) return w;
        Wallet created = new Wallet();
        created.setUserId(userId);
        created.setBalance(BigDecimal.ZERO);
        walletMapper.insert(created);
        return walletMapper.selectById(created.getId());
    }

    /** 收支流水分页 */
    public IPage<WalletLedger> ledger(Long userId, Integer page, Integer size) {
        return ledgerMapper.selectPage(Pages.of(page, size),
                Wrappers.<WalletLedger>lambdaQuery()
                        .eq(WalletLedger::getUserId, userId)
                        .orderByDesc(WalletLedger::getId));
    }

    /** 充值：加余额并记一条 RECHARGE 流水 */
    @Transactional
    public Wallet recharge(Long userId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw BizException.conflict("充值金额必须大于 0");
        }
        if (amount.compareTo(new BigDecimal("50000")) > 0) {
            throw BizException.conflict("单次充值不能超过 50000");
        }
        return change(userId, amount, "RECHARGE", null, "余额充值");
    }

    /** 扣款：余额不足返回 false，不写流水 */
    @Transactional
    public boolean pay(Long userId, BigDecimal amount, String bizNo, String remark) {
        getOrCreate(userId);
        // 扣款用条件更新：只有 balance >= 金额才更新成功，并发下不会扣成负数
        int rows = walletMapper.update(null, Wrappers.<Wallet>lambdaUpdate()
                .setSql("balance = balance - " + amount.toPlainString())
                .eq(Wallet::getUserId, userId)
                .ge(Wallet::getBalance, amount));
        if (rows == 0) return false;
        // 每次余额变动都写一条流水，并记下变动后的余额（balance_after），可回溯
        writeLedger(userId, amount.negate(), "PAY", bizNo, remark);
        return true;
    }

    /** 退款入账：加余额并记一条 REFUND 流水 */
    @Transactional
    public Wallet refund(Long userId, BigDecimal amount, String bizNo, String remark) {
        return change(userId, amount, "REFUND", bizNo, remark);
    }

    private Wallet change(Long userId, BigDecimal amount, String type, String bizNo, String remark) {
        getOrCreate(userId);
        walletMapper.update(null, Wrappers.<Wallet>lambdaUpdate()
                .setSql("balance = balance + " + amount.toPlainString())
                .eq(Wallet::getUserId, userId));
        writeLedger(userId, amount, type, bizNo, remark);
        return getOrCreate(userId);
    }

    private void writeLedger(Long userId, BigDecimal amount, String type, String bizNo, String remark) {
        Wallet w = walletMapper.selectOne(Wrappers.<Wallet>lambdaQuery().eq(Wallet::getUserId, userId));
        WalletLedger l = new WalletLedger();
        l.setUserId(userId);
        l.setChangeType(type);
        l.setAmount(amount);
        l.setBalanceAfter(w.getBalance());
        l.setBizNo(bizNo);
        l.setRemark(remark);
        ledgerMapper.insert(l);
    }
}