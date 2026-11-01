package com.carepilot.knowledge.service;

import com.carepilot.knowledge.RagProperties;
import com.carepilot.knowledge.embed.EmbeddingService;
import com.carepilot.knowledge.entity.KbChunk;
import com.carepilot.knowledge.entity.KbDocument;
import com.carepilot.knowledge.keyword.ElasticKeywordIndex;
import com.carepilot.knowledge.mapper.KbChunkMapper;
import com.carepilot.knowledge.mapper.KbDocumentMapper;
import com.carepilot.knowledge.rerank.RerankService;
import com.carepilot.knowledge.rewrite.QueryRewriter;
import com.carepilot.knowledge.vector.MilvusVectorStore;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 检索流水线：向量召回 + 关键词召回 → RRF 融合 → 精排 → 阈值过滤。
 * 精排不可用时退化为 RRF 排序，并按最高分归一化保证阈值语义一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private final EmbeddingService embeddingService;
    private final MilvusVectorStore vectorStore;
    private final ElasticKeywordIndex keywordIndex;
    private final KbChunkMapper chunkMapper;
    private final KbDocumentMapper documentMapper;
    private final RerankService rerankService;
    private final QueryRewriter queryRewriter;
    private final RagProperties props;
    private final MeterRegistry meterRegistry;

    public record Citation(long chunkId, long docId, String docTitle, String titlePath,
                           String snippet, String parentText, double score) {}

    public record Result(List<Citation> citations, String context, boolean weak, String debug) {

        public static Result empty(String debug) {
            return new Result(List.of(), "", true, debug);
        }
    }

    private record Item(KbChunk chunk, double score) {}

    /** 检索并组装引用与上下文；无可靠命中时 weak=true 表示应拒答 */
    public Result retrieve(String question, String category) {
        Timer.Sample sample = Timer.start(meterRegistry);
        long start = System.currentTimeMillis();
        try {
            return doRetrieve(question, category, start);
        } finally {
            sample.stop(meterRegistry.timer("carepilot.rag.retrieve"));
        }
    }

    private Result doRetrieve(String question, String category, long start) {
        if (question == null || question.isBlank()) {
            return Result.empty("问题为空");
        }

        String query = question;
        String rewriteNote = "off";
        if (props.isQueryRewrite()) {
            try {
                String rw = queryRewriter.rewrite(question);
                if (rw != null && !rw.isBlank()) {
                    query = rw;
                    rewriteNote = "on";
                }
            } catch (Exception e) {
                rewriteNote = "failed";
                log.warn("query rewrite failed: {}", e.getMessage());
            }
        }

        List<MilvusVectorStore.Hit> vecHits = List.of();
        try {
            float[] vector = embeddingService.embed(List.of(query)).get(0);
            vecHits = vectorStore.search(vector, props.getRecallTopK(), category);
        } catch (Exception e) {
            log.warn("vector recall failed: {}", e.getMessage());
        }

        List<ElasticKeywordIndex.Hit> kwHits = List.of();
        try {
            kwHits = keywordIndex.search(query, props.getRecallTopK(), category);
        } catch (Exception e) {
            log.warn("keyword recall failed: {}", e.getMessage());
        }

        if (vecHits.isEmpty() && kwHits.isEmpty()) {
            return Result.empty("向量与关键词都没有召回");
        }

        Map<Long, Double> fused = new LinkedHashMap<>();
        for (int i = 0; i < vecHits.size(); i++) {
            fused.merge(vecHits.get(i).chunkId(), 1.0 / (props.getRrfK() + i + 1), Double::sum);
        }
        for (int i = 0; i < kwHits.size(); i++) {
            fused.merge(kwHits.get(i).chunkId(), 1.0 / (props.getRrfK() + i + 1), Double::sum);
        }

        List<Long> topIds = fused.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(Math.max(1, props.getRerank().getTopN()))
                .map(Map.Entry::getKey)
                .toList();

        List<KbChunk> chunks = chunkMapper.selectBatchIds(topIds);
        Map<Long, KbChunk> byId = chunks.stream().collect(Collectors.toMap(KbChunk::getId, c -> c, (a, b) -> a));
        List<KbChunk> ordered = topIds.stream().map(byId::get).filter(Objects::nonNull).toList();
        if (ordered.isEmpty()) {
            return Result.empty("召回的知识块已被删除");
        }

        List<Item> items = new ArrayList<>();
        List<RerankService.Scored> scored = List.of();
        try {
            // 精排：把候选交给 rerank 模型重新打分，排序更准；失败会退回 RRF
            scored = rerankService.rerank(question, ordered.stream().map(KbChunk::getContent).toList());
        } catch (Exception e) {
            log.warn("rerank failed: {}", e.getMessage());
        }

        if (!scored.isEmpty()) {
            for (RerankService.Scored s : scored) {
                if (s.index() >= 0 && s.index() < ordered.size()) {
                    items.add(new Item(ordered.get(s.index()), s.score()));
                }
            }
            items.sort(Comparator.comparingDouble(Item::score).reversed());
        } else {
            // 没有精排时把 RRF 分数按最高分归一化到 0~1，这样拒答阈值仍然有意义
            double max = fused.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
            for (KbChunk c : ordered) {
                items.add(new Item(c, fused.getOrDefault(c.getId(), 0.0) / (max <= 0 ? 1.0 : max)));
            }
        }

        double topScore = items.isEmpty() ? 0.0 : items.get(0).score();

        List<Item> kept = items.stream()
                .filter(i -> i.score() >= props.getScoreThreshold())
                .limit(props.getFinalTopK())
                .toList();

        if (kept.isEmpty()) {
            return Result.empty(String.format("最高分 %.3f 低于阈值 %.2f（向量=%d 关键词=%d 融合=%d 精排=%d 改写=%s）",
                    topScore, props.getScoreThreshold(), vecHits.size(), kwHits.size(), fused.size(), scored.size(), rewriteNote));
        }

        Set<Long> docIds = kept.stream().map(i -> i.chunk().getDocumentId()).collect(Collectors.toSet());
        Map<Long, KbDocument> docs = documentMapper.selectBatchIds(docIds).stream()
                .collect(Collectors.toMap(KbDocument::getId, d -> d, (a, b) -> a));

        List<Citation> citations = new ArrayList<>(kept.size());
        for (Item item : kept) {
            KbChunk c = item.chunk();
            KbDocument d = docs.get(c.getDocumentId());
            String parent = c.getParentContent() == null || c.getParentContent().isBlank()
                    ? c.getContent() : c.getParentContent();
            citations.add(new Citation(
                    c.getId(),
                    c.getDocumentId(),
                    d == null ? "未知文档" : d.getTitle(),
                    c.getTitlePath(),
                    snippet(c.getContent()),
                    parent,
                    item.score()));
        }

        StringBuilder context = new StringBuilder();
        for (int i = 0; i < citations.size(); i++) {
            Citation c = citations.get(i);
            context.append("【").append(i + 1).append("】").append(c.docTitle());
            if (c.titlePath() != null && !c.titlePath().isBlank()) {
                context.append(" / ").append(c.titlePath());
            }
            context.append("\n").append(c.parentText()).append("\n\n");
        }

        String debug = String.format("向量=%d 关键词=%d 融合=%d 精排=%d 阈值=%.2f 最高分=%.3f 改写=%s 耗时=%dms",
                vecHits.size(), kwHits.size(), fused.size(), scored.size(),
                props.getScoreThreshold(), topScore, rewriteNote,
                System.currentTimeMillis() - start);

        log.info("retrieve done: {} citations, topScore={} ({}ms)", citations.size(), topScore,
                System.currentTimeMillis() - start);
        return new Result(citations, context.toString(), false, debug);
    }

    private String snippet(String s) {
        if (s == null) return "";
        String t = s.replace("\n", " ").trim();
        return t.length() <= 200 ? t : t.substring(0, 200) + "…";
    }
}
