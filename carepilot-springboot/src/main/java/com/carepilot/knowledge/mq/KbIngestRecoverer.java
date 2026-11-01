package com.carepilot.knowledge.mq;

import com.carepilot.knowledge.entity.KbDocument;
import com.carepilot.knowledge.mapper.KbDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 消息重试耗尽后的回调：把失败原因写库，避免消息无声消失。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KbIngestRecoverer implements MessageRecoverer {

    private final KbDocumentMapper documentMapper;

    @Override
    public void recover(Message message, Throwable cause) {
        Long documentId = extractDocumentId(message);
        if (documentId != null) {
            KbDocument patch = new KbDocument();
            patch.setId(documentId);
            patch.setStatus("FAILED");
            patch.setParseError(trim(rootMessage(cause)));
            documentMapper.updateById(patch);
            log.error("kb ingest exhausted retries, doc={} -> DLQ", documentId);
        }
        throw new AmqpRejectAndDontRequeueException("kb ingest failed after retries", cause);
    }

    private Long extractDocumentId(Message message) {
        try {
            var converter = new org.springframework.amqp.support.converter.Jackson2JsonMessageConverter();
            Map<?, ?> payload = (Map<?, ?>) converter.fromMessage(message);
            Object id = payload == null ? null : payload.get("documentId");
            return id == null ? null : Long.valueOf(String.valueOf(id));
        } catch (Exception e) {
            log.warn("cannot parse dead-letter message: {}", e.getMessage());
            return null;
        }
    }

    private String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    private String trim(String s) {
        if (s == null) return null;
        return s.length() > 480 ? s.substring(0, 480) : s;
    }
}