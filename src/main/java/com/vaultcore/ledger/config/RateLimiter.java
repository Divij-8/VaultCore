package com.vaultcore.ledger.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RateLimiter {

    private static final String KEY_PREFIX = "ratelimit:";

    private final StringRedisTemplate redisTemplate;

    @Value("${app.ratelimit.max-requests:100}")
    private int maxRequests;

    @Value("${app.ratelimit.window-minutes:1}")
    private int windowMinutes;

    public boolean isAllowed(String userId) {
        String key = KEY_PREFIX + userId;
        Long count = redisTemplate.opsForValue().increment(key);

        if (count == null) {
            return true;
        }

        if (count == 1) {
            redisTemplate.expire(key, windowMinutes, TimeUnit.MINUTES);
        }

        return count <= maxRequests;
    }

    public int getRemainingTokens(String userId) {
        String key = KEY_PREFIX + userId;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return maxRequests;
        }
        long used = Long.parseLong(value);
        return (int) Math.max(0, maxRequests - used);
    }
}
