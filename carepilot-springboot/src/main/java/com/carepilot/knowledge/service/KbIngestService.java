package com.carepilot.knowledge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carepilot.knowledge.RagProperties;
import com.carepilot.knowledge.chunk.MarkdownChunker;
import com.carepilot.knowledge.embed.EmbeddingService;
import com.carepilot.knowledge.entity.KbChunk;
import com.carepilot.knowledge.entity.KbDocument;
import com.carepilot.knowledge.keyword.ElasticKeywordIndex;
import com.carepilot.knowledge.mapper.KbChunkMapper;
import com.carepilot.knowledge.mapper.KbDocumentMapper;
import com.carepilot.knowledge.vector.MilvusVectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档解析流水线：读原件 → 切父子块 → 向量化 → 写 MySQL / Milvus / ES。
 * 重新解析前会先把旧块、旧索引、旧向量全部清掉，保证幂等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbIngestService {

    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;
    private final MarkdownChunker chunker;
    private final EmbeddingService embeddingService;
    private final MilvusVectorStore vectorStore;
    private final ElasticKeywordIndex keywordIndex;
    private final RagProperties props;

    private final Tika tika = new Tika();

    /** 解析一篇文档：状态机 PARSING → EMBEDDING → READY/FAILED */
    public void ingest(Long documentId) {
        KbDocument doc = documentMapper.selectById(documentId);
        if (doc == null) {
            log.warn("ingest skipped, document {} not found", documentId);
            return;
        }
        long start = System.currentTimeMillis();
        try {
            // 状态机：PENDING → PARSING → EMBEDDING → READY / FAILED，前端靠它显示进度
            updateStatus(doc, "PARSING", null);
            // 读原件；文件丢失会抛明确异常，最终落 FAILED + parse_error，而不是一直卡着
            String text = readText(doc);

            updateStatus(doc, "EMBEDDING", null);
            List<MarkdownChunker.Child> children = chunker.split(text);
            if (children.isEmpty()) {
                updateStatus(doc, "FAILED", "文档解析后没有可索引的内容");
                return;
            }

            // 重新解析前先清干净三处旧数据，保证多次解析结果一致（幂等）
            chunkMapper.hardDeleteByDocument(doc.getId());
            keywordIndex.deleteByDoc(doc.getId());
            // Milvus 也要清：只删 MySQL 和 ES 会留下指向不存在 chunk 的孤儿向量，越解析越多
            vectorStore.deleteByDocument(doc.getId());

            List<String> texts = children.stream().map(MarkdownChunker.Child::text).toList();
            // 批量向量化，一次请求算多段，减少调用次数
            List<float[]> vectors = embeddingService.embed(texts);

            List<Long> ids = new ArrayList<>();
            List<Long> docIds = new ArrayList<>();
            List<Integer> nos = new ArrayList<>();
            List<String> cats = new ArrayList<>();
            List<String> contents = new ArrayList<>();

            for (int i = 0; i < children.size(); i++) {
                MarkdownChunker.Child c = children.get(i);
                KbChunk row = new KbChunk();
                row.setDocumentId(doc.getId());
                row.setChunkNo(c.no());
                row.setParentNo(c.parentNo());
                row.setTitlePath(c.titlePath());
                row.setContent(c.text());
                row.setParentContent(c.parentText());
                row.setTokenCount(c.text().length());
                row.setEmbeddingModel("qwen3.7-text-embedding-flash");
                chunkMapper.insert(row);

                ids.add(row.getId());
                docIds.add(doc.getId());
                nos.add(c.no());
                cats.add(doc.getCategory());
                contents.add(c.text());
            }

            vectorStore.insert(ids, docIds, nos, cats, vectors);

            if (documentMapper.selectById(doc.getId()) == null) {
                chunkMapper.hardDeleteByDocument(doc.getId());
                vectorStore.deleteByDocument(doc.getId());
                keywordIndex.deleteByDoc(doc.getId());
                log.info("ingest rolled back, document {} was deleted concurrently", doc.getId());
                return;
            }

            List<KbChunk> rows = chunkMapper.selectList(Wrappers.<KbChunk>lambdaQuery()
                    .eq(KbChunk::getDocumentId, doc.getId()));
            keywordIndex.indexChunks(rows, doc.getTitle(), doc.getCategory());

            KbDocument patch = new KbDocument();
            patch.setId(doc.getId());
            patch.setStatus("READY");
            patch.setChunkCount(children.size());
            documentMapper.updateById(patch);
            log.info("ingest done doc={} title={} chunks={} in {} ms",
                    doc.getId(), doc.getTitle(), children.size(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("ingest failed doc={}", documentId, e);
            updateStatus(doc, "FAILED", friendlyError(e));

            throw new IllegalStateException("文档解析失败: " + e.getMessage(), e);
        }
    }

    /** 把 Tika / IO 的英文异常翻成能看懂的原因，原始异常仍写日志 */
    private String friendlyError(Exception e) {
        String msg = String.valueOf(e.getMessage());
        if (msg.contains("源文件不存在")) return msg;
        if (msg.contains("no content") || msg.contains("文档解析后没有可索引的内容")) return "文档解析后没有可索引的内容";
        if (msg.contains("Length to read") || msg.contains("Unexpected end") || msg.contains("Truncated")) {
            return "文件内容不完整或格式不受支持，请检查文件是否损坏";
        }
        if (msg.contains("Password") || msg.contains("encrypted")) return "文档已加密，请去除密码后再上传";
        return "文档解析失败，请确认文件格式与内容是否正常";
    }

    /** 用 Tika 抽文本；缺文件或空内容会抛出明确异常 */
    public String extractText(KbDocument doc) throws Exception {
        return readText(doc);
    }

    private String readText(KbDocument doc) throws Exception {
        String name = doc.getFileName() == null ? doc.getTitle() : doc.getFileName();
        if (doc.getFileUrl() == null || doc.getFileUrl().isBlank()) {
            throw new IllegalStateException("文档没有可读取的来源");
        }
        String path = doc.getFileUrl().replaceFirst("^file:", "");
        File file = new File(path);
        if (!file.exists()) {
            throw new IllegalStateException("源文件不存在：" + path);
        }
        String lower = name.toLowerCase();
        if (lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".txt")) {
            return readPlainText(file);
        }

        return tika.parseToString(file);
    }

    private String readPlainText(File file) throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of(file.getAbsolutePath()));
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            log.warn("utf-8 decode failed for {}, fallback to GB18030", file.getName());
            return new String(bytes, Charset.forName("GB18030"));
        }
    }

    private void updateStatus(KbDocument doc, String status, String error) {
        KbDocument patch = new KbDocument();
        patch.setId(doc.getId());
        patch.setStatus(status);
        patch.setParseError(error);
        documentMapper.updateById(patch);
    }
}