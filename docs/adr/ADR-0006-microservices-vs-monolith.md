# ADR-0006: Microservices Architecture over Monolith

**Status:** Accepted  
**Date:** 2026-06-08  
**Phase:** ARCH  
**Bounded contexts affected:** All  

---

## Context

The ecommerce platform must support seven distinct bounded contexts: User/Auth, Product
Catalog, Cart, Order, Payment, Inventory, and Notification. A foundational architectural
decision must be made about how these contexts are deployed and operated.

Three options were evaluated:

1. **Monolith** — all contexts in a single deployable unit
2. **Modular monolith** — separate modules in one codebase, single deployable
3. **Microservices** — each context is an independent service and deployable

This decision has the highest blast radius of any ADR in the project. It cannot easily
be reversed once implementation begins.

The platform is a **portfolio and learning project** with a target of building architect-
level competencies. The scope includes two phases: containerised microservices (Phase 1)
and AWS serverless (Phase 2).

---

## Decision

**Microservices architecture** — one service per bounded context, deployed independently,
communicating via REST (queries) and Kafka events (state changes).

Each service:
- Owns its data store (MySQL schema per service — see ADR-0008)
- Has its own repository, Dockerfile, and Kubernetes manifests
- Is independently deployable without coordinated releases
- Scales independently based on its own load profile

---

## Consequences

### Positive

- **Independent deployability.** The Order service can be updated and redeployed without
  touching Payment or Catalog. Reduces release coordination overhead as the system matures.
- **Independent scaling.** Inventory and Payment can scale to handle flash sales without
  over-provisioning Cart or Notification.
- **Technology heterogeneity.** Each service can adopt the best technology for its workload
  (deferred — all Phase 1 services use Java/Spring Boot for consistency).
- **Bounded context enforcement.** Service boundaries make DDD aggregate boundaries
  physically real — accidental cross-context coupling is caught at compile time (no
  shared DB, no shared library with domain models).
- **Phase 2 migration path.** Microservices map cleanly to AWS Lambda function groups;
  each service becomes a Lambda group with its own DynamoDB table and EventBridge source.
- **Learning outcome.** Builds hands-on experience with distributed systems patterns
  (saga, outbox, circuit breaker) that a monolith would not require.

### Negative

- **Operational complexity.** Seven services, seven DBs, Kafka, Redis — the local
  development environment requires Docker Compose; production requires Kubernetes.
  A monolith would run with a single `java -jar`.
- **Distributed tracing required.** A single request spans multiple services; without
  `correlationId` propagation and Grafana Loki, debugging is significantly harder.
- **Network latency.** Cross-service REST calls add ~1–5 ms per hop on the same cluster.
  Acceptable for current latency SLOs but must be monitored.
- **Data consistency is eventual.** Cross-context operations use sagas (ADR-0005) instead
  of ACID transactions. Engineers must reason about partial failure and compensation paths.
- **Higher initial investment.** More boilerplate (Dockerfile, k8s manifests, Flyway
  migrations, Kafka topics) per service before any business logic exists.

---

## Alternatives Rejected

### Monolith

Single Spring Boot application, single MySQL schema, no inter-service messaging.

Pros: Simplest to develop locally, ACID transactions across all bounded contexts, no
network latency between contexts, easiest to debug.

Rejected because:
- Does not achieve the learning goal (no distributed systems experience).
- Cannot migrate to AWS serverless (Phase 2) without a full rewrite.
- Scaling is all-or-nothing; cannot scale Payment independently from Catalog.
- Aggregate boundary enforcement is social (code review) not physical.

### Modular Monolith

Single deployable but with strict internal module boundaries. Modules communicate via
in-process interfaces, not network calls. No shared package references across modules.

Pros: Much simpler operationally than microservices; enforces bounded context boundaries
at the Java module level; ACID transactions available; no Kafka needed.

Would be the right choice for a production system at early scale (< 50K orders/day)
where operational simplicity is the priority.

Rejected for this project because:
- Phase 2 (AWS serverless) cannot be implemented from a modular monolith without
  full decomposition — the migration path requires separate deployable units.
- Does not build distributed systems skills (saga, outbox, circuit breaker).
- Does not demonstrate microservices operational experience for the architect portfolio.

If this were a production system at a startup, the recommendation would be: start with
a modular monolith, extract services only when a specific scaling or deployment need
justifies the operational cost.
