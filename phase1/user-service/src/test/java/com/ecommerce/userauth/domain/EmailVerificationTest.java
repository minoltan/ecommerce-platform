package com.ecommerce.userauth.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailVerificationTest {

    @Test
    void issueGeneratesATokenExpiringAfterTtl() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        EmailVerification verification = EmailVerification.issue(userId, now, Duration.ofHours(24));

        assertThat(verification.getUserId()).isEqualTo(userId);
        assertThat(verification.getToken()).isNotBlank();
        assertThat(verification.getExpiresAt()).isEqualTo(now.plus(Duration.ofHours(24)));
    }

    @Test
    void consumeSucceedsBeforeExpiry() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        EmailVerification verification = EmailVerification.issue(UUID.randomUUID(), now, Duration.ofHours(24));

        verification.consume(now.plusSeconds(60));
    }

    @Test
    void consumeThrowsWhenTokenAlreadyUsed() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        EmailVerification verification = EmailVerification.issue(UUID.randomUUID(), now, Duration.ofHours(24));
        verification.consume(now.plusSeconds(60));

        assertThatThrownBy(() -> verification.consume(now.plusSeconds(120)))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    void consumeThrowsWhenTokenHasExpired() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        EmailVerification verification = EmailVerification.issue(UUID.randomUUID(), now, Duration.ofHours(24));

        assertThatThrownBy(() -> verification.consume(now.plus(Duration.ofHours(25))))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("expired");
    }
}
