# Microservices Architecture — Interview Questions & Answers

---

### Basic

**1. What is a microservice? How does it differ from a monolith?**

A microservice is a small, independently deployable service that owns a single bounded context. A monolith packages all business logic into one deployable unit.

| | Monolith | Microservices |
|---|---|---|
| Deployment | One unit | Independent per service |
| Scaling | Scale everything | Scale individual services |
| Technology | Single stack | Polyglot |
| Failure | One failure can crash all | Failures are isolated |
| Complexity | Simple to start | Complex distributed system |

ADR-0006 documented the choice of microservices for this project due to independent scalability requirements (e.g., Product Catalog needs read scaling; Payment needs high reliability).

---

**2. What is the single responsibility principle at the service level?**

Each microservice owns exactly one bounded context and does one thing well. In this project:
- User/Auth only handles identity and authentication — not product listings, not order processing.
- The boundary is enforced by: separate schemas (ADR-0008), separate deployables, separate Kafka topics.

Violating SRP at the service level leads to a "distributed monolith" — services that are deployed separately but tightly coupled via shared DBs or synchronous chains.

---

**3. What is synchronous vs asynchronous communication between services?**

- **Synchronous** — the caller waits for a response (REST over HTTP). Tight temporal coupling — if the downstream service is down, the call fails immediately.
- **Asynchronous** — the caller publishes an event and continues (Kafka). Loose coupling — downstream services process when ready.

This project uses Kafka for cross-context integration (domain events) per ADR-0007. Synchronous REST is used only for query-type calls where an immediate response is needed (e.g., API Gateway → user-service for token validation).

---

### Intermediate

**4. What is ADR-0006 (microservices vs monolith)?**

ADR-0006 chose microservices over a modular monolith. Key reasons documented:
- Different scaling profiles: Catalog (read-heavy), Payment (reliability-critical), Inventory (write-heavy).
- Independent deployment cycles: Notification can be updated without deploying Order.
- Bounded context isolation reduces blast radius of failures.

Alternatives rejected: modular monolith (good for early stage but limits independent scaling), serverless-first (premature for Phase 1 learning objectives). Phase 2 migrates to AWS serverless to compare.

---

**5. Why does each service have its own database schema (ADR-0008)?**

Database-per-service enforces bounded context isolation at the storage layer:
- No cross-schema SQL joins — services cannot bypass their API boundary.
- Independent schema evolution — User/Auth can add columns to `users` without affecting other services.
- Independent scaling — Catalog might use MySQL with read replicas; Cart uses Redis; Inventory could use a write-optimised DB.

Trade-off: cross-context queries (e.g., "orders with user details") require event-driven projections or API composition at the gateway level.

---

**6. What is a saga pattern? What two types exist and which does this project use?**

A saga coordinates a distributed transaction across multiple services using a sequence of local transactions with compensating transactions on failure.

- **Choreography** — each service reacts to events and emits its own. No central coordinator. Used in this project (Kafka). Order flow: `OrderPlaced` → Payment charges → `PaymentAuthorised` → Inventory reserves → `StockReserved` → Order confirms.
- **Orchestration** — a central saga orchestrator sends commands. Better visibility, easier compensation logic. Used in Phase 2 (AWS Step Functions).

Choreography is chosen per ADR-0014 for Phase 1 because the flows are straightforward and avoid a single-point-of-failure orchestrator.

---

**7. What is the CAP theorem? How does it apply to the user-service?**

CAP theorem: a distributed system can only guarantee two of:
- **Consistency** — all reads see the latest write.
- **Availability** — every request receives a response.
- **Partition tolerance** — the system continues despite network partitions.

The user-service (MySQL + Redis) prioritises **CP** (Consistency + Partition tolerance):
- MySQL with `ddl-auto: validate` enforces strong consistency within a transaction.
- A Redis outage causes service unavailability (auth operations fail) rather than returning stale/incorrect data.
- The outbox pattern provides eventual consistency across service boundaries.

---

**8. What is eventual consistency? How does the outbox pattern achieve it?**

Eventual consistency means that, given no new updates, all replicas will eventually converge to the same value. The outbox pattern achieves it for cross-service events:
- `UserRegistered` is written to `user_auth_outbox` in the same transaction as the `User` row.
- `OutboxRelay` publishes it to Kafka within ≤500ms.
- The Notification service consumes it and sends the email (may be seconds later).

There is a window where the user exists in the DB but the Notification service hasn't received the event yet — this is the "eventually consistent" window.

---

**9. What is a correlation ID? How does `CorrelationIdFilter` propagate it?**

A correlation ID is a UUID assigned to each request, used to trace a single user action across multiple service logs. `CorrelationIdFilter`:
1. Reads `X-Correlation-Id` header from the incoming request (accepts caller-supplied ID).
2. Generates a new UUID if none is provided.
3. Puts it in SLF4J's MDC (`correlationId` key) — it appears in every log line for that request.
4. Echoes it back in the response `X-Correlation-Id` header.
5. `AuthService` uses the correlation ID as the `correlationId` field in outbox events, so the same ID appears in Kafka events and downstream service logs.

---

**10. What is an API Gateway? What role does it play in this architecture?**

The API Gateway is the single entry point for all external clients. Responsibilities:
- **Routing** — routes `/auth/*` to user-service, `/products/*` to catalog-service, etc.
- **Authentication** — validates JWT by calling the user-service JWKS endpoint; downstream services receive pre-validated identity.
- **Rate limiting** — per-client rate limiting at the gateway level.
- **SSL termination** — handles TLS; services communicate over plaintext internally.
- **Request correlation** — injects `X-Correlation-Id` for tracing.

In Phase 1, the API Gateway is the last service in the build order. In Phase 2, AWS API Gateway replaces it.

---

**11. What is service discovery? How would downstream services find the user-service in Kubernetes?**

Service discovery resolves a logical service name to an IP:port. In Kubernetes, every `Service` resource gets a DNS entry: `<service-name>.<namespace>.svc.cluster.local`. Other services reach the user-service at:
```
http://user-service.ecommerce.svc.cluster.local:8081
```
Kubernetes' built-in CoreDNS handles the resolution. No external service registry (Eureka, Consul) is needed. Spring Cloud's `@LoadBalancerClient` or native Kubernetes `Service` load balancing distributes traffic across pods.

---

**12. What is circuit breaking (Resilience4j)? Why is it in the parent POM?**

A circuit breaker monitors calls to downstream services. If failures exceed a threshold, the circuit "opens" — subsequent calls fail fast (return a fallback) without hitting the struggling downstream service. After a timeout, the circuit half-opens to test if the service recovered.

Resilience4j is in the parent POM because it will be needed by Cart (→ Inventory for stock checks) and Order (→ Payment for charge). These services make synchronous HTTP calls where failures must not cascade. User/Auth doesn't make synchronous downstream calls currently, so it doesn't use Resilience4j yet.

---

### Advanced

**13. How would you handle a distributed transaction spanning user-service and order-service?**

Example: a new user's first order must exist only if user registration completed. This is an eventual consistency problem — no 2PC across services.

Approach using saga choreography:
1. `UserRegistered` event published to Kafka.
2. Order service consumes it and creates a `PENDING_USER_VERIFICATION` record.
3. When user verifies email → `UserEmailVerified` event.
4. Order service transitions the record to `ELIGIBLE`.

For compensation: if user is deactivated before email verification, `UserDeactivated` cancels any pending order. Each step is a local transaction with idempotent consumers.

---

**14. What is the strangler fig pattern?**

The strangler fig pattern migrates from a monolith incrementally. New features are built as microservices; old monolith code is replaced gradually by routing traffic to the new service. The monolith "strangles" like a fig tree's roots overtaking a host tree.

In this project's context: if starting from a monolith, the User/Auth bounded context would be extracted first (highest reuse across all other services). An API Gateway would route `/auth/*` to the new microservice while everything else still hits the monolith.

---

**15. What is event sourcing? How does it differ from the outbox pattern?**

- **Event sourcing** — the database of record IS the event log. The current state is derived by replaying events. No mutable state table — only an append-only event store.
- **Outbox pattern** — mutable state tables exist (e.g., `users`); events are a side effect written to an outbox table for publication. The outbox is a delivery mechanism, not the source of truth.

This project uses the outbox pattern — `users` is the source of truth; `user_auth_outbox` is only for Kafka delivery. Event sourcing would mean no `users` table — only a `user_events` store, and current state derived by replaying `UserRegistered`, `UserEmailVerified`, `UserDeactivated` in order.

---

**16. What is the two-phase commit problem?**

2PC (Two-Phase Commit) is a distributed transaction protocol: a coordinator asks all participants to prepare (phase 1), then commits or rolls back all (phase 2). Problems:
- **Blocking** — if the coordinator crashes after phase 1, participants are locked until recovery.
- **Performance** — all participants hold locks during the protocol.
- **Availability** — one participant being down blocks the entire transaction.

This project avoids 2PC entirely — each service only uses local DB transactions. Cross-service consistency is achieved via sagas (eventual consistency + compensating transactions).

---

**17. How would you implement distributed tracing using the `correlationId` header?**

Each service propagates the `X-Correlation-Id` header:
1. `CorrelationIdFilter` extracts or generates the ID and stores it in MDC.
2. All outbound HTTP calls (via `RestTemplate` or `WebClient`) include the header.
3. Kafka outbox events carry the `correlationId` field.
4. Consuming services extract it from the event and put it in their MDC.

For structured tracing (spans, traces), integrate OpenTelemetry with Micrometer Tracing (already a Spring Boot 3.x auto-config). The `correlationId` becomes the trace ID, visible in Jaeger or Zipkin across all service logs.

---

**18. What is backward compatibility in API design? How would you version the user-service API?**

Backward-compatible changes: adding optional fields, adding new endpoints. Breaking changes: removing fields, changing field types, changing required fields.

For a breaking change in the user-service REST API:
1. Add a new version: `POST /v2/auth/register` with the new contract.
2. Keep `POST /v1/auth/register` working (all existing clients unaffected).
3. Deprecate v1 with a `Deprecation` response header.
4. Remove v1 only after all clients have migrated.

In Kafka events, `schemaVersion` in `OutboxEventEnvelope` supports consumer-side version detection — consumers check `schemaVersion` and handle both v1 and v2 payloads during migration.
