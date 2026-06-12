package com.ecommerce.userauth.outbox;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Event envelope written to {@code user_auth_outbox.payload} and published to Kafka unchanged
 * (ADR-0002 §6 — {@code eventId}, {@code eventType}, {@code occurredAt}, {@code correlationId},
 * {@code schemaVersion} alongside the event-specific {@code data}).
 */
public record OutboxEventEnvelope(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID correlationId,
        int schemaVersion,
        Map<String, Object> data) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static OutboxEventEnvelope of(String eventType, UUID correlationId, Map<String, Object> data) {
        return new OutboxEventEnvelope(UUID.randomUUID(), eventType, Instant.now(), correlationId, CURRENT_SCHEMA_VERSION, data);
    }
}
