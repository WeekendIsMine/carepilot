package com.carepilot.knowledge.service;

import com.carepilot.knowledge.config.KbRabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 解析任务消费者。消息里只带 documentId，具体解析交给 KbIngestService。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KbIngestConsumer {

    private final KbIngestService ingestService;

    /**
     * 收到消息就解析；异常抛出交给重试/死信机制，由补偿任务记录失败原因。
     */
    @RabbitListener(queues = "${carepilot.rag.mq.queue:carepilot.kb.ingest.queue}",
            containerFactory = com.carepilot.knowledge.config.KbRabbitConfig.LISTENER_FACTORY)
    public void onMessage(Map<String, Object> payload) {
        Object id = payload == null ? null : payload.get("documentId");
        if (id == null) return;
        long documentId = Long.parseLong(String.valueOf(id));
        String traceId = "mq-" + documentId;
        org.slf4j.MDC.put(com.carepilot.common.trace.TraceIdFilter.MDC_KEY, traceId);
        try {
            log.info("mq received ingest task doc={}", documentId);
            ingestService.ingest(documentId);
        } finally {
            org.slf4j.MDC.remove(com.carepilot.common.trace.TraceIdFilter.MDC_KEY);
        }
    }
}