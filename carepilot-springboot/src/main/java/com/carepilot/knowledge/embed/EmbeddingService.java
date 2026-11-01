package com.carepilot.knowledge.embed;

import com.carepilot.knowledge.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量化：调用 DashScope embedding，按 batch 分批请求，避免单次超长。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final RagProperties props;

    public List<float[]> embed(List<String> texts) {
        List<float[]> out = new ArrayList<>(texts.size());
        int batch = Math.max(1, props.getEmbeddingBatch());
        for (int i = 0; i < texts.size(); i += batch) {
            List<String> slice = texts.subList(i, Math.min(texts.size(), i + batch));
            long start = System.currentTimeMillis();
            List<float[]> vectors = embeddingModel.embed(slice);
            log.debug("embedded {} texts in {} ms", slice.size(), System.currentTimeMillis() - start);
            out.addAll(vectors);
        }
        return out;
    }

}