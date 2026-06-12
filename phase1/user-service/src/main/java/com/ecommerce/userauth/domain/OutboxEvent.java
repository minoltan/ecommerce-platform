package com.ecommerce.userauth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Write-side of the transactional outbox pattern (V1__init.sql {@code user_auth_outbox}).
 * Persisted in the same transaction as the aggregate change it describes; a separate
 * relay/publisher (DEV subtask 86exxgxng) reads unpublished rows and emits them to Kafka.
 */
@Entity
@Table(name = "user_auth_outbox")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "aggregate_id", columnDefinition = "CHAR(36)", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "JSON")
    private String payload;

    @Column(name = "correlation_id", columnDefinition = "CHAR(36)", nullable = false)
    private UUID correlationId;

    @Column(name = "published", nullable = false)
    private boolean published;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
        // JPA
    }

    public OutboxEvent(UUID aggregateId, String eventType, String payload, UUID correlationId) {
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.correlationId = correlationId;
        this.published = false;
    }

    public Long getId() {
        return id;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public boolean isPublished() {
        return published;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    /** Marks this event as published by the {@link com.ecommerce.userauth.outbox.OutboxRelay}. */
    public void markPublished(Instant now) {
        this.published = true;
        this.publishedAt = now;
    }
}
