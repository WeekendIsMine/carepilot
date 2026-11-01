package com.carepilot.knowledge.mq;

import com.carepilot.knowledge.entity.KbDocument;
import com.carepilot.knowledge.entity.KbIngestFailure;
import com.carepilot.knowledge.mapper.KbDocumentMapper;
import com.carepilot.knowledge.mapper.KbIngestFailureMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 死信消费者：解析重试耗尽的消息落到这里，记录一条失败记录供后台重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KbDlqConsumer {

    private final KbIngestFailureMapper failureMapper;
    private final KbDocumentMapper documentMapper;

    @RabbitListener(queues = "${carepilot.rag.mq.dlq:carepilot.kb.ingest.dlq}")
    public void onDeadLetter(Map<String, Object> payload) {
        Object id = payload == null ? null : payload.get("documentId");
        if (id == null) {
            log.warn("dead letter without documentId: {}", payload);
            return;
        }
        long documentId = Long.parseLong(String.valueOf(id));
        KbDocument doc = documentMapper.selectById(documentId);

        KbIngestFailure f = new KbIngestFailure();
        f.setDocumentId(documentId);
        f.setDocumentTitle(doc == null ? null : doc.getTitle());
        f.setError(doc == null ? "文档已删除" : doc.getParseError());
        f.setAttempts(3);
        f.setResolved(0);
        failureMapper.insert(f);

        log.error("kb ingest dead letter recorded, doc={} title={}", documentId, f.getDocumentTitle());
    }
}
