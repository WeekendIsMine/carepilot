package com.carepilot.knowledge.vector;

import com.carepilot.knowledge.RagProperties;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Milvus 向量库操作：建集合、写入向量、按文档删除、相似度检索。
 * 连接失败时标记不可用，检索自动退化为只用 ES。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MilvusVectorStore {

    private static final String FIELD_ID = "id";
    private static final String FIELD_DOC = "document_id";
    private static final String FIELD_CHUNK = "chunk_no";
    private static final String FIELD_CATEGORY = "category";
    private static final String FIELD_VECTOR = "vector";
    private static final int DIM = 1024;

    private final RagProperties props;
    private final Gson gson = new Gson();

    private MilvusClientV2 client;
    private boolean available;

    public record Hit(long chunkId, double score) {}

    @PostConstruct
    void init() {
        try {
            String uri = "http://" + props.getMilvus().getHost() + ":" + props.getMilvus().getPort();
            client = new MilvusClientV2(ConnectConfig.builder().uri(uri).build());
            ensureCollection();
            available = true;
            log.info("milvus connected: {} collection={}", uri, props.getMilvus().getCollection());
        } catch (Exception e) {
            available = false;
            log.warn("milvus unavailable, vector recall disabled: {}", e.getMessage());
        }
    }

    @PreDestroy
    void close() {
        if (client != null) {
            try { client.close(); } catch (Exception ignored) { }
        }
    }

    private void ensureCollection() {
        String name = props.getMilvus().getCollection();
        Boolean exists = client.hasCollection(HasCollectionReq.builder().collectionName(name).build());
        if (Boolean.TRUE.equals(exists)) {
            client.loadCollection(LoadCollectionReq.builder().collectionName(name).build());
            return;
        }
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                .fieldSchemaList(List.of(
                        CreateCollectionReq.FieldSchema.builder().name(FIELD_ID).dataType(DataType.Int64)
                                .isPrimaryKey(true).autoID(false).build(),
                        CreateCollectionReq.FieldSchema.builder().name(FIELD_DOC).dataType(DataType.Int64).build(),
                        CreateCollectionReq.FieldSchema.builder().name(FIELD_CHUNK).dataType(DataType.Int64).build(),
                        CreateCollectionReq.FieldSchema.builder().name(FIELD_CATEGORY).dataType(DataType.VarChar)
                                .maxLength(64).build(),
                        CreateCollectionReq.FieldSchema.builder().name(FIELD_VECTOR).dataType(DataType.FloatVector)
                                .dimension(DIM).build()))
                .build();

        client.createCollection(CreateCollectionReq.builder()
                .collectionName(name)
                .collectionSchema(schema)
                .indexParams(List.of(IndexParam.builder()
                        .fieldName(FIELD_VECTOR)
                        .indexType(IndexParam.IndexType.FLAT)
                        .metricType(IndexParam.MetricType.COSINE)
                        .build()))
                .build());
        client.loadCollection(LoadCollectionReq.builder().collectionName(name).build());
        log.info("milvus collection created: {}", name);
    }

    public void insert(List<Long> chunkIds, List<Long> docIds, List<Integer> chunkNos,
                       List<String> categories, List<float[]> vectors) {
        if (!available) return;
        List<JsonObject> rows = new ArrayList<>(chunkIds.size());
        for (int i = 0; i < chunkIds.size(); i++) {
            JsonObject o = new JsonObject();
            o.addProperty(FIELD_ID, chunkIds.get(i));
            o.addProperty(FIELD_DOC, docIds.get(i));
            o.addProperty(FIELD_CHUNK, chunkNos.get(i));
            o.addProperty(FIELD_CATEGORY, categories.get(i) == null ? "general" : categories.get(i));
            o.add(FIELD_VECTOR, gson.toJsonTree(vectors.get(i)));
            rows.add(o);
        }
        client.insert(InsertReq.builder().collectionName(props.getMilvus().getCollection()).data(rows).build());
        log.debug("milvus inserted {} rows", rows.size());
    }

    public List<Hit> search(float[] vector, int topK, String categoryFilter) {
        if (!available) return List.of();
        try {
            SearchReq.SearchReqBuilder<?, ?> b = SearchReq.builder()
                    .collectionName(props.getMilvus().getCollection())
                    .annsField(FIELD_VECTOR)
                    .data(List.of(new FloatVec(vector)))
                    .topK(topK)
                    .outputFields(List.of(FIELD_DOC, FIELD_CHUNK, FIELD_CATEGORY));
            if (categoryFilter != null && !categoryFilter.isBlank()) {
                b.filter(FIELD_CATEGORY + " == \"" + categoryFilter + "\"");
            }
            SearchResp resp = client.search(b.build());
            List<Hit> hits = new ArrayList<>();
            for (List<SearchResp.SearchResult> group : resp.getSearchResults()) {
                for (SearchResp.SearchResult r : group) {
                    hits.add(new Hit(Long.parseLong(String.valueOf(r.getId())),
                            r.getScore() == null ? 0 : r.getScore()));
                }
            }
            return hits;
        } catch (Exception e) {
            log.warn("milvus search failed: {}", e.getMessage());
            return List.of();
        }
    }

    public void deleteByDocument(long documentId) {
        if (!available) return;
        try {
            client.delete(io.milvus.v2.service.vector.request.DeleteReq.builder()
                    .collectionName(props.getMilvus().getCollection())
                    .filter(FIELD_DOC + " == " + documentId)
                    .build());
            log.info("milvus vectors deleted for document {}", documentId);
        } catch (Exception e) {
            log.warn("milvus delete failed doc={}: {}", documentId, e.getMessage());
        }
    }

    public void dropCollection() {
        if (!available) return;
        try {
            client.dropCollection(io.milvus.v2.service.collection.request.DropCollectionReq.builder()
                    .collectionName(props.getMilvus().getCollection()).build());
        } catch (Exception e) {
            log.warn("drop collection failed: {}", e.getMessage());
        }
    }

    public long rowCount() {
        if (client == null) return -1;
        try {
            return client.getCollectionStats(
                    io.milvus.v2.service.collection.request.GetCollectionStatsReq.builder()
                            .collectionName(props.getMilvus().getCollection())
                            .build()).getNumOfEntities();
        } catch (Exception e) {
            log.warn("milvus rowCount failed: {}", e.getMessage());
            return -1;
        }
    }

    public Map<String, Object> stats() {
        return Map.of("available", available,
                "collection", props.getMilvus().getCollection(),
                "rows", rowCount());
    }
}