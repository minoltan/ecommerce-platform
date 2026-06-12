package com.ecommerce.userauth.repository;

import com.ecommerce.userauth.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void issuedTokenValidatesAndPersistsWithSevenDayTtl() {
        UUID userId = UUID.randomUUID();

        IssuedRefreshToken issued = refreshTokenRepository.issue(userId);

        assertThat(refreshTokenRepository.validate(userId, issued.token())).isTrue();

        Long ttlSeconds = redis.getExpire("refresh:" + userId + ":" + issued.tokenId(), TimeUnit.SECONDS);
        assertThat(ttlSeconds).isPositive();
        assertThat(Duration.ofSeconds(ttlSeconds)).isLessThanOrEqualTo(Duration.ofDays(7));
    }

    @Test
    void unknownOrTamperedTokenDoesNotValidate() {
        UUID userId = UUID.randomUUID();
        refreshTokenRepository.issue(userId);

        assertThat(refreshTokenRepository.validate(userId, UUID.randomUUID() + "." + UUID.randomUUID())).isFalse();
        assertThat(refreshTokenRepository.validate(userId, "not-a-valid-token")).isFalse();
    }

    @Test
    void rotateRevokesOldTokenAndIssuesNewOne() {
        UUID userId = UUID.randomUUID();
        IssuedRefreshToken first = refreshTokenRepository.issue(userId);

        Optional<IssuedRefreshToken> rotated = refreshTokenRepository.rotate(userId, first.token());

        assertThat(rotated).isPresent();
        assertThat(refreshTokenRepository.validate(userId, first.token())).isFalse();
        assertThat(refreshTokenRepository.validate(userId, rotated.get().token())).isTrue();
    }

    @Test
    void rotatingAnAlreadyRevokedTokenFails() {
        UUID userId = UUID.randomUUID();
        IssuedRefreshToken first = refreshTokenRepository.issue(userId);
        refreshTokenRepository.revoke(userId, first.tokenId());

        assertThat(refreshTokenRepository.rotate(userId, first.token())).isEmpty();
    }

    @Test
    void revokeAllRemovesEverySessionForUser() {
        UUID userId = UUID.randomUUID();
        IssuedRefreshToken first = refreshTokenRepository.issue(userId);
        IssuedRefreshToken second = refreshTokenRepository.issue(userId);

        refreshTokenRepository.revokeAll(userId);

        assertThat(refreshTokenRepository.validate(userId, first.token())).isFalse();
        assertThat(refreshTokenRepository.validate(userId, second.token())).isFalse();
    }
}
