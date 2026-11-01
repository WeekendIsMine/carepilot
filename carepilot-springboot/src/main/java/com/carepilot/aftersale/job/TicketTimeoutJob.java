package com.carepilot.aftersale.job;

import com.carepilot.aftersale.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务：把超过时限还没人处理的待审工单自动关闭，并还原订单状态。
 * 默认每 5 分钟扫一次，超时时间由 carepilot.aftersale.pending-timeout-minutes 控制。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketTimeoutJob {

    private final TicketService ticketService;

    @Value("${carepilot.aftersale.pending-timeout-minutes:120}")
    private int timeoutMinutes;

    @Scheduled(initialDelay = 60_000, fixedDelay = 300_000)
    public void closeStaleTickets() {
        try {
            int n = ticketService.expirePending(timeoutMinutes);
            if (n > 0) {
                log.info("ticket timeout job closed {} ticket(s)", n);
            }
        } catch (Exception e) {
            log.error("ticket timeout job failed", e);
        }
    }
}
