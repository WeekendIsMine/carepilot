package com.carepilot.knowledge.controller;

import com.carepilot.common.api.Result;
import com.carepilot.knowledge.entity.KbDocument;
import com.carepilot.knowledge.service.KbService;
import com.carepilot.knowledge.service.KbFailureService;
import com.carepilot.knowledge.entity.KbIngestFailure;
import com.carepilot.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import java.util.Map;

/**
 * 知识库后台接口：上传、列表、预览、删除、重新解析、失败重试、重建索引。
 */
@Tag(name = "知识库管理")
@RestController
@RequestMapping("/api/admin/kb")
@RequiredArgsConstructor
public class KbAdminController {

    private final KbService kbService;
    private final KbFailureService failureService;

    @Operation(summary = "文档列表")
    @GetMapping("/documents")
    public Result<com.carepilot.common.api.PageResult<KbDocument>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return Result.ok(com.carepilot.common.api.PageResult.of(kbService.list(page, size)));
    }

    @Operation(summary = "上传文档（异步解析）")
    @PostMapping("/documents")
    public Result<KbService.UploadResult> upload(@RequestPart("file") MultipartFile file,
                                                 @RequestParam(required = false) String title,
                                                 @RequestParam(required = false) String category) throws Exception {
        return Result.ok(kbService.upload(file, title, category, SecurityUtils.userId()));
    }

    @Operation(summary = "重新解析")
    @PostMapping("/documents/{id}/reingest")
    public Result<Void> reingest(@PathVariable Long id) {
        kbService.reingest(id);
        return Result.ok();
    }

    @Operation(summary = "预览正文（抽取后的纯文本）")
    @GetMapping(value = "/documents/{id}/text", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> text(@PathVariable Long id) {
        return ResponseEntity.ok().contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(kbService.extractText(id));
    }

    @Operation(summary = "查看原始文件")
    @GetMapping("/documents/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable Long id) {
        KbDocument doc = kbService.get(id);
        File f = kbService.resolveFile(doc);
        Resource resource = new FileSystemResource(f);
        String encoded = URLEncoder.encode(doc.getTitle() + extensionOf(doc), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encoded)
                .contentType(mediaTypeOf(doc))
                .contentLength(f.length())
                .body(resource);
    }

    private String extensionOf(KbDocument doc) {
        String name = doc.getFileName() == null ? "" : doc.getFileName();
        int i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(i) : "";
    }

    private MediaType mediaTypeOf(KbDocument doc) {
        String ext = extensionOf(doc).toLowerCase();
        return switch (ext) {
            case ".pdf" -> MediaType.APPLICATION_PDF;
            case ".md", ".markdown", ".txt", ".csv" -> new MediaType("text", "plain", StandardCharsets.UTF_8);
            case ".html", ".htm" -> MediaType.TEXT_HTML;
            case ".doc" -> MediaType.parseMediaType("application/msword");
            case ".docx" -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case ".png" -> MediaType.IMAGE_PNG;
            case ".jpg", ".jpeg" -> MediaType.IMAGE_JPEG;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    @Operation(summary = "解析失败列表（死信补偿）")
    @GetMapping("/failures")
    public Result<com.carepilot.common.api.PageResult<KbIngestFailure>> failures(
            @RequestParam(required = false) Integer resolved,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return Result.ok(com.carepilot.common.api.PageResult.of(failureService.list(resolved, page, size)));
    }

    @Operation(summary = "重试某条失败记录")
    @PostMapping("/failures/{id}/retry")
    public Result<Void> retryFailure(@PathVariable Long id) {
        failureService.retry(id);
        return Result.ok();
    }

    @Operation(summary = "删除文档")
    @DeleteMapping("/documents/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        kbService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "全量重建索引")
    @PostMapping("/reindex")
    public Result<Void> reindex() {
        kbService.reingestAll();
        return Result.ok();
    }

    @Operation(summary = "知识库统计")
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.ok(kbService.stats());
    }
}