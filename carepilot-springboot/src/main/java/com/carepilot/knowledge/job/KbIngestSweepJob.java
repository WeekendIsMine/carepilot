package com.carepilot.knowledge.job;

import com.carepilot.knowledge.service.KbService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 知识库自愈任务：定时扫出卡在 PENDING/PARSING/EMBEDDING 超时的文档重新投递，
 * 兜住「消息丢了 / 队列重启」这类异步失败。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KbIngestSweepJob {

    private final KbService kbService;

    @Value("${carepilot.kb.stuck-timeout-minutes:10}")
    private int timeoutMinutes;

    @Scheduled(initialDelay = 120_000, fixedDelay = 300_000)
    public void sweep() {
        try {
            int n = kbService.resweepStuck(timeoutMinutes, 20);
            if (n > 0) {
                log.info("kb ingest sweep re-dispatched {} document(s)", n);
            }
        } catch (Exception e) {
            log.error("kb ingest sweep failed", e);
        }
    }
}
