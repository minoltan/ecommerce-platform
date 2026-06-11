# Design Patterns Used in This Project

**Purpose:** Running catalogue of architectural and design patterns adopted across the Ecommerce Platform, with the reasoning behind each choice. This is a portfolio artefact for the Software Architect transition — it should explain *why*, not just *what*, so the trade-offs can be defended in an interview or design review.

**Status:** Living document — update whenever a new pattern is adopted (and reference the ADR if one exists).

**Sources:** `docs/requirements/event-storming.md`, `docs/hld/system-context.md`, `docs/hld/container-diagram.md`

---

## 1. Domain-Driven Design — Bounded Contexts & Aggregates

**Where used:** All 7 services (User/Auth, Catalog, Cart, Order, Payment, Inventory, Notification).

**Description:** Each microservice owns one bounded context with its own aggregates (`Order`, `Payment`, `InventoryItem`, etc.), enforcing invariants internally and exposing state changes only via domain events.

**Why chosen:**
- Aligns service boundaries with business capabilities, not technical layers — each team/service can evolve its data model independently.
- Prevents the "shared database" anti-pattern: no cross-context DB joins, which keeps services independently deployable and scalable.
- Aggregates give a clear consistency boundary (e.g., `InventoryItem.availableQty = onHandQty - reservedQty` is enforced in one place, not scattered across services).

**Trade-off accepted:** Cross-context queries that would be a single SQL join in a monolith now require either an event-driven read model or a synchronous call — more moving parts, but isolates failure domains (CAP: each context independently chooses CP or AP).

---

## 2. Event-Driven Architecture (Choreography)

**Where used:** All cross-context integration (Kafka topics: `user-auth.*`, `catalog.*`, `cart.*`, `order.*`, `payment.*`, `inventory.*`).

**Description:** Services communicate cross-context exclusively via published domain events (`OrderPlaced`, `PaymentAuthorised`, `StockReserved`, etc.). Each service reacts to events it cares about and publishes its own events in response — no central coordinator.

**Why chosen:**
- Matches NFR-PERF-004 (order placement p99 < 500ms) — the customer-facing request only needs to persist the order; downstream effects (stock reservation, payment initiation, notifications) happen asynchronously.
- Loose coupling: Notification (a pure consumer) can be added/removed without touching any publisher.
- Natural fit for the **choreography-based saga** below — each service knows its own reaction, no god-service owns the whole flow.

**Trade-off accepted:** Eventual consistency (≤ 2s per NFR-CONS-001) across contexts, and harder to trace a single business transaction across services (mitigated by `correlationId` propagation and the saga sequence diagrams in `event-storming.md`).

---

## 3. Saga Pattern — Choreography (Phase 1) → Orchestration (Phase 2)

**Where used:** Checkout flow — Order, Payment, Inventory (Sagas A–E in `event-storming.md`).

**Description:** The "place order" business transaction spans three services with no distributed transaction. Phase 1 uses **choreography**: each service listens for the previous service's event and emits its own (`OrderPlaced` → `StockReserved` + `PaymentInitiated` → `PaymentAuthorised` → `OrderConfirmed` → `StockCommitted`). Failure paths trigger **compensating actions** (`StockReleased`, `CartReactivated`, refunds).

**Why chosen:**
- Order/Payment/Inventory form the platform's distributed-transaction boundary; a 2PC across three databases is operationally fragile and doesn't scale to NFR-SCALE-002 (500 orders/min).
- Choreography keeps Phase 1 simple — no extra orchestration infrastructure (Step Functions doesn't exist yet in containers), and the compensating logic per service is currently simple enough (release stock, reactivate cart, fail order).

**Why it will change in Phase 2:** Per `container-diagram.md` §11, the saga moves to **orchestration via AWS Step Functions** (ADR-003). Lambda's stateless, short-lived execution model makes long-running choreographed state harder to reason about and debug; Step Functions gives a visual execution history and centralizes the compensation logic that's starting to sprawl across services (H-OR-1, H-PM-1).

**Trade-off accepted:** Choreography (Phase 1) trades central visibility for simplicity; if compensating logic grows more complex (e.g., partial refunds + partial stock release combinations), orchestration becomes necessary — this is the explicit Phase 1→2 learning comparison.

---

## 4. Transactional Outbox Pattern

**Where used:** Order Service (`order_outbox`), Payment Service (`payment_outbox`).

**Description:** State changes and the corresponding domain event are written in the **same DB transaction** (entity table + outbox table). A separate relay process polls the outbox table every 500ms, publishes to Kafka, and marks rows as published.

**Why chosen:**
- Order and Payment carry financial state — losing an `OrderPlaced` or `PaymentAuthorised` event because Kafka was unreachable at the moment of the DB commit is unacceptable (at-least-once delivery is a hard requirement, NFR-CONS-001).
- Avoids a distributed transaction (XA) between MySQL and Kafka, which is operationally complex and has poor throughput.

**Why NOT used everywhere:** Cart, User/Auth, Catalog, Inventory, and Notification publish less-critical events where a small window of event loss on service crash is acceptable — adding outbox infrastructure to every service would be over-engineering for the risk level.

**Trade-off accepted:** At-least-once delivery means consumers must be idempotent (see Idempotency pattern below); 500ms relay polling adds latency to event propagation (flagged as OQ-CD-04 — may need Debezium/CDC if order volume grows).

---

## 5. Idempotency Key Pattern

**Where used:** Payment Service (webhook processing on `transactionId`, Redis `idem:{idempotencyKey}` with 24h TTL); Notification Service (`(userId, sourceEventId, channel)` dedup).

**Description:** Every operation that could be retried or delivered more than once (Kafka at-least-once delivery, payment gateway webhook retries) is keyed by a unique identifier. Processing the same key twice is a no-op the second time.

**Why chosen:**
- Direct consequence of choosing at-least-once delivery (outbox pattern + Kafka). Without idempotency, a duplicated `PaymentAuthorised` webhook could double-confirm an order, and a replayed Kafka topic during incident recovery could send duplicate emails (H-NT-1).
- Critical constraint for Payment: "Zero duplicate charges" (event-storming.md, Priority 5) — idempotency on `transactionId` is the only way to guarantee this with an unreliable network.

**Trade-off accepted:** Requires storing dedup keys (Redis TTL, or a `notification` row per `(userId, sourceEventId, channel)`) — small storage/lookup cost in exchange for correctness.

---

## 6. State Machine Pattern (Aggregate Lifecycle)

**Where used:** `Order` aggregate (PENDING → CONFIRMED → PROCESSING → SHIPPED → DELIVERED, with CANCELLED/FAILED branches), `Payment` aggregate (INITIATED → AUTHORISED → CAPTURED → REFUND_INITIATED → REFUNDED), `Product` aggregate (DRAFT → PUBLISHED ↔ UNPUBLISHED → ARCHIVED).

**Description:** Each aggregate's lifecycle is modelled as an explicit state machine with defined legal transitions. Commands are rejected if they don't match a valid transition from the current state (e.g., `CancelOrder` only allowed in PENDING/CONFIRMED).

**Why chosen:**
- Makes invariants explicit and testable — "can this order be cancelled?" becomes a lookup against the state diagram, not scattered `if` conditions.
- Essential for saga reasoning: compensating actions depend on *which state* the aggregate was in when the failure occurred (e.g., refund only if Payment was CAPTURED, not just AUTHORISED — H-PM-4).
- Documented visually as Mermaid state diagrams in `event-storming.md` — doubles as both design artefact and developer reference.

**Trade-off accepted:** Adds upfront design work (every transition must be enumerated) but pays off by eliminating an entire class of "invalid state" bugs, especially around the late-webhook race conditions (H-PM-1, H-OR-2).

---

## 7. Optimistic Locking (Concurrency Control)

**Where used:** `InventoryItem` aggregate (version field on every stock mutation), `Order` aggregate (version field for concurrent cancel/ship — H-OR-2).

**Description:** Each aggregate carries a `version` column. Updates include a `WHERE version = :expectedVersion` clause; a 0-row update means a concurrent modification occurred and the operation is retried or rejected.

**Why chosen:**
- Inventory's hard invariant is `availableQty` must never go negative (H-IN-1: two orders racing for the last unit). Pessimistic locking (`SELECT FOR UPDATE`) would serialize all stock updates per SKU — a throughput bottleneck during flash sales (NFR-SCALE-002: 500 orders/min).
- Optimistic locking allows high read concurrency and only pays a cost (retry) on the rare actual conflict — appropriate because conflicts on a single SKU are infrequent relative to total traffic.

**Trade-off accepted:** Application code must handle retry-on-conflict logic; under pathological contention (e.g., a viral product with 1 unit left and 10,000 concurrent buyers) optimistic locking degrades to many wasted retries — would need a queue-based reservation approach at that scale.

---

## 8. Cache-Aside Pattern

**Where used:** Cart Service (Redis `cart:user:{userId}` / `cart:guest:{sessionId}`), User/Auth (refresh tokens, JWT blacklist), Payment (idempotency keys).

**Description:** Cart state is written to and read from Redis directly (Redis is the primary store for active carts, with TTL-based expiry — not a cache in front of MySQL for this data). On Redis unavailability, Cart falls back to a degraded MySQL read path.

**Why chosen:**
- Constraint from `system-context.md` §6.1: "Guest sessions must not require a database write on every page view." A guest adding/removing cart items repeatedly would generate excessive MySQL writes; Redis hashes with TTL are purpose-built for this ephemeral, high-churn data.
- Session-scoped data (cart, refresh tokens, rate-limit counters) naturally expires — Redis TTL handles cleanup for free, no cron/cleanup job needed.

**Trade-off accepted:** Redis becomes a dependency on the critical path for cart operations; documented degraded-mode fallback (MySQL read, reduced performance) keeps the system available (⚠️ Degraded, not ✅) if Redis is down — explicit acceptance of a weaker availability guarantee for this one path.

---

## 9. API Gateway Pattern

**Where used:** Single entry point for all client traffic (Spring Cloud Gateway in Phase 1, AWS API Gateway in Phase 2).

**Description:** All Guest/Customer/Admin/Webhook traffic enters through one gateway responsible for TLS termination, JWT validation (RS256, `exp` check), rate limiting (Redis token bucket), and path-based routing to the correct microservice.

**Why chosen:**
- Centralizes cross-cutting concerns (auth, rate limiting, TLS) so individual services don't each reimplement JWT validation — reduces duplication and the security review surface to one component.
- Required by the security boundary stated in `system-context.md` §8: "No service is exposed directly to the internet."

**Trade-off accepted:** The gateway is a potential single point of failure / bottleneck — mitigated by horizontal scaling (HPA) and the explicit rule that **service-to-service calls never go through the gateway** (Kubernetes DNS instead), keeping internal traffic off the gateway's critical path.

---

## 10. Circuit Breaker (Planned for Sync Calls)

**Where used:** Order ↔ Payment synchronous hop (the only sync call in the order placement path, per NFR-PERF-004); Order/Payment service deployments generally (NFR-AVAIL-002 → "circuit breakers" listed as a design implication).

**Description:** Wrap synchronous inter-service calls (e.g., Cart validating stock before checkout via Inventory's internal API) with a circuit breaker that fails fast after a threshold of errors, instead of piling up threads waiting on a degraded downstream service.

**Why chosen:**
- The platform's NFR-AVAIL targets (99.9% / 99.95%) explicitly call out that a single slow dependency must not cascade into a full outage. Without a circuit breaker, a slow Inventory service could exhaust Cart's thread pool during a flash sale.
- Pairs with the "Payment Gateway down → return 503 immediately, don't retry synchronously" failure mode in `system-context.md` §6.4 — fail fast is the explicit design intent.

**Trade-off accepted:** Adds a library dependency (e.g., Resilience4j) and requires tuning thresholds/timeouts per call — too aggressive a breaker causes false-positive failures during normal latency spikes.

---

## 11. Dead Letter Queue (DLQ) Pattern

**Where used:** Notification Service — email/SMS/push dispatch retries (NT-05/06/07: 1 min → 5 min → 15 min back-off, then DLQ + on-call alert).

**Description:** Failed async operations are retried a bounded number of times with exponential back-off; after exhausting retries, the message is routed to a separate queue/table for manual inspection rather than being dropped or retried forever.

**Why chosen:**
- Notification failures must never block or retry-loop against the customer-facing order flow (`system-context.md` §6.5: "the platform never waits on a provider response"). DLQ + alerting gives operators visibility without coupling notification health to order health.
- Bounded retries (3×) prevent a permanently-failing provider from generating unbounded retry traffic.

**Trade-off accepted:** DLQ messages require a manual/automated reprocessing path — an operational process, not just a code pattern; currently scoped as "alert on-call," reprocessing tooling is a future artefact.

---

## 12. CQRS-lite (Separate Read Model for Search)

**Where used:** Product Catalog Service — MySQL is the system of record for products; a separate Search Index (Elasticsearch/OpenSearch, pending ADR-010) serves customer search queries.

**Description:** Writes (create/update/publish product) go to MySQL. On every publish/update/archive event, the product is projected into a denormalized search document. Reads for search/browse hit the search index, not MySQL directly.

**Why chosen:**
- NFR-PERF-001 requires product search p95 < 200ms across a potentially large catalogue with full-text and faceted queries — relational full-text search doesn't scale to this latency target with complex filters.
- Decouples the write model (normalized, transactional) from the read model (denormalized, optimized for query patterns) without going to full event sourcing.

**Trade-off accepted:** Search index lags MySQL by up to 5 seconds (H-PC-3, accepted as a documented eventual-consistency window); requires a fallback (MySQL full-text search) if the search index is down, adding a second query implementation to maintain.

---

## Pattern Decision Log (Cross-Reference)

| Pattern | First adopted in | Related ADR | Status |
|---|---|---|---|
| DDD Bounded Contexts / Aggregates | event-storming.md v0.1 | — | Accepted |
| Event-Driven Architecture (Choreography) | event-storming.md v0.1 | — | Accepted (Phase 1) |
| Saga (Choreography → Orchestration) | event-storming.md v0.3 | ADR-003 (planned) | Phase 1 accepted; Phase 2 change documented |
| Transactional Outbox | container-diagram.md v0.1 | — | Accepted |
| Idempotency Key | event-storming.md v0.3 | — | Accepted |
| State Machine (aggregate lifecycle) | event-storming.md v0.1 | — | Accepted |
| Optimistic Locking | event-storming.md v0.3 | ADR candidate (H-IN-1, H-CT-1) | Pending ADR |
| Cache-Aside (Redis) | system-context.md v0.1 | — | Accepted |
| API Gateway | container-diagram.md v0.1 | OQ-CD-01 (Spring Cloud Gateway vs Nginx) | Accepted, gateway tech open |
| Circuit Breaker | container-diagram.md v0.1 | — | Planned, not yet detailed |
| Dead Letter Queue | event-storming.md v0.1 | — | Accepted |
| CQRS-lite (Search read model) | container-diagram.md v0.1 | ADR-010 (planned) | Pending ADR |

---

## How to Extend This Document

When a new pattern is adopted (in an LLD, ADR, or implementation):
1. Add a numbered section: **Where used / Description / Why chosen / Trade-off accepted**.
2. Add a row to the Pattern Decision Log.
3. If the pattern resolves a hotspot from `event-storming.md`, reference the hotspot ID (e.g., H-IN-1).
4. If the pattern differs between Phase 1 and Phase 2, state both and explain the driver for the change — this comparison is the core learning output of this project.
