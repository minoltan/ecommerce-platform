# ADR-0002: Kafka Topic Design and Partitioning Strategy

**Status:** Accepted
**Date:** 2026-06-10
**Phase:** Phase 1 only — Phase 2 replaces Kafka with EventBridge/SQS (see container-diagram.md §11); a follow-up ADR will define the equivalent EventBridge event-pattern/SQS design when Phase 2 work begins.
**Bounded contexts affected:** All 7 — every service is either a publisher, a consumer, or both.

---

## Context

`event-storming.md` v0.3 catalogues **91 domain events across 7 bounded contexts**, and
`container-diagram.md` §5 has already sketched a topic map at the "topic group" level
(`user-auth.*`, `catalog.*`, `cart.*`, `order.*`, `payment.*`, `inventory.*`) with a
provisional naming convention (`{context}.{entity}.{event}`), partition keys
(`orderId` / `userId`), replication factor 3, and retention (7 days default, 30 days
for `order.*`/`payment.*`). This ADR formalises those provisional choices into a
concrete, implementable topic design and resolves the questions the container diagram
left open:

1. **Granularity:** one Kafka topic per *event type* (91 topics), one topic per
   *bounded context* (7 topics), or something in between?
2. **Partitioning:** how many partitions per topic, and does the chosen key
   (`orderId`/`userId`) actually deliver the ordering guarantees the saga needs?
3. **Consumer group strategy:** how do multiple consumers of the same topic (e.g.,
   `order.*` is consumed by Payment, Inventory, Notification, and Cart) avoid
   competing for the same messages?
4. **Schema evolution:** how do new event types or fields get added without breaking
   existing consumers?

The saga flows (Saga A–E in event-storming.md) depend on **relative ordering** of
events for the same `orderId` — e.g., `OrderPlaced` must be processed by Inventory
before a later `OrderCancelled` for the same order, or `StockReleased` could fire for
an order whose stock was never reserved.

---

## Decision

### 1. Topic granularity — one topic per `{context}.{entity}.{event}`, grouped by context prefix

Each domain event gets its own Kafka topic, named `{context}.{entity}.{event}` in
`kebab-case`, e.g.:

- `order.order.placed`, `order.order.confirmed`, `order.order.cancelled`,
  `order.order.shipped`, `order.order.delivered`, `order.order.failed`,
  `order.return.approved`, …
- `payment.payment.authorised`, `payment.payment.failed`, `payment.refund.processed`, …
- `inventory.inventory-item.stock-reserved`,
  `inventory.inventory-item.stock-reservation-failed`, …

This gives **~50 topics** (consumer-only / internal-only events from the 91-event
catalogue do not need a topic — see §4 below). The `{context}.*` prefix is the "topic
group" referenced informally in `container-diagram.md` §5; it is a naming convention,
not a single physical topic.

### 2. Partitioning

| Topic group | Partition key | Partition count | Rationale |
|---|---|---|---|
| `order.*`, `payment.*`, `inventory.*` | `orderId` | 12 | All saga events for one order land on the same partition → strict per-order ordering. 12 partitions sized for NFR-SCALE-002 (500 orders/min) with headroom for consumer parallelism. |
| `cart.*` | `userId` (or `sessionId` for guest carts) | 6 | Cart events for one user/session are ordered; lower volume than order events. |
| `user-auth.*` | `userId` | 3 | Low volume (login/registration events); ordering per-user is the only requirement. |
| `catalog.*` | `productId` | 6 | Per-product ordering (e.g., `ProductPriceUpdated` must not race with `ProductUnpublished` for the same product). |

**Replication factor: 3** across 3 brokers (as stated in container-diagram.md §5),
`min.insync.replicas=2`, `acks=all` on all producers — required for the at-least-once
durability guarantee that the transactional outbox (ADR pending — outbox pattern,
container-diagram.md §7) depends on.

### 3. Retention

- **Default: 7 days** for `user-auth.*`, `catalog.*`, `cart.*`, `inventory.*`.
- **30 days** for `order.*`, `payment.*`, and `inventory.*` topics that participate in
  financial reconciliation (`inventory.inventory-item.stock-reserved`,
  `inventory.inventory-item.stock-committed`) — supports replay-based audit and
  reconciliation jobs without depending on application DB retention alone.

### 4. Which events get a topic — "cross-context" vs. "internal"

Only events with at least one **cross-context consumer** (per the Cross-Context Event
Boundary Matrix in event-storming.md) get a Kafka topic. Events marked "Internal only"
or "—" in that matrix (e.g., `EmailVerified`, `CartItemAdded`, `OrderNoteAdded`) are
persisted in the owning service's own DB only and are **not** published to Kafka. This
keeps the topic count manageable (~50 instead of 91) and avoids publishing
infrastructure for events nothing outside the service needs.

If a future requirement needs one of these events externally (e.g., an analytics
pipeline wants `CartItemAdded`), a topic is added at that point — adding a topic is a
non-breaking, additive change under this design.

### 5. Consumer groups

Each consuming **service** uses a single consumer group per topic, named
`{consumer-service}.{topic-name}` (e.g., `inventory-service.order.order.placed`,
`notification-service.order.order.placed`). Where a topic has multiple consumer
services (e.g., `order.order.placed` → Inventory, Payment, Notification), each gets its
**own consumer group** so all three receive every message independently — this is
standard Kafka pub/sub fan-out and matches the "multiple BCs react to one event"
pattern throughout event-storming.md.

Within a service, the consumer group has as many consumer instances as partitions (12
for order-keyed topics) to allow horizontal scaling under HPA without exceeding
Kafka's one-consumer-per-partition limit.

### 6. Schema evolution

- Event payloads are versioned via a `schemaVersion` integer field in every event
  envelope (alongside `eventId`, `eventType`, `occurredAt`, `correlationId`).
- New optional fields may be added without bumping `schemaVersion` (consumers ignore
  unknown fields — Jackson `FAIL_ON_UNKNOWN_PROPERTIES=false`).
- Breaking changes (field removal, type change, renamed required field) require a new
  topic suffix (`order.order.placed.v2`) with both versions published during a
  migration window — full schema registry (Confluent Schema Registry / Avro) is
  deferred; JSON + envelope versioning is sufficient for Phase 1's scale.

---

## Consequences

### Positive

- **Per-order ordering guaranteed** for the saga's most critical path — `orderId`-keyed
  partitioning across `order.*`/`payment.*`/`inventory.*` directly satisfies the
  ordering requirement behind H-OR-1, H-OR-2, H-PM-1.
- **Fine-grained consumer subscriptions.** A service that only cares about
  `OrderCancelled` subscribes to exactly `order.order.cancelled`, not a noisy combined
  `order.*` stream it must filter client-side — simpler consumer code, smaller
  per-message deserialization surface.
- **Independent scaling and retention per event type.** `order.order.placed` (high
  volume, 30-day retention) and `order.return.approved` (low volume) can have different
  partition counts/retention without affecting each other — not possible with a single
  combined topic.
- **Additive evolution.** Adding a new domain event = adding a new topic + ACL grant,
  with zero risk to existing consumers (no shared topic schema to coordinate).
- **12 partitions on order-keyed topics** gives ~40 orders/min per partition at peak
  (500/min ÷ 12), comfortably within a single consumer's throughput — no rebalancing
  needed at current NFR-SCALE-002 targets, with room to grow before repartitioning is
  required (repartitioning is a heavy operation — chosen with headroom deliberately).

### Negative

- **~50 topics to manage** (ACLs, retention configs, monitoring dashboards) vs. 7 or
  91 — a middle ground that requires topic-provisioning automation (Terraform / Kafka
  Helm chart values) rather than manual `kafka-topics.sh` per topic. This is accepted
  as a one-time tooling cost.
- **Cross-event ordering is NOT guaranteed across different topics.** E.g.,
  `order.order.placed` and `payment.payment.authorised` are different topics — Kafka
  gives no relative ordering guarantee between them even with the same `orderId` key.
  This is acceptable because the saga's choreography is event-driven request/reaction
  (Payment only acts after consuming `OrderPlaced`; it doesn't need to know Inventory's
  reaction order) — but this constraint must be called out explicitly in each service's
  LLD so engineers don't assume cross-topic ordering.
- **Repartitioning order-keyed topics later is expensive.** If order volume grows
  beyond ~40/min/partition and 12 partitions becomes insufficient, increasing partition
  count on an existing topic changes the `orderId → partition` mapping for *all* keys,
  breaking the in-flight ordering guarantee for orders whose saga spans the
  repartitioning moment. Mitigation: over-provision partitions now (12 vs. a
  tightly-calculated ~7) to delay this; a true fix requires a blue/green topic
  migration, out of scope for Phase 1.

---

## Alternatives Rejected

### One topic per bounded context (7 topics total)

All events from a context (e.g., all of Order's 8 event types) published to a single
`order-events` topic, with `eventType` as a payload field; consumers subscribe to the
whole topic and filter.

Rejected because:
- Consumers that only care about one event type (e.g., Notification only needs
  `OrderShipped` for push notifications, not `OrderNoteAdded`) still receive and
  deserialize every event on the topic — wasted throughput and CPU at scale.
- A single topic mixes high-volume events (`OrderPlaced`, ~500/min) with rare ones
  (`ReturnApproved`), making partition-count and retention tuning a compromise instead
  of per-event-type optimisation.
- Schema evolution is harder: every consumer of `order-events` must handle every event
  type's schema, even ones it ignores, because deserialization happens before filtering.

### One topic per event type with NO context grouping in the name (flat namespace)

Same as the chosen design but without the `{context}.` prefix, e.g., `order-placed`
instead of `order.order.placed`.

Rejected because the `{context}.{entity}.{event}` convention (already proposed in
container-diagram.md §5) makes ACL design trivial — a single ACL rule
`order.*` grants a service read access to all Order-published topics — and gives
immediate visual grouping in Kafka UI tooling (Conduktor, AKHQ) when topic count grows
to ~50.

### Single firehose topic for all 91 events

One topic, all events, `eventType` field for filtering, single massive partition count.

Rejected outright — this is the "distributed monolith" anti-pattern for Kafka. No
per-event retention/partition tuning, every consumer pays the deserialization cost of
every event in the system, and a single hot key (e.g., a viral product's
`orderId`-keyed events) could create a noisy-neighbour problem affecting unrelated
event types sharing the same partition.

### `userId` as the universal partition key (instead of `orderId` for order/payment/inventory)

Use `userId` everywhere for consistency — one customer's entire activity stream stays
ordered.

Rejected because the saga's ordering requirement is **per-order**, not per-user. A
customer placing two concurrent orders (e.g., one regular order and one return) would
have both orders' events interleaved on the same partition under `userId` keying, with
no benefit — and a single very active customer (e.g., a B2B account placing hundreds of
orders) would create a hot partition. `orderId` keying distributes load evenly and
matches the actual consistency boundary (the `Order` aggregate, per ADR/DDD
discussion).
