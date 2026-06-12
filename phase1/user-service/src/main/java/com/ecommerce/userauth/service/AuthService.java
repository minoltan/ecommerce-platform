package com.ecommerce.userauth.service;

import com.ecommerce.userauth.domain.Email;
import com.ecommerce.userauth.domain.EmailAlreadyRegisteredException;
import com.ecommerce.userauth.domain.EmailVerification;
import com.ecommerce.userauth.domain.InvalidCredentialsException;
import com.ecommerce.userauth.domain.InvalidRefreshTokenException;
import com.ecommerce.userauth.domain.InvalidTokenException;
import com.ecommerce.userauth.domain.OutboxEvent;
import com.ecommerce.userauth.domain.RateLimitExceededException;
import com.ecommerce.userauth.domain.User;
import com.ecommerce.userauth.repository.EmailVerificationRepository;
import com.ecommerce.userauth.repository.OutboxEventRepository;
import com.ecommerce.userauth.repository.RateLimitRepository;
import com.ecommerce.userauth.repository.RefreshTokenRepository;
import com.ecommerce.userauth.repository.RotatedRefreshToken;
import com.ecommerce.userauth.repository.TokenBlacklistRepository;
import com.ecommerce.userauth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Application service orchestrating the Auth API (docs/api-specs/user-service-api.yaml) on top of
 * the {@link User} aggregate, JWT issuance (ADR-0011), and the Redis session primitives from
 * LLD §6. Writes {@link OutboxEvent} rows for domain events; a separate relay publishes them to
 * Kafka (DEV subtask 86exxgxng).
 */
@Service
public class AuthService {

    private static final Duration EMAIL_VERIFICATION_TTL = Duration.ofHours(24);
    private static final Duration LOGIN_LOCKOUT_WINDOW = Duration.ofMinutes(15);
    private static final long LOGIN_MAX_ATTEMPTS = 5;
    private static final Duration REGISTER_RATE_WINDOW = Duration.ofHours(1);
    private static final long REGISTER_MAX_ATTEMPTS = 10;

    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final RateLimitRepository rateLimitRepository;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public AuthService(UserRepository userRepository,
                        EmailVerificationRepository emailVerificationRepository,
                        OutboxEventRepository outboxEventRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        TokenBlacklistRepository tokenBlacklistRepository,
                        RateLimitRepository rateLimitRepository,
                        JwtService jwtService,
                        ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenBlacklistRepository = tokenBlacklistRepository;
        this.rateLimitRepository = rateLimitRepository;
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    /**
     * {@code POST /auth/register}. Creates an {@code UNVERIFIED} user, issues an email
     * verification token, and writes a {@code UserRegistered} outbox event.
     */
    @Transactional
    public UUID register(Email email, String rawPassword, String displayName, String clientIp) {
        if (!rateLimitRepository.tryConsume(RateLimitRepository.registerKey(clientIp), REGISTER_MAX_ATTEMPTS, REGISTER_RATE_WINDOW)) {
            throw new RateLimitExceededException("Too many registration attempts from this address");
        }
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }

        User user = User.register(email, rawPassword, displayName == null ? "" : displayName);
        userRepository.save(user);

        EmailVerification verification = EmailVerification.issue(user.getId(), Instant.now(), EMAIL_VERIFICATION_TTL);
        emailVerificationRepository.save(verification);

        writeOutboxEvent(user.getId(), "UserRegistered", Map.of(
                "userId", user.getId().toString(),
                "email", email.value(),
                "verificationToken", verification.getToken()));

        return user.getId();
    }

    /**
     * {@code POST /auth/verify-email}. Activates the user and immediately issues a token pair,
     * per the {@code TokenResponse} return type in the API spec.
     */
    @Transactional
    public IssuedTokens verifyEmail(String token) {
        EmailVerification verification = emailVerificationRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Verification token not found"));
        verification.consume(Instant.now());

        User user = userRepository.findById(verification.getUserId())
                .orElseThrow(() -> new InvalidTokenException("Verification token not found"));
        user.verifyEmail();
        userRepository.save(user);

        return issueTokens(user);
    }

    /**
     * {@code POST /auth/login}. Enforces {@code rate:{userId}:login} (5 failures / 15 min, LLD §6.4)
     * and writes a {@code UserLoggedIn} outbox event on success.
     */
    @Transactional
    public IssuedTokens login(Email email, String rawPassword) {
        User user = userRepository.findByEmail(email).orElseThrow(InvalidCredentialsException::new);

        String loginRateLimitKey = RateLimitRepository.loginKey(user.getId());
        if (!rateLimitRepository.tryConsume(loginRateLimitKey, LOGIN_MAX_ATTEMPTS, LOGIN_LOCKOUT_WINDOW)) {
            throw new RateLimitExceededException("Account temporarily locked due to repeated failed logins");
        }

        user.login(rawPassword);
        rateLimitRepository.reset(loginRateLimitKey);
        userRepository.save(user);

        writeOutboxEvent(user.getId(), "UserLoggedIn", Map.of(
                "userId", user.getId().toString(),
                "email", email.value()));

        return issueTokens(user);
    }

    /**
     * {@code POST /auth/refresh}. Rotates the refresh token per LLD §6.2 and issues a fresh
     * access token. The presented token is revoked even on subsequent failures.
     */
    @Transactional
    public IssuedTokens refresh(String refreshToken) {
        RotatedRefreshToken rotated = refreshTokenRepository.rotate(refreshToken)
                .orElseThrow(InvalidRefreshTokenException::new);

        User user = userRepository.findById(rotated.userId()).orElseThrow(InvalidRefreshTokenException::new);
        IssuedAccessToken accessToken = jwtService.issueAccessToken(user);
        return new IssuedTokens(accessToken, rotated.token());
    }

    /**
     * {@code POST /auth/logout}. Revokes the refresh token's session and, if an access token was
     * presented, blacklists its {@code jti} (ADR-0011).
     */
    @Transactional
    public void logout(String refreshToken, String accessTokenJti, Duration accessTokenRemainingTtl) {
        refreshTokenRepository.revoke(refreshToken);
        if (accessTokenJti != null) {
            tokenBlacklistRepository.blacklist(accessTokenJti, accessTokenRemainingTtl);
        }
    }

    private IssuedTokens issueTokens(User user) {
        IssuedAccessToken accessToken = jwtService.issueAccessToken(user);
        var refreshToken = refreshTokenRepository.issue(user.getId());
        return new IssuedTokens(accessToken, refreshToken);
    }

    private void writeOutboxEvent(UUID aggregateId, String eventType, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            outboxEventRepository.save(new OutboxEvent(aggregateId, eventType, json, UUID.randomUUID()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload for " + eventType, e);
        }
    }
}
