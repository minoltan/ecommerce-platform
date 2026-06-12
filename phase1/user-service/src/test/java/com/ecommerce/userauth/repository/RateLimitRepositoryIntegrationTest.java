package com.ecommerce.userauth.repository;

import com.ecommerce.userauth.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RateLimitRepository rateLimitRepository;

    @Test
    void allowsRequestsUpToLimitThenRejects() {
        String key = RateLimitRepository.loginKey(UUID.randomUUID());

        for (int i = 0; i < 5; i++) {
            assertThat(rateLimitRepository.tryConsume(key, 5, Duration.ofMinutes(15))).isTrue();
        }

        assertThat(rateLimitRepository.tryConsume(key, 5, Duration.ofMinutes(15))).isFalse();
    }

    @Test
    void resetClearsCounter() {
        String key = RateLimitRepository.registerKey("203.0.113.5");

        rateLimitRepository.tryConsume(key, 1, Duration.ofHours(1));
        assertThat(rateLimitRepository.tryConsume(key, 1, Duration.ofHours(1))).isFalse();

        rateLimitRepository.reset(key);

        assertThat(rateLimitRepository.tryConsume(key, 1, Duration.ofHours(1))).isTrue();
    }

    @Test
    void endpointKeyIsPerUserAndPerEndpoint() {
        UUID userId = UUID.randomUUID();
        String cartKey = RateLimitRepository.endpointKey(userId, "cart");
        String ordersKey = RateLimitRepository.endpointKey(userId, "orders");

        assertThat(cartKey).isNotEqualTo(ordersKey);
        assertThat(rateLimitRepository.tryConsume(cartKey, 500, Duration.ofMinutes(1))).isTrue();
    }
}
