package com.ecommerce.userauth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A single-use email verification token issued at registration time (V1__init.sql
 * {@code email_verifications}). Consumed by {@code POST /auth/verify-email}.
 */
@Entity
@Table(name = "email_verifications")
public class EmailVerification {

    @Id
    @Column(name = "id", columnDefinition = "CHAR(36)")
    private UUID id;

    @Column(name = "user_id", columnDefinition = "CHAR(36)", nullable = false)
    private UUID userId;

    @Column(name = "token", nullable = false, unique = true)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected EmailVerification() {
        // JPA
    }

    private EmailVerification(UUID userId, String token, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.token = token;
        this.expiresAt = expiresAt;
    }

    /** Issues a new verification token for {@code userId}, valid for {@code ttl}. */
    public static EmailVerification issue(UUID userId, Instant now, java.time.Duration ttl) {
        return new EmailVerification(userId, UUID.randomUUID().toString(), now.plus(ttl));
    }

    /** Marks this token as consumed. Throws if already used or expired. */
    public void consume(Instant now) {
        if (usedAt != null) {
            throw new InvalidTokenException("Verification token has already been used");
        }
        if (now.isAfter(expiresAt)) {
            throw new InvalidTokenException("Verification token has expired");
        }
        this.usedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getToken() {
        return token;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
