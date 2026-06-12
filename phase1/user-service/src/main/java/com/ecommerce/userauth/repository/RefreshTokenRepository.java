package com.ecommerce.userauth.repository;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Redis-backed refresh token storage per LLD §6.1 (canonical, supersedes ADR-0011's DB table).
 *
 * <p>Each session is stored under {@code refresh:{userId}:{tokenId}} with a 7-day TTL, holding
 * the SHA-256 hash of the opaque secret half of the token. The token returned to the client is
 * {@code "{userId}.{tokenId}.{secret}"} — embedding {@code userId} lets {@code /auth/refresh}
 * and {@code /auth/logout} resolve a session from the {@code refreshToken} alone, as required by
 * the {@code RefreshTokenRequest} schema (no separate user identifier). Only the hash of
 * {@code secret} is persisted.
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
        return new IssuedRefreshToken(userId + SEPARATOR + tokenId + SEPARATOR + secret, tokenId);
    }

    /**
     * Validates a presented refresh token, returning the userId it belongs to if it is well-formed
     * and matches a live (non-expired, non-revoked) session. Does not consume/rotate the token.
     */
    public Optional<UUID> validate(String presentedToken) {
        return parse(presentedToken)
                .filter(parsed -> hash(parsed.secret()).equals(redis.opsForValue().get(key(parsed.userId(), parsed.tokenId()))))
                .map(ParsedToken::userId);
    }

    /**
     * Rotates a refresh token: revokes the presented token and issues a new one for the same user
     * (LLD §6.2 refresh-rotation). Returns empty if the presented token is invalid, unknown, or
     * already used, in which case the caller should treat this as a possible token-replay and may
     * choose to revoke all sessions for the user.
     */
    public Optional<RotatedRefreshToken> rotate(String presentedToken) {
        return parse(presentedToken).flatMap(parsed -> {
            String key = key(parsed.userId(), parsed.tokenId());
            String stored = redis.opsForValue().get(key);
            if (stored == null || !stored.equals(hash(parsed.secret()))) {
                return Optional.empty();
            }
            redis.delete(key);
            return Optional.of(new RotatedRefreshToken(parsed.userId(), issue(parsed.userId())));
        });
    }

    /** Revokes the session identified by the presented token (LLD §6.2 logout-single). No-op if malformed. */
    public void revoke(String presentedToken) {
        parse(presentedToken).ifPresent(parsed -> redis.delete(key(parsed.userId(), parsed.tokenId())));
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

    private record ParsedToken(UUID userId, String tokenId, String secret) {
    }

    private static Optional<ParsedToken> parse(String presentedToken) {
        String[] parts = presentedToken.split("\\" + SEPARATOR, 3);
        if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ParsedToken(UUID.fromString(parts[0]), parts[1], parts[2]));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
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
