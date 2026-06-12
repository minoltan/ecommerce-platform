package com.ecommerce.userauth.repository;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

/**
 * Redis-backed refresh token storage per LLD §6.1 (canonical, supersedes ADR-0011's DB table).
 *
 * <p>Each session is stored under {@code refresh:{userId}:{tokenId}} with a 7-day TTL, holding
 * the SHA-256 hash of the opaque secret half of the token. The token returned to the client is
 * {@code "{tokenId}.{secret}"}; only the hash of {@code secret} is persisted.
 */
@Repository
public class RefreshTokenRepository {

    private static final Duration TTL = Duration.ofDays(7);
    private static final String SEPARATOR = ".";

    private final StringRedisTemplate redis;

    public RefreshTokenRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Issues a new refresh token for the given user, storing its hash with a 7-day TTL. */
    public IssuedRefreshToken issue(UUID userId) {
        String tokenId = UUID.randomUUID().toString();
        String secret = UUID.randomUUID().toString();
        redis.opsForValue().set(key(userId, tokenId), hash(secret), TTL);
        return new IssuedRefreshToken(tokenId + SEPARATOR + secret, tokenId);
    }

    /**
     * Validates a presented refresh token for the given user against the stored hash.
     * Does not consume/rotate the token.
     */
    public boolean validate(UUID userId, String presentedToken) {
        return tokenId(presentedToken)
                .map(parsed -> {
                    String stored = redis.opsForValue().get(key(userId, parsed.tokenId()));
                    return stored != null && stored.equals(hash(parsed.secret()));
                })
                .orElse(false);
    }

    /**
     * Rotates a refresh token: revokes the presented token and issues a new one, atomically
     * from the caller's perspective (LLD §6.2 refresh-rotation). Returns empty if the presented
     * token is invalid or unknown, in which case the caller should treat this as a possible
     * token-replay and may choose to revoke all sessions for the user.
     */
    public java.util.Optional<IssuedRefreshToken> rotate(UUID userId, String presentedToken) {
        return tokenId(presentedToken).flatMap(parsed -> {
            String key = key(userId, parsed.tokenId());
            String stored = redis.opsForValue().get(key);
            if (stored == null || !stored.equals(hash(parsed.secret()))) {
                return java.util.Optional.empty();
            }
            redis.delete(key);
            return java.util.Optional.of(issue(userId));
        });
    }

    /** Revokes a single session by tokenId (LLD §6.2 logout-single). */
    public void revoke(UUID userId, String tokenId) {
        redis.delete(key(userId, tokenId));
    }

    /** Revokes all sessions for a user (LLD §6.2 logout-all / password-change). */
    public void revokeAll(UUID userId) {
        Set<String> keys = redis.keys("refresh:" + userId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private static String key(UUID userId, String tokenId) {
        return "refresh:" + userId + ":" + tokenId;
    }

    private record ParsedToken(String tokenId, String secret) {
    }

    private static java.util.Optional<ParsedToken> tokenId(String presentedToken) {
        int separatorIndex = presentedToken.indexOf(SEPARATOR);
        if (separatorIndex <= 0 || separatorIndex == presentedToken.length() - 1) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new ParsedToken(
                presentedToken.substring(0, separatorIndex),
                presentedToken.substring(separatorIndex + 1)));
    }

    private static String hash(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
