package com.carepilot.knowledge.rerank;

import com.carepilot.knowledge.RagProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 精排：把召回候选交给 rerank 模型重新打分排序。
 * 不可用（没配 key / 超时 / 报错）时返回 null，由检索层退回 RRF 排序。
 */
@Slf4j
@Service
public class RerankService {

    private final RestClient http;
    private final ObjectMapper om;
    private final RagProperties props;
    private final String apiKey;

    public RerankService(@Qualifier("kbRestClient") RestClient http, ObjectMapper om, RagProperties props,
                         @Value("${carepilot.ai.api-key:}") String apiKey) {
        this.http = http;
        this.om = om;
        this.props = props;
        this.apiKey = apiKey;
    }

    public record Scored(int index, double score) {}

    public boolean available() {
        if (!props.getRerank().isEnabled() || apiKey == null || apiKey.isBlank()) return false;
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", props.getRerank().getModel());
            body.put("input", Map.of("query", "ping", "documents", List.of("ping")));
            body.put("parameters", Map.of("top_n", 1, "return_documents", false));
            http.post().uri(props.getRerank().getUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(body)
                    .retrieve().body(String.class);
            return true;
        } catch (Exception e) {
            log.warn("rerank unavailable: {}", e.getMessage());
            return false;
        }
    }

    public List<Scored> rerank(String query, List<String> documents) {
        if (!props.getRerank().isEnabled() || documents.isEmpty() || apiKey == null || apiKey.isBlank()) {
            return List.of();
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", props.getRerank().getModel());
            body.put("input", Map.of("query", query, "documents", documents));
            body.put("parameters", Map.of("top_n", props.getRerank().getTopN(), "return_documents", false));

            String resp = http.post().uri(props.getRerank().getUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(body)
                    .retrieve().body(String.class);

            JsonNode root = om.readTree(resp);
            List<Scored> out = new ArrayList<>();
            for (JsonNode r : root.path("output").path("results")) {
                out.add(new Scored(r.path("index").asInt(), r.path("relevance_score").asDouble()));
            }
            out.sort((a, b) -> Double.compare(b.score(), a.score()));
            return out;
        } catch (Exception e) {
            log.warn("rerank failed, fallback to rrf order: {}", e.getMessage());
            return List.of();
        }
    }
}