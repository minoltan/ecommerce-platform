# ADR-0005: Choreography-Based Saga for Order Management (Phase 1); Orchestration in Phase 2

**Status:** Accepted  
**Date:** 2026-06-08  
**Phase:** ARCH  
**Bounded contexts affected:** Order, Payment, Inventory  

---

## Context

The order checkout flow is a **distributed transaction** spanning three services:

```
Order Service → Payment Service → Inventory Service
```

All three must succeed atomically: if payment fails, no stock is reserved; if stock is
unavailable after payment, the payment must be voided. There is no shared database that
can provide ACID guarantees across these contexts.

Two saga coordination styles are available:

- **Choreography:** Each service listens to events and reacts without a central
  coordinator. Services are autonomous.
- **Orchestration:** A central saga orchestrator (Step Functions, Spring State Machine)
  drives each step explicitly and handles compensations.

The choice has long-term consequences for coupling, testability, and operational
observability.

---

## Decision

**Phase 1: Choreography-based saga over Kafka.**  
**Phase 2: Orchestration via AWS Step Functions for the confirmation saga.**

### Phase 1 — Choreography

Each service publishes and reacts to domain events. No central coordinator exists.

```
Order publishes:   OrderPlaced
Payment consumes:  OrderPlaced → authorise → PaymentAuthorised | PaymentFailed
Inventory consumes: PaymentAuthorised → reserve → StockReserved | StockUnavailable
Order consumes:    StockReserved → CONFIRMED
                   PaymentFailed → PAYMENT_FAILED (retry or cancel)
                   StockUnavailable → trigger PaymentVoid, → CANCELLED
```

Compensation is **event-driven**:

- `StockUnavailable` → Order publishes `PaymentVoidRequested` → Payment voids and
  publishes `PaymentVoided` → Order moves to `CANCELLED`.
- `OrderCancelled` → Inventory publishes `StockReleased`.

All events carry `orderId`, `correlationId`, and `timestamp`. Each service stores enough
local state to handle its compensation independently.

**Implementation notes:**

- Kafka consumer group per service; each service owns its consumer.
- Idempotent consumers: check `processed_event_ids` Redis key (24-hour TTL) before
  executing.
- Outbox pattern (ADR-0003) ensures events are published reliably.
- Dead-letter topic (`order.dlq`, `payment.dlq`, `inventory.dlq`) for messages that fail
  after 5 retries; ops team monitors DLQ lag.

### Phase 2 — Orchestration (Step Functions)

The confirmation saga (Order → Payment → Inventory join) is migrated to an AWS Step
Functions Express Workflow:

```
StartExecution(orderId)
  → Lambda: authorisePayment (waitForTaskToken, timeout: 30s)
  → Lambda: reserveStock    (waitForTaskToken, timeout: 15s)
  → Lambda: confirmOrder
  → Catch: compensate (void payment, release stock, cancel order)
```

The orchestrator holds saga state explicitly in the Step Functions execution history.
Compensations are `Catch` branches — no ad-hoc event listening required.

**Why orchestration in Phase 2?**

The confirmation saga requires joining two independent async responses (`PaymentAuthorised`
+ `StockReserved`) with individual timeouts. Expressing this in choreography requires
Order Service to maintain a partial-state record and poll for both events — effectively
reimplementing an orchestrator. Step Functions `waitForTaskToken` handles this natively.

---

## Consequences

### Positive (Phase 1 Choreography)

- **Loose coupling.** Payment and Inventory know nothing about Order's internal state;
  they react to events. Adding a new downstream consumer (e.g., Fraud Detection) requires
  no changes to existing services.
- **Independent deployability.** Services can be deployed and scaled independently without
  coordinating with an orchestrator release.
- **No SPOF.** No single orchestrator whose failure stalls all sagas.
- **Natural fit for Kafka.** Choreography maps directly to the Kafka topic/consumer-group
  model; no additional infrastructure.

### Negative (Phase 1 Choreography)

- **Harder to visualise.** The saga flow is implicit in event subscriptions spread across
  three codebases. SA-004 sequence diagrams and this ADR are the primary documentation.
- **Compensating transaction complexity.** Each service must implement its own compensation
  logic and handle edge cases (e.g., PaymentVoidRequested arrives after payment already
  captured). All compensation handlers must be idempotent.
- **Event ordering is not guaranteed across topics.** Kafka guarantees ordering within a
  partition but not across `order.*`, `payment.*`, and `inventory.*` topics. Design must
  not assume cross-topic ordering.
- **Debugging a stalled saga** requires correlating logs across three services by
  `correlationId`. Mitigated by structured logging + Grafana Loki.

### Positive (Phase 2 Orchestration)

- **Explicit saga state.** Step Functions execution history is the audit trail; no need
  to correlate logs.
- **Timeout handling native.** `waitForTaskToken` with a `TimeoutSeconds` field handles
  the "payment not authorised within 30 seconds" scenario without custom timer logic.
- **Compensation as catch branches.** Rollback is a first-class concept in the state
  machine definition, not scattered event handlers.

### Negative (Phase 2 Orchestration)

- **Central coordinator SPOF.** If Step Functions is unavailable, no new orders can be
  confirmed. Mitigated by AWS SLA (99.9%) and regional failover.
- **Coupling via orchestrator.** Payment and Inventory Lambda functions are invoked by
  Step Functions — they must expose a task-token callback interface. Changing the saga
  requires an orchestrator redeploy.
- **Cost.** Step Functions Express Workflow charges per state transition. At 100K
  orders/day × ~10 transitions/saga = 1M transitions/day — approximately $25/day at
  current pricing. Acceptable but must be monitored.

---

## Alternatives Rejected

### Orchestration in Phase 1 (Spring State Machine)

Use Spring State Machine as the saga orchestrator in the Order Service. Each saga step
is a state in the machine; transitions are driven by Kafka events consumed by the Order
Service.

Viable but:

- Spring State Machine requires the Order Service to consume events from Payment and
  Inventory topics — creating implicit coupling.
- State machine persistence (in-memory vs DB-backed) adds operational complexity.
- The Order Service becomes a bottleneck for all saga coordination.

Deferred to Phase 2 (replaced by Step Functions). Choreography is simpler to implement
correctly for Phase 1 scope (< 1K orders/day).

### XA / Two-Phase Commit across services

No viable distributed XA implementation exists that spans MySQL, Kafka, and an external
payment gateway. Rejected without further analysis.

### Process Manager (Saga Orchestrator as separate service)

Deploy a dedicated Process Manager service that coordinates the saga without being tied
to Order business logic. More architecturally pure than embedding orchestration in Order
Service, but:

- Adds a seventh service before Phase 1 is validated.
- Operational overhead of a new service with its own DB and deployment pipeline.

Deferred. Could be revisited if the saga logic grows beyond the confirmation, cancellation,
and return flows defined in SA-006.

### Event-carried state transfer (pass full order state in each event)

Instead of each service querying Order for context, embed the full order state in every
event. Reduces REST calls but:

- Kafka messages become large (multi-KB JSON), increasing broker storage and consumer
  memory pressure.
- Sensitive data (customer PII, payment amounts) embedded in inventory events violates
  least-privilege data sharing.

Rejected. Events carry only the fields the consumer needs (orderId, variantId, qty,
amount) — not the full aggregate.
