package com.ecommerce.userauth.repository;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

/**
 * Redis-backed fixed-window counters used to enforce the rate limits in LLD §6.4:
 * <ul>
 *   <li>{@code rate:{userId}:login} — 5 failed attempts within 15 minutes locks the account.</li>
 *   <li>{@code rate:{ip}:register} — 10 registrations per hour per IP.</li>
 *   <li>{@code rate:{userId}:{endpoint}} — 500 requests per minute per authenticated user.</li>
 * </ul>
 * The gateway-level {@code rate:{ip}:*} limit (100/min) is enforced outside this service.
 */
@Repository
public class RateLimitRepository {

    private final StringRedisTemplate redis;

    public RateLimitRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Increments the counter at {@code key}, starting its TTL on first increment, and reports
     * whether the resulting count is within {@code limit}.
     */
    public boolean tryConsume(String key, long limit, Duration window) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, window);
        }
        return count == null || count <= limit;
    }

    /** Clears a counter, e.g. on successful login (resets {@code rate:{userId}:login}). */
    public void reset(String key) {
        redis.delete(key);
    }

    public static String loginKey(java.util.UUID userId) {
        return "rate:" + userId + ":login";
    }

    public static String registerKey(String ip) {
        return "rate:" + ip + ":register";
    }

    public static String endpointKey(java.util.UUID userId, String endpoint) {
        return "rate:" + userId + ":" + endpoint;
    }
}
