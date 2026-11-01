package com.carepilot.knowledge.service;

import com.carepilot.common.storage.OssStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 知识库原件上传到对象存储。没配 AK/SK 时静默降级，不影响本地解析。
 */
@Component
@RequiredArgsConstructor
public class KbS3Storage {

    private final OssStorage oss;

    /** 上传原件，返回可访问 URL；失败返回 null */
    public String upload(Path file, String key) {
        return oss.uploadFile(file, key);
    }

    /** 删除原件对象 */
    public boolean delete(String key) {
        return oss.delete(key);
    }
}
