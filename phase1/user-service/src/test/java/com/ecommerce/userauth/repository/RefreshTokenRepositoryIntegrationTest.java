package com.ecommerce.userauth.repository;

import com.ecommerce.userauth.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

        assertThat(refreshTokenRepository.validate(issued.token())).contains(userId);

        Long ttlSeconds = redis.getExpire("refresh:" + userId + ":" + issued.tokenId(), TimeUnit.SECONDS);
        assertThat(ttlSeconds).isPositive();
        assertThat(Duration.ofSeconds(ttlSeconds)).isLessThanOrEqualTo(Duration.ofDays(7));
    }

    @Test
    void unknownOrTamperedTokenDoesNotValidate() {
        UUID userId = UUID.randomUUID();
        refreshTokenRepository.issue(userId);

        assertThat(refreshTokenRepository.validate(userId + "." + UUID.randomUUID() + "." + UUID.randomUUID())).isEmpty();
        assertThat(refreshTokenRepository.validate("not-a-valid-token")).isEmpty();
    }

    @Test
    void rotateRevokesOldTokenAndIssuesNewOne() {
        UUID userId = UUID.randomUUID();
        IssuedRefreshToken first = refreshTokenRepository.issue(userId);

        Optional<RotatedRefreshToken> rotated = refreshTokenRepository.rotate(first.token());

        assertThat(rotated).isPresent();
        assertThat(rotated.get().userId()).isEqualTo(userId);
        assertThat(refreshTokenRepository.validate(first.token())).isEmpty();
        assertThat(refreshTokenRepository.validate(rotated.get().token().token())).contains(userId);
    }

    @Test
    void rotatingAnAlreadyRevokedTokenFails() {
        UUID userId = UUID.randomUUID();
        IssuedRefreshToken first = refreshTokenRepository.issue(userId);
        refreshTokenRepository.revoke(first.token());

        assertThat(refreshTokenRepository.rotate(first.token())).isEmpty();
    }

    @Test
    void concurrentRotationOfTheSameTokenOnlySucceedsOnce() throws Exception {
        // Regresses the race fixed by GETDEL in RefreshTokenRepository#rotate (ADR-0015): two
        // requests rotating the same token must not both succeed and issue two new sessions.
        UUID userId = UUID.randomUUID();
        IssuedRefreshToken first = refreshTokenRepository.issue(userId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Optional<RotatedRefreshToken>>> futures = List.of(
                    executor.submit(() -> rotateAfterBarrier(first.token(), ready, start)),
                    executor.submit(() -> rotateAfterBarrier(first.token(), ready, start)));

            ready.await();
            start.countDown();

            long successCount = 0;
            for (Future<Optional<RotatedRefreshToken>> future : futures) {
                if (future.get(5, TimeUnit.SECONDS).isPresent()) {
                    successCount++;
                }
            }

            assertThat(successCount).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private Optional<RotatedRefreshToken> rotateAfterBarrier(String token, CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();
        return refreshTokenRepository.rotate(token);
    }

    @Test
    void revokeAllRemovesEverySessionForUser() {
        UUID userId = UUID.randomUUID();
        IssuedRefreshToken first = refreshTokenRepository.issue(userId);
        IssuedRefreshToken second = refreshTokenRepository.issue(userId);

        refreshTokenRepository.revokeAll(userId);

        assertThat(refreshTokenRepository.validate(first.token())).isEmpty();
        assertThat(refreshTokenRepository.validate(second.token())).isEmpty();
    }
}
