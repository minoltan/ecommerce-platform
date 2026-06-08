# ADR-0004: Service Communication — REST for Queries, Kafka Events for State Changes

**Status:** Accepted  
**Date:** 2026-06-08  
**Phase:** ARCH  
**Bounded contexts affected:** All  

---

## Context

Seven bounded contexts must communicate. Two broad mechanisms are available:

- **Synchronous** (REST/HTTP, gRPC): caller blocks waiting for a response.
- **Asynchronous** (Kafka events): caller publishes and continues; consumer processes
  independently.

The choice affects: latency, availability, coupling, data consistency, and operational
complexity. A blanket policy in either direction causes problems:

- Pure async for everything makes real-time query responses (e.g. "what is the current
  price?") unnecessarily complex.
- Pure sync for everything creates cascading failure chains — if Payment is slow, Order
  is slow, and Cart feels it too.

The platform needs a consistent, principled rule that engineers can apply without case-by-
case architectural review.

---

## Decision

**Rule: REST over HTTP for reads; Kafka domain events for writes / state changes.**

| Communication type | Mechanism | Examples |
|---|---|---|
| Query (read) — caller needs data now | REST (OpenAPI 3.x) | Product Catalog → price lookup by Cart; User/Auth → profile fetch by API Gateway |
| Command that crosses a context boundary and changes state | Kafka event | Order → Payment: `OrderPlaced`; Payment → Inventory: `PaymentAuthorised` |
| Internal service operation (same context) | Method call / in-process | `OrderService.confirm()` calling `OrderRepository.save()` |

### Hard rules (non-negotiable)

1. **No service calls another service's database directly.** All cross-context data access
   goes through the owning service's REST API or a replicated read model.
2. **No synchronous REST call on the happy-path write flow after the API boundary.**
   Once a write request enters the system (e.g., POST /orders/checkout), all downstream
   coordination happens via Kafka events. The HTTP response returns as soon as the local
   DB write + outbox write succeeds.
3. **No Kafka event for a query.** Request/reply over Kafka (correlation ID pattern) is
   forbidden — use REST.
4. **Notification service is consumer-only.** It never drives REST calls into other
   contexts.

### Permitted synchronous calls

| Caller | Callee | Purpose |
|---|---|---|
| API Gateway / BFF | Any service | Routing inbound client requests |
| Cart Service | Product Catalog | Price snapshot at add-to-cart |
| Cart Service | Inventory Service | Availability check (display only, not reservation) |
| Order Service | User Service | Shipping address validation at checkout |
| Payment Service | External gateway | Stripe / Razorpay REST API |

All sync calls must:
- Have a circuit breaker (Resilience4j) with a 2-second timeout.
- Be read-only or idempotent.
- Degrade gracefully: caller returns stale cached data or a user-facing error — never
  propagates an internal 500 upward silently.

---

## Consequences

### Positive

- **Temporal decoupling.** Kafka consumers process at their own pace; a slow Notification
  service does not block Order confirmation.
- **Availability isolation.** If Payment service is degraded, orders can be queued in
  Kafka; existing browsing and cart operations are unaffected.
- **Audit log for free.** Kafka topic retention (7–30 days per topic) provides a durable
  event log without additional infrastructure.
- **Scalability.** Consumers can scale independently; adding Notification replicas does
  not require changes to Order or Payment.
- **Simple query path.** REST for reads keeps query latency predictable and avoids the
  overhead of event-driven projections for simple lookups.

### Negative

- **Eventual consistency on write paths.** A customer placing an order gets a 202 (order
  created); confirmation email arrives seconds later. UX must handle this — order status
  polling or WebSocket push is needed for real-time feedback.
- **Harder to reason about distributed state.** Engineers used to synchronous request/
  response must adopt event-driven mental models. Mitigated by this ADR as a reference
  document and the sequence diagrams (SA-004).
- **Sync calls are still failure points.** Cart → Catalog price lookup is synchronous;
  if Catalog is down, add-to-cart fails. Mitigated by circuit breaker + Redis price cache
  (30-minute TTL).
- **Debugging is harder.** A saga spanning 5 Kafka events is harder to trace than a single
  HTTP call stack. Mitigated by `correlationId` propagated through all events and injected
  into MDC for log correlation (Grafana Loki query: `{correlationId="..."}`)

---

## Alternatives Rejected

### Pure async (Kafka everywhere, including queries)

Implement all cross-service communication via Kafka using the request/reply pattern
(reply-to topic + correlation ID). Provides maximum decoupling but:

- Round-trip latency for a query becomes 50–200 ms (Kafka round trip) vs < 5 ms for an
  internal REST call on the same Kubernetes cluster.
- Debugging correlation across two topics is significantly harder.
- Overkill for simple point-to-point reads.

Rejected. The added complexity does not justify the marginal decoupling benefit for reads.

### Pure sync (REST everywhere)

All cross-context calls are synchronous REST. Easy to reason about locally, but:

- Write paths (checkout) become a synchronous chain: Cart → Order → Payment → Inventory.
  If any link is slow (e.g., Stripe 3-second response), the entire chain blocks.
- Cascading failures: a single slow service degrades all services that call it, including
  user-facing reads.
- No natural audit log — events must be explicitly emitted to a separate store.

Rejected for the write path. Synchronous calls are retained for read operations only.

### gRPC for inter-service sync calls

Replace REST with gRPC for lower latency and strongly typed contracts. Benefits:

- Binary protocol (smaller payload, faster serialisation).
- Server streaming (useful for catalog search results).

Deferred, not rejected. REST is sufficient for Phase 1 volume. gRPC can be adopted in
Phase 2 for high-frequency catalog lookups (product price, stock availability display).
A future ADR will evaluate the trade-off when concrete latency metrics are available.

### Service mesh (Istio / Linkerd) for all sync communication

Route all REST calls through a mesh sidecar to get mTLS, retries, and circuit breaking
at the infrastructure layer rather than application code (Resilience4j). More operationally
powerful but:

- Adds Istio/Linkerd as a mandatory infrastructure dependency for Phase 1.
- Increases local development complexity (sidecars in Docker Compose).

Deferred to Phase 2 (Kubernetes production cluster). Phase 1 uses Resilience4j annotations
in application code.
