package com.carepilot.catalog.service;

import com.carepilot.catalog.entity.Product;
import com.carepilot.catalog.mapper.ProductMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 商品缓存：空值占位防穿透，SETNX 互斥重建防击穿，TTL 加随机抖动防雪崩。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCache {

    private static final String KEY = "carepilot:product:";
    private static final String NULL_VALUE = "__NULL__";
    private static final Duration NULL_TTL = Duration.ofSeconds(60);
    private static final int BASE_TTL = 300;
    private static final int JITTER_TTL = 120;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ProductMapper productMapper;

    /** 只读缓存，未命中返回 null */
    public Product get(Long id) {
        String key = KEY + id;
        String cached = redis.opsForValue().get(key);
        if (NULL_VALUE.equals(cached)) {
            return null;
        }
        if (cached != null) {
            return deserialize(cached);
        }

        String lockKey = key + ":lock";
        Boolean locked = redis.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(3));
        if (Boolean.TRUE.equals(locked)) {
            try {
                Product p = productMapper.selectById(id);
                writeThrough(key, p);
                return p;
            } finally {
                redis.delete(lockKey);
            }
        }

        for (int i = 0; i < 5; i++) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            String again = redis.opsForValue().get(key);
            if (NULL_VALUE.equals(again)) return null;
            if (again != null) return deserialize(again);
        }
        return productMapper.selectById(id);
    }

    /** 商品变更后删缓存 */
    public void evict(Long id) {
        redis.delete(KEY + id);
    }

    private void writeThrough(String key, Product p) {
        if (p == null) {
            redis.opsForValue().set(key, NULL_VALUE, NULL_TTL);
            return;
        }
        try {
            int ttl = BASE_TTL + ThreadLocalRandom.current().nextInt(JITTER_TTL);
            redis.opsForValue().set(key, objectMapper.writeValueAsString(p), Duration.ofSeconds(ttl));
        } catch (Exception e) {
            log.warn("cache write failed for product {}: {}", p.getId(), e.getMessage());
        }
    }

    private Product deserialize(String json) {
        try {
            return objectMapper.readValue(json, Product.class);
        } catch (Exception e) {
            log.warn("cache read failed, treat as miss: {}", e.getMessage());
            return null;
        }
    }
}