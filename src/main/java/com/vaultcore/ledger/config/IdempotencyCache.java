package com.vaultcore.ledger.config;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class IdempotencyCache {

    private static final long TTL_HOURS = 24;
    private static final String KEY_PREFIX = "idempotency:";

    private final StringRedisTemplate redisTemplate;

    public boolean isDuplicate(String idempotencyKey) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + idempotencyKey));
    }

    public Optional<UUID> getTransactionId(String idempotencyKey) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + idempotencyKey);
        return value != null ? Optional.of(UUID.fromString(value)) : Optional.empty();
    }

    public void record(String idempotencyKey, UUID transactionId) {
        redisTemplate.opsForValue().set(
                KEY_PREFIX + idempotencyKey,
                transactionId.toString(),
                TTL_HOURS,
                TimeUnit.HOURS
        );
    }

    public void remove(String idempotencyKey) {
        redisTemplate.delete(KEY_PREFIX + idempotencyKey);
    }
}
