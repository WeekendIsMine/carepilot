package com.carepilot.knowledge.keyword;

import com.carepilot.knowledge.RagProperties;
import com.carepilot.knowledge.entity.KbChunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ES 关键词检索：建索引（IK 分词）、写块、按文档删除、BM25 查询。
 * 与向量检索并列为两路召回。
 */
@Slf4j
@Component
public class ElasticKeywordIndex {

    private final RestClient http;
    private final ObjectMapper om;
    private final RagProperties props;
    private volatile boolean ready;

    public ElasticKeywordIndex(@Qualifier("kbRestClient") RestClient http,
                               ObjectMapper om, RagProperties props) {
        this.http = http;
        this.om = om;
        this.props = props;
    }

    public record Hit(long chunkId, double score) {}

    private String base() {
        return props.getEs().getUrl();
    }

    private String index() {
        return props.getEs().getIndex();
    }

    public synchronized void ensureIndex() {
        if (ready) return;
        try {
            String mapping = """
                    {
                      "settings": { "number_of_shards": 1, "number_of_replicas": 0 },
                      "mappings": {
                        "properties": {
                          "chunk_id": { "type": "long" },
                          "doc_id":   { "type": "long" },
                          "title":    { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" },
                          "category": { "type": "keyword" },
                          "content":  { "type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart" }
                        }
                      }
                    }
                    """;
            http.put().uri(base() + "/" + index())
                    .contentType(MediaType.APPLICATION_JSON).body(mapping).retrieve().toBodilessEntity();
            ready = true;
            log.info("es index created: {}", index());
        } catch (Exception e) {

            String msg = String.valueOf(e.getMessage());
            if (msg.contains("resource_already_exists")) {
                ready = true;
                log.info("es index exists: {}", index());
            } else {
                log.warn("es index init failed: {}", msg);
            }
        }
    }

    public void deleteByDoc(long docId) {
        if (!ready) return;
        try {
            http.post().uri(base() + "/" + index() + "/_delete_by_query?refresh=true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("query", Map.of("term", Map.of("doc_id", docId))))
                    .retrieve().toBodilessEntity();
        } catch (Exception e) {
            log.warn("es delete by doc failed: {}", e.getMessage());
        }
    }

    public void indexChunks(List<KbChunk> chunks, String title, String category) {
        ensureIndex();
        if (!ready) return;
        for (KbChunk c : chunks) {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("chunk_id", c.getId());
            doc.put("doc_id", c.getDocumentId());
            doc.put("title", title);
            doc.put("category", category == null ? "general" : category);
            doc.put("content", c.getContent());
            try {
                http.put().uri(base() + "/" + index() + "/_doc/" + c.getId())
                        .contentType(MediaType.APPLICATION_JSON).body(doc).retrieve().toBodilessEntity();
            } catch (Exception e) {
                log.warn("es index chunk {} failed: {}", c.getId(), e.getMessage());
                return;
            }
        }
        try {
            http.post().uri(base() + "/" + index() + "/_refresh").retrieve().toBodilessEntity();
        } catch (Exception ignored) { }
    }

    public List<Hit> search(String query, int topK, String category) {
        ensureIndex();
        if (!ready) return List.of();
        try {
            Map<String, Object> must = Map.of("match", Map.of("content", Map.of("query", query, "operator", "or")));
            Map<String, Object> bool = new LinkedHashMap<>();
            bool.put("must", List.of(must));
            if (category != null && !category.isBlank()) {
                bool.put("filter", List.of(Map.of("term", Map.of("category", category))));
            }
            Map<String, Object> body = Map.of(
                    "size", topK,
                    "_source", List.of("chunk_id"),
                    "query", Map.of("bool", bool));

            String resp = http.post().uri(base() + "/" + index() + "/_search")
                    .contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(String.class);
            JsonNode root = om.readTree(resp);
            List<Hit> hits = new ArrayList<>();
            for (JsonNode h : root.path("hits").path("hits")) {
                hits.add(new Hit(h.path("_source").path("chunk_id").asLong(), h.path("_score").asDouble()));
            }
            return hits;
        } catch (Exception e) {
            log.warn("es search failed: {}", e.getMessage());
            return List.of();
        }
    }

    public boolean isReady() {
        return ready;
    }
}