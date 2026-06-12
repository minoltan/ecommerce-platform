package com.ecommerce.userauth.repository;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

/**
 * Redis-backed JWT access-token blacklist per ADR-0011: {@code blacklist:{jti}}, with a TTL
 * equal to the token's remaining lifetime so entries expire naturally once the token would
 * have expired anyway.
 */
@Repository
public class TokenBlacklistRepository {

    private final StringRedisTemplate redis;

    public TokenBlacklistRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Blacklists the given JWT id until {@code remainingTtl} elapses. No-op if already expired. */
    public void blacklist(String jti, Duration remainingTtl) {
        if (remainingTtl.isZero() || remainingTtl.isNegative()) {
            return;
        }
        redis.opsForValue().set(key(jti), "1", remainingTtl);
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(key(jti)));
    }

    private static String key(String jti) {
        return "blacklist:" + jti;
    }
}
