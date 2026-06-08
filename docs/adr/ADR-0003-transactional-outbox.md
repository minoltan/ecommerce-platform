# ADR-0003: Reliable Event Publishing via Transactional Outbox Pattern

**Status:** Accepted  
**Date:** 2026-06-08  
**Phase:** ARCH  
**Bounded contexts affected:** Order, Payment (mandatory); all others (recommended)  

---

## Context

Services must publish domain events to Kafka after mutating their local state. The naive
approach — write to DB, then publish to Kafka in the same request — is not atomic:

```
1. UPDATE orders SET status = 'CONFIRMED'   ← succeeds
2. kafka.send("order.confirmed", event)     ← crashes
   → event lost; consumers never notified; saga stalls
```

The converse failure (Kafka write succeeds, DB write fails) produces phantom events that
drive downstream services into an inconsistent state.

This is the **dual-write problem**. It is especially critical for the Order and Payment
services where missed or duplicated events corrupt the saga and financial ledgers.

---

## Decision

All domain event publishing uses the **Transactional Outbox Pattern**:

1. The service writes the domain event as a row into an **`*_outbox`** table in the **same
   DB transaction** as the state mutation. If the transaction rolls back, the event row is
   also rolled back — atomically.
2. A dedicated **Outbox Relay** component (a Spring `@Scheduled` poller running in the
   same JVM, or a Debezium CDC connector in later iterations) reads unpublished rows from
   the outbox table and publishes them to Kafka.
3. Once Kafka `ack` is received, the relay marks the row as `published = true` (or deletes
   it).
4. Consumers must be **idempotent** — the relay guarantees at-least-once delivery; exactly-
   once requires deduplication on the consumer side (Redis key or DB unique constraint on
   `source_event_id`).

### Outbox table schema (per service)

```sql
CREATE TABLE order_outbox (
    id             CHAR(36)     NOT NULL PRIMARY KEY,
    aggregate_id   CHAR(36)     NOT NULL,          -- orderId
    event_type     VARCHAR(100) NOT NULL,           -- e.g. 'OrderConfirmed'
    payload        JSON         NOT NULL,
    published      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    published_at   DATETIME(3),
    INDEX idx_outbox_unpublished (published, created_at)
);
```

### Relay behaviour

- Polls every **500 ms** for `published = false` rows ordered by `created_at ASC`.
- Batch size: **50 rows** per poll cycle (limits in-flight Kafka sends per cycle).
- On Kafka send failure: exponential backoff (500 ms, 1 s, 2 s, …) up to 5 retries;
  after 5 failures, the relay stops and raises a `CRITICAL` alert.
- On relay restart after crash: re-reads all `published = false` rows — safe because
  consumers deduplicate on `event_id`.
- Outbox rows with `published = true` are **purged after 7 days** by a nightly DB job
  (retention window for debugging and replay).

### Mandatory vs recommended

| Context | Outbox mandatory? | Reason |
|---|---|---|
| Order | Yes | Saga coordinator; missed event stalls the entire flow |
| Payment | Yes | Financial event; must not be lost or duplicated |
| Inventory | Yes | Stock events drive order confirmation/cancellation |
| User/Auth | Recommended | `UserRegistered` drives welcome email; loss is recoverable |
| Product Catalog | Recommended | `ProductCreated` / `PriceUpdated` drive search index |
| Cart | Not required | Cart events are ephemeral; Redis is the source of truth |
| Notification | Not required | Consumer only; does not publish upstream events |

---

## Consequences

### Positive

- **Atomic event publishing.** DB transaction guarantees that state change and outbox row
  succeed or fail together. Event is never lost even if the JVM crashes between the DB
  commit and the Kafka send.
- **At-least-once delivery with clear semantics.** Consumers know they must deduplicate on
  `event_id` — this is an explicit contract, not a hidden assumption.
- **Replayable.** Outbox rows are retained for 7 days; a consumer can replay events by
  re-reading the outbox (via an admin endpoint or Debezium snapshot).
- **No distributed transaction / 2PC required.** The pattern achieves the same safety
  guarantee as XA without the overhead and coordinator SPOF.
- **Observable.** `SELECT COUNT(*) FROM order_outbox WHERE published = false` is a simple
  lag metric exported to Prometheus via Spring Actuator custom gauge.

### Negative

- **Polling latency.** 500 ms poll interval means Kafka publish is delayed up to 500 ms
  after the DB commit. Acceptable for all current flows (no sub-second event latency SLO).
  Mitigated in Phase 2 by DynamoDB Streams (sub-second CDC).
- **Outbox table is a hot write path.** High-throughput Order and Payment services write
  two rows per transaction (state + outbox). Monitor `order_outbox` table growth; the
  7-day purge job must keep up.
- **Relay is a single point of failure per service.** If the relay thread dies silently,
  events accumulate. Mitigated by: Prometheus alert on `outbox_lag_seconds > 5`; Spring
  `@Scheduled` thread death causes pod restart via health-check endpoint.
- **Debezium migration needed at scale.** The polling relay is operationally simpler but
  adds DB load. At > 500 TPS per service, replacing the poller with Debezium CDC
  (MySQL binlog reader) is recommended — deferred to a later ADR.

---

## Alternatives Rejected

### Direct Kafka publish in the same request (no outbox)

Publish event immediately after DB commit. Fails if Kafka is unavailable or the JVM
crashes after commit but before send. In financial contexts this is unacceptable. Rejected.

### Two-phase commit (XA / distributed transaction)

Enrol both MySQL and Kafka in an XA transaction coordinated by a JTA transaction manager
(e.g., Atomikos). Provides atomicity across both resources but:

- Kafka's KIP-98 transactions (EOS) have limited compatibility with JTA.
- XA hold locks across the network round-trip to Kafka, dramatically reducing throughput.
- Complex to operate; coordinator is a SPOF.

Rejected in favour of the outbox pattern which achieves equivalent safety without 2PC.

### Event sourcing (events are the source of truth)

Store domain events as the primary record; derive current state by replaying. Eliminates
the dual-write problem entirely because there is only one store. However:

- Requires CQRS read models (projection tables) for all queries — significant complexity uplift.
- The team's Java/Spring expertise is ORM-centric; event sourcing requires a fundamental
  paradigm shift.
- Not justified for Phase 1 scope.

Deferred — could be revisited for Order service in Phase 2 with Kafka as the event log.

### Saga orchestrator managing all publishes (no outbox in services)

Push all Kafka publishing responsibility into a central saga orchestrator (e.g., Spring
State Machine). Services only update their DB; the orchestrator publishes events on their
behalf. Moves the dual-write problem to the orchestrator rather than eliminating it.
Additionally, creates tight coupling between all services and the orchestrator.
Rejected.
