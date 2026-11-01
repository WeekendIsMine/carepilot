package com.carepilot.knowledge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.carepilot.knowledge.entity.KbChunk;
import com.carepilot.knowledge.entity.KbDocument;
import com.carepilot.knowledge.entity.KbIngestFailure;
import com.carepilot.knowledge.mapper.KbChunkMapper;
import com.carepilot.knowledge.mapper.KbDocumentMapper;
import com.carepilot.knowledge.config.KbRabbitConfig;
import com.carepilot.knowledge.keyword.ElasticKeywordIndex;
import com.carepilot.knowledge.vector.MilvusVectorStore;
import com.carepilot.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 知识库主干：上传（按内容 SHA-256 去重）、重新解析、删除、统计、自愈扫描。
 * 解析走 MQ 异步；事务提交后才投递，避免消费者查不到刚插入的文档。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbService {

    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;
    private final KbIngestService ingestService;
    private final KbS3Storage s3Storage;
    private final MilvusVectorStore vectorStore;
    private final ElasticKeywordIndex keywordIndex;
    private final RabbitTemplate rabbitTemplate;

    @org.springframework.beans.factory.annotation.Value("${carepilot.rag.mq.exchange:carepilot.kb.exchange}")
    private String kbExchange;

    @org.springframework.beans.factory.annotation.Value("${carepilot.rag.mq.enabled:true}")
    private boolean mqEnabled;

    @Value("${carepilot.upload-dir:data/uploads}")
    private String uploadDir;

    /** 按 id 取文档 */
    public KbDocument get(Long id) {
        KbDocument doc = documentMapper.selectById(id);
        if (doc == null) throw BizException.notFound("文档不存在");
        return doc;
    }

    /** 把库里的 file_url 变成可读的 Path，找不到时给出明确原因 */
    public java.io.File resolveFile(KbDocument doc) {
        String path = doc.getFileUrl() == null ? null : doc.getFileUrl().replaceFirst("^file:", "");
        if (path == null || path.isBlank()) throw BizException.notFound("该文档没有可读取的源文件");
        java.io.File f = new java.io.File(path);
        if (!f.exists()) throw BizException.notFound("源文件已丢失：" + doc.getTitle());
        return f;
    }

    /** 抽取文档文本给预览用 */
    public String extractText(Long id) {
        KbDocument doc = get(id);
        try {
            return ingestService.extractText(doc);
        } catch (Exception e) {
            throw new BizException(com.carepilot.common.api.ResultCode.INTERNAL_ERROR, "文档内容读取失败：" + e.getMessage());
        }
    }

    /** 文档分页列表 */
    public com.baomidou.mybatisplus.core.metadata.IPage<KbDocument> list(Integer page, Integer size) {
        return documentMapper.selectPage(com.carepilot.common.api.Pages.of(page, size),
                Wrappers.<KbDocument>lambdaQuery().orderByDesc(KbDocument::getId));
    }

    /** 全部未删除文档 */
    public List<KbDocument> listAll() {
        return documentMapper.selectList(Wrappers.<KbDocument>lambdaQuery());
    }

    public record UploadResult(Long id, String title, boolean replaced) {}

    /** 能稳定抽出文本的格式；和前端上传框的 accept 保持一致 */
    private static final java.util.Set<String> ALLOWED_EXT = java.util.Set.of(
            ".md", ".markdown", ".txt", ".pdf", ".doc", ".docx",
            ".ppt", ".pptx", ".xls", ".xlsx", ".html", ".htm", ".csv", ".rtf");

    /** 上传：算哈希去重、落盘、可选传 OSS，重复上传走覆盖 */
    @Transactional
    public UploadResult upload(MultipartFile file, String title, String category, Long userId) throws Exception {
        byte[] bytes = file.getBytes();
        // 去重靠内容哈希，不靠文件名：同一份文件改名再传也会命中已有文档（走覆盖）
        String hash = sha256(bytes);

        String original = file.getOriginalFilename() == null ? "doc.txt" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')).toLowerCase() : ".txt";
        // 只接受能被 Tika 稳定解析的格式，避免用户传二进制文件后只看到一句解析失败
        if (!ALLOWED_EXT.contains(ext)) {
            throw BizException.conflict("只支持 Markdown / TXT / PDF / Word / PPT / Excel / HTML / CSV 文档，"
                    + "不支持 " + ext + " 这类文件");
        }
        String finalTitle = title == null || title.isBlank()
                ? original.replaceFirst("\\.[^.]+$", "")
                : title;

        Path dir = Path.of(uploadDir);
        Files.createDirectories(dir);

        // 落盘也用哈希命名，同一份文件在磁盘上只留一份
        Path target = dir.resolve(hash.substring(0, 12) + ext);
        Files.write(target, bytes);
        String ossUrl = s3Storage.upload(target, "kb/" + target.getFileName());

        KbDocument existing = documentMapper.selectOne(Wrappers.<KbDocument>lambdaQuery()
                .eq(KbDocument::getFileHash, hash)
                .last("limit 1"));

        Long docId;
        // 重复上传等于「覆盖」：标题/文件更新、状态重置、块数清零，重新解析
        boolean replaced = existing != null;
        if (replaced) {
            documentMapper.update(null, Wrappers.<KbDocument>lambdaUpdate()
                    .set(KbDocument::getTitle, finalTitle)
                    .set(KbDocument::getCategory, category)
                    .set(KbDocument::getFileName, original)
                    .set(KbDocument::getFileUrl, target.toAbsolutePath().toString())
                    .set(KbDocument::getOssUrl, ossUrl)
                    .set(KbDocument::getStatus, "PENDING")
                    .set(KbDocument::getChunkCount, 0)
                    .set(KbDocument::getParseError, null)
                    .eq(KbDocument::getId, existing.getId()));
            docId = existing.getId();
            log.info("kb re-upload, overwrite doc={} file={}", docId, original);
        } else {
            KbDocument doc = new KbDocument();
            doc.setTitle(finalTitle);
            doc.setCategory(category);
            doc.setSourceType("UPLOAD");
            doc.setFileName(original);
            doc.setFileHash(hash);
            doc.setFileUrl(target.toAbsolutePath().toString());
            doc.setOssUrl(ossUrl);
            doc.setStatus("PENDING");
            doc.setChunkCount(0);
            doc.setCreatedBy(userId);
            try {
                documentMapper.insert(doc);
            } catch (org.springframework.dao.DuplicateKeyException e) {

                KbDocument race = documentMapper.selectOne(Wrappers.<KbDocument>lambdaQuery()
                        .eq(KbDocument::getFileHash, hash).last("limit 1"));
                if (race == null) throw e;
                doc = race;
                replaced = true;
            }
            docId = doc.getId();
            log.info("kb uploaded doc={} file={} hash={} oss={}", docId, original, hash.substring(0, 12), ossUrl);
        }

        dispatchAfterCommit(docId);
        return new UploadResult(docId, finalTitle, replaced);
    }

    private String sha256(byte[] bytes) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte x : digest) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    /**
     * 关键点：上传方法在事务里，必须等事务提交后再发 MQ
     * 否则消费者毫秒级收到消息时这行文档还查不到，任务白白丢掉、文档永久卡 PENDING
     */
    private void dispatchAfterCommit(Long documentId) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            dispatch(documentId);
                        }
                    });
        } else {
            dispatch(documentId);
        }
    }

    /** 投递解析任务；MQ 不可用时同步解析 */
    public void dispatch(Long documentId) {
        if (!mqEnabled) {
            log.info("mq dispatch disabled, ingest synchronously: doc={}", documentId);
            ingestService.ingest(documentId);
            return;
        }
        try {
            rabbitTemplate.convertAndSend(kbExchange, KbRabbitConfig.ROUTING_KEY,
                    Map.of("documentId", documentId));
        } catch (Exception e) {
            log.warn("mq unavailable, ingest synchronously: {}", e.getMessage());
            ingestService.ingest(documentId);
        }
    }

    /** 删除：清块/索引/向量，逻辑删文档并释放哈希唯一键 */
    @Transactional
    public void delete(Long id) {
        KbDocument doc = documentMapper.selectById(id);
        if (doc == null) throw BizException.notFound("文档不存在");

        chunkMapper.hardDeleteByDocument(id);

        documentMapper.update(null, Wrappers.<KbDocument>lambdaUpdate()
                .set(KbDocument::getFileHash, null)
                .eq(KbDocument::getId, id));
        documentMapper.deleteById(id);
        vectorStore.deleteByDocument(id);
        keywordIndex.deleteByDoc(id);
        // 文档删了，它的失败补偿记录也要收尾，否则后台一直提示「解析失败 N」
        if (failureMapper != null) {
            failureMapper.update(null, Wrappers.<KbIngestFailure>lambdaUpdate()
                    .set(KbIngestFailure::getResolved, 1)
                    .eq(KbIngestFailure::getDocumentId, id));
        }
        log.info("kb document deleted: {} ({})", id, doc.getTitle());
    }

    /** 重新解析单篇 */
    public void reingest(Long id) {
        dispatch(id);
    }

    /** 自愈：把卡在 PENDING/PARSING/EMBEDDING 超时的文档重投 */
    public int resweepStuck(int timeoutMinutes, int maxBatch) {
        java.time.LocalDateTime deadline = java.time.LocalDateTime.now().minusMinutes(Math.max(1, timeoutMinutes));
        List<KbDocument> stuck = documentMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<KbDocument>lambdaQuery()
                        .in(KbDocument::getStatus, List.of("PENDING", "PARSING", "EMBEDDING"))
                        .lt(KbDocument::getUpdatedAt, deadline)
                        .orderByAsc(KbDocument::getId)
                        .last("limit " + Math.max(1, maxBatch)));
        int n = 0;
        for (KbDocument d : stuck) {
            try {

                KbDocument patch = new KbDocument();
                patch.setId(d.getId());
                patch.setStatus("PENDING");
                patch.setChunkCount(0);
                documentMapper.updateById(patch);
                n++;
                dispatch(d.getId());
                log.warn("resweep stuck document {} ({} -> PENDING)", d.getId(), d.getStatus());
            } catch (Exception e) {
                log.warn("resweep document {} failed: {}", d.getId(), e.getMessage());
            }
        }
        return n;
    }

    /** 全量重新解析 */
    public void reingestAll() {
        for (KbDocument d : listAll()) {
            dispatch(d.getId());
        }
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.carepilot.knowledge.mapper.KbIngestFailureMapper failureMapper;

    /** 文档数、块数、未处理失败数 */
    public Map<String, Object> stats() {
        long docs = documentMapper.selectCount(Wrappers.<KbDocument>lambdaQuery());
        long chunks = chunkMapper.selectCount(Wrappers.<KbChunk>lambdaQuery());
        long failed = failureMapper == null ? 0
                : failureMapper.selectCount(Wrappers.<com.carepilot.knowledge.entity.KbIngestFailure>lambdaQuery()
                        .eq(com.carepilot.knowledge.entity.KbIngestFailure::getResolved, 0));
        return Map.of("documents", docs, "chunks", chunks, "failures", failed);
    }
}