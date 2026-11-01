package com.carepilot.common.idempotency;

import com.carepilot.common.api.ResultCode;
import com.carepilot.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 幂等控制：同一个幂等键在有效期内只放行一次，防止重复提交（下单、支付这类写操作）。
 * 用 Redis SETNX 实现，第一次写入成功才算「抢到」。
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String IN_PROGRESS = "__IN_PROGRESS__";

    private final StringRedisTemplate redis;

    private String key(String scope, String idempotencyKey) {
        return "carepilot:idem:" + scope + ":" + idempotencyKey;
    }

    public Optional<String> tryAcquire(String scope, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        if (idempotencyKey.length() > 128) {
            throw new BizException(ResultCode.BAD_REQUEST, "Idempotency-Key 过长");
        }
        Boolean acquired = redis.opsForValue().setIfAbsent(key(scope, idempotencyKey), IN_PROGRESS, TTL);
        if (Boolean.TRUE.equals(acquired)) {
            return Optional.empty();
        }
        String existing = redis.opsForValue().get(key(scope, idempotencyKey));
        if (existing == null) {

            redis.opsForValue().set(key(scope, idempotencyKey), IN_PROGRESS, TTL);
            return Optional.empty();
        }
        if (IN_PROGRESS.equals(existing)) {
            throw new BizException(ResultCode.CONFLICT, "相同请求正在处理中，请勿重复提交");
        }
        return Optional.of(existing);
    }

    public void complete(String scope, String idempotencyKey, String result) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return;
        redis.opsForValue().set(key(scope, idempotencyKey), result, TTL);
    }

    public void release(String scope, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return;
        redis.delete(key(scope, idempotencyKey));
    }
}
