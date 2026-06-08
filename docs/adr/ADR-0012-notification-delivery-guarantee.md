# ADR-0012: Notification Delivery Guarantee — At-Least-Once with Idempotent Consumers

**Status:** Accepted  
**Date:** 2026-06-08  
**Phase:** ARCH  
**Bounded contexts affected:** Notification  

---

## Context

The Notification service consumes events from multiple Kafka topics (`order.*`, `payment.*`,
`user-auth.*`) and delivers notifications via email, SMS, and push channels. It is a
pure consumer — it publishes no events upstream.

Two failure modes must be handled:

1. **Duplicate delivery** — Kafka at-least-once means the same event may arrive multiple
   times. Sending a "Your order is confirmed" email twice is a poor customer experience
   and undermines trust.
2. **Lost notification** — A notification that is never sent causes the customer to
   miss a critical update (e.g., payment failure, shipment tracking).

The system must choose where on the spectrum between exactly-once and at-least-once it
sits, and how to handle duplicates at the application layer.

---

## Decision

**At-least-once delivery with idempotent consumers.** Notifications are guaranteed to
be sent at least once. Deduplication at the consumer prevents sending the same
notification twice for the same triggering event.

### Deduplication mechanism

```sql
CREATE TABLE notifications (
    id                CHAR(36)     NOT NULL PRIMARY KEY,
    user_id           CHAR(36)     NOT NULL,
    source_event_id   VARCHAR(255) NOT NULL,   -- Kafka event's eventId field
    channel           ENUM('EMAIL','SMS','PUSH') NOT NULL,
    status            ENUM('PENDING','SENT','FAILED','SKIPPED') NOT NULL,
    UNIQUE KEY uq_notif_dedup (user_id, source_event_id, channel)
);
```

Before sending any notification:
1. Attempt `INSERT INTO notifications (id, user_id, source_event_id, channel, status) VALUES (?, ?, ?, ?, 'PENDING')`
2. If `DUPLICATE KEY` (UNIQUE constraint violation) → skip; notification already sent or in progress
3. If INSERT succeeds → send via channel (email/SMS/push provider) → UPDATE status = 'SENT' | 'FAILED'

The `source_event_id` is the `eventId` field from the Kafka event payload (a UUID set
by the publishing service at event creation time and preserved through outbox relay).

### Retry policy

| Attempt | Delay | Max attempts |
|---|---|---|
| 1 | immediate | — |
| 2 | 30 seconds | — |
| 3 | 5 minutes | — |
| 4 | 30 minutes | final |

After 4 failures: status = `FAILED`; event published to `notification.dlq` Kafka topic
for manual inspection.

### DLQ monitoring

`notification.dlq` consumer lag is monitored via Prometheus alert:
`kafka_consumer_lag{topic="notification.dlq"} > 0` → PagerDuty alert.

Ops team reviews DLQ messages; common causes: template misconfiguration, third-party
provider outage, invalid user contact data.

---

## Consequences

### Positive

- **No duplicate emails/SMS.** The UNIQUE constraint on `(user_id, source_event_id, channel)`
  is a DB-enforced guarantee; even if the Kafka consumer is restarted mid-processing,
  the second attempt hits a DUPLICATE KEY and skips.
- **Audit trail.** Every notification attempt is recorded in the `notifications` table,
  queryable for support ("did the customer receive their shipment email?").
- **Decoupled failure handling.** A failed SMS does not block the email for the same
  event. Channels are processed independently; DLQ captures per-channel failures.
- **Provider-agnostic.** The deduplication layer is independent of which email/SMS
  provider is used. Swapping SendGrid for SES requires only adapter changes, not schema
  changes.

### Negative

- **Not exactly-once.** A notification that succeeds at the provider level but fails
  before the DB `UPDATE status = 'SENT'` will be retried and hit the DUPLICATE KEY on
  INSERT — and correctly skipped. However, the status row remains `PENDING` indefinitely.
  Mitigation: a sweep job marks rows `PENDING` for > 10 minutes as `FAILED` and re-queues.
- **`source_event_id` must be stable across retries.** Kafka event producers must set a
  stable `eventId` (UUID v4 at event creation time, persisted in the outbox table).
  If the eventId is regenerated on outbox replay, deduplication breaks. This is an
  interface contract enforced at the publishing services.
- **UNIQUE constraint is per-channel.** If a user should receive both email and SMS for
  the same event, both rows are created (different `channel` value). This is intentional —
  a failed SMS should not block the email.
- **DLQ requires operational process.** DLQ messages are not automatically retried
  indefinitely; an ops process must review and re-queue or discard them. This is a
  deliberate choice — unbounded retries for external API calls can amplify failure cascades.

---

## Alternatives Rejected

### Exactly-once Kafka semantics (EOS)

Kafka transactions + idempotent producers guarantee each message is delivered exactly
once to the consumer. Eliminates the need for application-layer deduplication.

Rejected because:
- EOS only covers the Kafka→consumer delivery; it does not prevent duplicate provider
  API calls if the consumer crashes between Kafka commit and provider call.
- Application-layer deduplication is required regardless to handle provider retries.
- EOS adds significant configuration overhead (`isolation.level=read_committed`,
  transactional producers) for marginal benefit given the application-layer check is
  required anyway.

### Fire-and-forget (no deduplication)

Send on every Kafka delivery without deduplication. Simpler code, no UNIQUE constraint.

Rejected — duplicate order confirmation emails are a trust-damaging customer experience.
The UNIQUE constraint cost (one DB INSERT per notification) is negligible.

### Idempotency via Redis only (no DB persistence)

Cache `idem:{userId}:{eventId}:{channel}` in Redis with 24-hour TTL. Skip if cache hit.

Faster than DB INSERT but:
- Redis eviction or restart drops the cache; the same notification can be sent again.
- No permanent audit trail of sent notifications.
- Redis TTL of 24 hours may not cover all retry windows (DLQ events can be retried
  after 24 hours by ops).

Redis is used as a fast-path first-level check before the DB INSERT — same pattern as
payment idempotency (ADR-0009). The DB UNIQUE constraint is the durable backstop.
