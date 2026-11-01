package com.carepilot.knowledge;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 配置：召回数量、融合参数、阈值、切块大小、Milvus / ES / 精排地址。
 * 对应 application.yml 里的 carepilot.rag.*。
 */
@Data
@Component
@ConfigurationProperties(prefix = "carepilot.rag")
public class RagProperties {
    private int recallTopK = 8;
    private int finalTopK = 5;
    private int rrfK = 60;
    private double scoreThreshold = 0.1;
    private int chunkSize = 450;
    private int chunkOverlap = 60;
    private boolean queryRewrite = true;
    private int embeddingBatch = 10;

    private Milvus milvus = new Milvus();
    private Es es = new Es();
    private Rerank rerank = new Rerank();

    @Data
    public static class Milvus {
        private String host = "localhost";
        private int port = 19530;
        private String collection = "carepilot_kb_chunk";
    }

    @Data
    public static class Es {
        private String url = "http://localhost:9200";
        private String index = "carepilot_kb_chunk";
    }

    @Data
    public static class Rerank {
        private boolean enabled = true;
        private String url = "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";
        private String model = "qwen3.7-text-rerank";
        private int topN = 8;
        private int timeoutMs = 4000;
    }
}