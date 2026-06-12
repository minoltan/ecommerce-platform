package com.ecommerce.userauth.repository;

import com.ecommerce.userauth.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBlacklistRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TokenBlacklistRepository tokenBlacklistRepository;

    @Test
    void blacklistedTokenIsReportedAsBlacklisted() {
        String jti = UUID.randomUUID().toString();

        tokenBlacklistRepository.blacklist(jti, Duration.ofMinutes(5));

        assertThat(tokenBlacklistRepository.isBlacklisted(jti)).isTrue();
    }

    @Test
    void unknownTokenIsNotBlacklisted() {
        assertThat(tokenBlacklistRepository.isBlacklisted(UUID.randomUUID().toString())).isFalse();
    }

    @Test
    void zeroOrNegativeTtlIsNoOp() {
        String jti = UUID.randomUUID().toString();

        tokenBlacklistRepository.blacklist(jti, Duration.ZERO);
        tokenBlacklistRepository.blacklist(jti, Duration.ofSeconds(-1));

        assertThat(tokenBlacklistRepository.isBlacklisted(jti)).isFalse();
    }
}
