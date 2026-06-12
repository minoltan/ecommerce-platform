package com.ecommerce.userauth.service;

import java.time.Instant;

/**
 * A freshly-issued JWT access token along with the metadata needed to manage its lifecycle
 * (e.g. blacklisting on logout, per ADR-0011).
 */
public record IssuedAccessToken(String token, String jti, Instant issuedAt, Instant expiresAt) {

    public long expiresInSeconds() {
        return expiresAt.getEpochSecond() - issuedAt.getEpochSecond();
    }
}
