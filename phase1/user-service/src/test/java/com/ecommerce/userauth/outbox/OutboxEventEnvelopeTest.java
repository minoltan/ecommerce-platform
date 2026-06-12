package com.ecommerce.userauth.outbox;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventEnvelopeTest {

    @Test
    void ofPopulatesAGeneratedEventIdTimestampAndCurrentSchemaVersion() {
        UUID correlationId = UUID.randomUUID();
        Map<String, Object> data = Map.of("userId", "abc-123");

        OutboxEventEnvelope envelope = OutboxEventEnvelope.of("UserRegistered", correlationId, data);

        assertThat(envelope.eventId()).isNotNull();
        assertThat(envelope.eventType()).isEqualTo("UserRegistered");
        assertThat(envelope.occurredAt()).isNotNull();
        assertThat(envelope.correlationId()).isEqualTo(correlationId);
        assertThat(envelope.schemaVersion()).isEqualTo(OutboxEventEnvelope.CURRENT_SCHEMA_VERSION);
        assertThat(envelope.data()).isEqualTo(data);
    }

    @Test
    void eachInvocationGeneratesAUniqueEventId() {
        OutboxEventEnvelope first = OutboxEventEnvelope.of("UserRegistered", UUID.randomUUID(), Map.of());
        OutboxEventEnvelope second = OutboxEventEnvelope.of("UserRegistered", UUID.randomUUID(), Map.of());

        assertThat(first.eventId()).isNotEqualTo(second.eventId());
    }
}
