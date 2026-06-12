package com.ecommerce.userauth.domain;

import org.springframework.security.crypto.bcrypt.BCrypt;

/**
 * Value object per docs/lld/user-auth-lld.md §3.3 — bcrypt, cost &gt;= 12. Exposes only
 * {@link #matches(String)}; the hash itself is never serialised back to a client.
 */
public record PasswordHash(String hash) {

    private static final int BCRYPT_COST = 12;
    private static final int MIN_RAW_LENGTH = 8;

    public static PasswordHash hash(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_RAW_LENGTH) {
            throw new IllegalArgumentException(
                    "Password must be at least " + MIN_RAW_LENGTH + " characters");
        }
        return new PasswordHash(BCrypt.hashpw(rawPassword, BCrypt.gensalt(BCRYPT_COST)));
    }

    public boolean matches(String rawPassword) {
        return rawPassword != null && BCrypt.checkpw(rawPassword, hash);
    }
}
