package com.carepilot.common.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

/**
 * 阿里云 OSS 上传：商品图片、知识库原件都走这里。
 * 没配 AK/SK 时降级为「只存本地」，不影响功能。
 */
@Slf4j
@Component
public class OssStorage {

    @Value("${carepilot.oss.endpoint:}")
    private String endpoint;
    @Value("${carepilot.oss.bucket:}")
    private String bucket;
    @Value("${carepilot.oss.prefix:carepilot/}")
    private String prefix;
    @Value("${carepilot.oss.access-key-id:}")
    private String accessKeyId;
    @Value("${carepilot.oss.access-key-secret:}")
    private String accessKeySecret;

    public boolean enabled() {
        return endpoint != null && !endpoint.isBlank()
                && accessKeyId != null && !accessKeyId.isBlank();
    }

    public String uploadBytes(byte[] bytes, String objectKey, String contentType) {
        if (!enabled()) return null;
        try {
            OSS client = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
            try {
                ObjectMetadata meta = new ObjectMetadata();
                meta.setContentLength(bytes.length);
                if (contentType != null) meta.setContentType(contentType);
                String key = prefix + objectKey;
                client.putObject(bucket, key, new ByteArrayInputStream(bytes), meta);
                return publicUrl(key);
            } finally {
                client.shutdown();
            }
        } catch (Exception e) {
            log.warn("oss upload failed key={}: {}", objectKey, e.getMessage());
            return null;
        }
    }

    public String uploadFile(Path file, String objectKey) {
        if (!enabled()) return null;
        try {
            OSS client = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
            try {
                String key = prefix + objectKey;
                client.putObject(bucket, key, file.toFile());
                return publicUrl(key);
            } finally {
                client.shutdown();
            }
        } catch (Exception e) {
            log.warn("oss upload failed key={}: {}", objectKey, e.getMessage());
            return null;
        }
    }

    public boolean delete(String urlOrKey) {
        if (!enabled() || urlOrKey == null || urlOrKey.isBlank()) return false;
        try {
            String key = toKey(urlOrKey);
            OSS client = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
            try {
                client.deleteObject(bucket, key);
                return true;
            } finally {
                client.shutdown();
            }
        } catch (Exception e) {
            log.warn("oss delete failed {}: {}", urlOrKey, e.getMessage());
            return false;
        }
    }

    private String publicUrl(String key) {
        return "https://" + bucket + "." + endpoint + "/" + key;
    }

    private String toKey(String urlOrKey) {
        String host = bucket + "." + endpoint + "/";
        if (urlOrKey.startsWith("http")) {
            int idx = urlOrKey.indexOf(host);
            return idx >= 0 ? urlOrKey.substring(idx + host.length()) : urlOrKey;
        }
        return urlOrKey.startsWith(prefix) ? urlOrKey : prefix + urlOrKey;
    }
}
