package com.ecommerce.userauth.service;

import com.ecommerce.userauth.domain.User;
import com.ecommerce.userauth.domain.UserNotFoundException;
import com.ecommerce.userauth.domain.UserRole;
import com.ecommerce.userauth.domain.UserStatus;
import com.ecommerce.userauth.outbox.OutboxEventEnvelope;
import com.ecommerce.userauth.repository.OutboxEventRepository;
import com.ecommerce.userauth.repository.RefreshTokenRepository;
import com.ecommerce.userauth.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import com.ecommerce.userauth.config.CorrelationIdFilter;
import com.ecommerce.userauth.domain.OutboxEvent;

/**
 * Admin-only operations on the {@link User} aggregate, per
 * docs/lld/user-auth-lld.md §8.1 (LLD-SD-01) and UC-UA-09.
 */
@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public AdminUserService(UserRepository userRepository,
                             RefreshTokenRepository refreshTokenRepository,
                             OutboxEventRepository outboxEventRepository,
                             ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /** {@code GET /admin/users} — optionally filtered by status and/or role (uses idx_users_status). */
    @Transactional(readOnly = true)
    public Page<User> listUsers(UserStatus status, UserRole role, Pageable pageable) {
        if (status != null && role != null) {
            return userRepository.findByStatusAndRole(status, role, pageable);
        }
        if (status != null) {
            return userRepository.findByStatus(status, pageable);
        }
        if (role != null) {
            return userRepository.findByRole(role, pageable);
        }
        return userRepository.findAll(pageable);
    }

    /**
     * {@code POST /admin/users/{userId}/deactivate} (T-UA-02/03). Revokes all of the user's
     * refresh-token sessions (LLD §6.2) and writes a {@code UserDeactivated} outbox event.
     *
     * <p>Per LLD §8.1, an active access token's {@code jti} should also be blacklisted; the
     * service has no record of a user's current {@code jti} to do so out-of-band (only refresh
     * tokens are tracked), so the access token simply expires naturally (≤15 min, ADR-0011).
     * Flagged for follow-up alongside OQ-LLD-UA-06.
     */
    @Transactional
    public User deactivateUser(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));

        user.deactivate();
        userRepository.save(user);

        refreshTokenRepository.revokeAll(userId);

        writeOutboxEvent(user.getId(), "UserDeactivated", Map.of("userId", user.getId().toString()));

        return user;
    }

    private void writeOutboxEvent(UUID aggregateId, String eventType, Map<String, Object> data) {
        UUID correlationId = currentCorrelationId();
        OutboxEventEnvelope envelope = OutboxEventEnvelope.of(eventType, correlationId, data);
        try {
            String json = objectMapper.writeValueAsString(envelope);
            outboxEventRepository.save(new OutboxEvent(aggregateId, eventType, json, correlationId));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox payload for " + eventType, e);
        }
    }

    /** Reuses the request's correlation ID (set by {@link CorrelationIdFilter}) if present and a valid UUID. */
    private static UUID currentCorrelationId() {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId == null) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(correlationId);
        } catch (IllegalArgumentException e) {
            return UUID.randomUUID();
        }
    }
}
