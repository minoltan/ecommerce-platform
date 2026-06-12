# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Purpose

Portfolio and learning project for a Java backend engineer transitioning to a **Software Architect role**. The platform is built in two phases to practice end-to-end architectural ownership: requirements → design → implementation → operational trade-off analysis. Every phase produces industry-standard artefacts (HLD, LLD, ADRs, API specs), not just code.

---

## Bounded Contexts (DDD)

The system is decomposed into seven bounded contexts. Each maps 1-to-1 to a microservice in Phase 1 and a Lambda function group in Phase 2.

| Bounded Context | Responsibility | Key Domain Events |
|---|---|---|
| **User / Auth** | Identity, authentication, JWT issuance | `UserRegistered`, `UserLoggedIn` |
| **Product Catalog** | Product listings, search, pricing | `ProductCreated`, `PriceUpdated` |
| **Cart** | Session cart, line items, pricing snapshot | `CartCheckedOut` |
| **Order** | Order lifecycle, state machine | `OrderPlaced`, `OrderCancelled`, `OrderFulfilled` |
| **Payment** | Payment processing, refunds | `PaymentAuthorised`, `PaymentFailed`, `RefundIssued` |
| **Inventory** | Stock levels, reservations | `StockReserved`, `StockReleased`, `LowStockAlert` |
| **Notification** | Email / SMS / push, templating | _(consumer only — no upstream dependents)_ |

Integration between contexts happens **exclusively via domain events** (Kafka in Phase 1, EventBridge/SNS in Phase 2). No direct DB sharing across contexts.

---

## Two-Phase Architecture

### Phase 1 — Containerised Microservices

| Concern | Technology |
|---|---|
| Services | Java 21, Spring Boot 3.x, Maven multi-module |
| Containerisation | Docker, Docker Compose (local), Kubernetes (prod-like) |
| Sync API | REST over HTTP (OpenAPI 3.x) |
| Async messaging | Apache Kafka (domain events, choreography-based sagas) |
| Primary datastore | MySQL 8 — one schema per service, Flyway migrations |
| Caching | Redis — session cache (Cart), rate limiting, idempotency keys |
| Observability | Spring Actuator, Micrometer → Prometheus + Grafana |

Each service lives at `phase1/<context-name>-service/` and owns its `Dockerfile`, `k8s/` manifests, and DB migrations.

### Phase 2 — AWS Serverless

| Concern | Technology |
|---|---|
| Compute | AWS Lambda (Java 21 / SnapStart) |
| Sync API | API Gateway (REST or HTTP API) |
| Async / events | EventBridge (domain events), SQS (work queues), SNS (fan-out) |
| Orchestration | Step Functions (order saga, payment flow) |
| Datastore | DynamoDB (single-table design per context) |
| Auth | Cognito |

Phase 2 is an **architectural evolution**, not a rewrite from scratch. ADRs must document what changed from Phase 1 and why.

---

## Repository Structure

```
ecommerce-platform/
├── phase1/                  # Spring Boot microservices
│   └── <context>-service/
│       ├── src/
│       ├── k8s/
│       └── Dockerfile
├── phase2/                  # AWS serverless implementations
├── docs/
│   ├── hld/                 # System-level design (C4 diagrams, flow diagrams)
│   ├── lld/                 # Service-level design (data models, sequence diagrams)
│   ├── adr/                 # Architecture Decision Records
│   └── api-specs/           # OpenAPI 3.x (REST), AsyncAPI 2.x (Kafka/EventBridge)
└── skills/
    ├── roles/               # Architect role competencies, stakeholder communication
    └── techniques/          # Design patterns, trade-off frameworks, estimation
```

---

## Completed Artefacts

- **Phase 1 — Requirement Engineering** (`v0.1.0`): event storming, functional/non-functional requirements, user stories, acceptance criteria, OpenAPI stubs, use case diagrams for all 7 bounded contexts.
- **Phase 2 — System Architecture** (`v0.2.0`): C4 Level 1–3 diagrams, sequence diagrams, ER diagrams, order state machine, ADR-0001–ADR-0014, all 7 bounded-context LLDs, cross-cutting HLD sync rounds, API Gateway design, Kubernetes deployment architecture.

## Current Phase: Java Microservices Implementation (Phase 3)

The project is in **Phase 3 — Java Microservices Implementation** (`v0.3.0` on completion). Work at this stage implements the Phase 1 microservices per each bounded context's completed LLD, following `skills/roles/backend-developer.md`:

- Multi-module Maven project under `phase1/`, one module per bounded context
- Flyway migrations matching each LLD's DB Schema section
- Domain aggregates, REST APIs (per `docs/api-specs/*.yaml`), Kafka outbox publishers/consumers
- Unit + integration tests (JUnit 5, Mockito, Testcontainers)
- Per-service `Dockerfile` and `phase1/k8s/{base,overlays}` Kustomize manifests

Build order (per `WORKFLOW.md`): User & Auth → Product Catalog → Cart → Order → Payment → Inventory → Notification → API Gateway.

Implementation must follow each context's LLD and referenced ADRs — do not redesign during implementation; raise discrepancies as Open Questions / ADR amendments instead.

---

## Build & Run (Phase 1)

> Update commands here as services are scaffolded.

```bash
# Build all services (from phase1/)
./mvnw clean package -DskipTests

# Run a single service
./mvnw spring-boot:run -pl <context>-service

# Run a single test class
./mvnw test -pl <context>-service -Dtest=ClassName

# Start local infrastructure (Kafka, MySQL, Redis) — see infra/README.md
docker compose -f docker-compose.infra.yml up -d

# Build and start all services
docker compose up --build

# Deploy to local Kubernetes
kubectl apply -k phase1/k8s/overlays/local
```

---

## Documentation Standards

### HLD (`docs/hld/`)
- One document per major flow (e.g., `order-checkout-flow.md`)
- Must include: C4 Level 1 (System Context) and Level 2 (Container) diagrams using Mermaid or draw.io
- Cover: happy path, failure modes, scaling assumptions

### LLD (`docs/lld/`)
- One document per bounded context (e.g., `lld/order-service.md`)
- Must include: aggregate model, sequence diagrams for key operations, DB schema, API contract reference
- Cover: consistency strategy, caching policy, saga/transaction boundaries

### ADR (`docs/adr/`)
- Filename: `ADR-0001-short-title.md`
- Format: **Context** → **Decision** → **Consequences** (positive + negative) → **Alternatives Rejected**
- Every significant technology choice, pattern selection, or architectural trade-off needs an ADR

### API Specs (`docs/api-specs/`)
- REST: OpenAPI 3.x, one file per service
- Events: AsyncAPI 2.x, one file covering all Kafka topics / EventBridge events

---

## Working With This User

The owner has deep Java/Spring Boot backend expertise and is building **architect-level thinking**. Calibrate all assistance accordingly:

- **Architecture before code.** For any new feature or service, produce the LLD and an ADR draft before writing implementation code.
- **Surface trade-offs explicitly.** Name the CAP implications, operational complexity, and cost of every design choice — don't just recommend the "best" option.
- **DDD discipline.** Enforce aggregate boundaries. Flag any design that would require cross-context DB queries or synchronous calls where events should suffice.
- **Diagram-first communication.** Produce Mermaid diagrams (sequence, component, ER) as a primary design artefact — not an afterthought.
- **Saga awareness.** Order, Payment, and Inventory form a distributed transaction boundary. Default to choreography-based sagas (Kafka); use orchestration (Step Functions / Spring State Machine) only when compensating logic is complex enough to justify it.
- **Phase comparison.** When working in Phase 2, explicitly contrast the serverless design against the Phase 1 equivalent — this comparison is the core learning output.
- **Skills artefacts.** Reusable insights (estimation techniques, stakeholder templates, pattern cheat-sheets) belong in `skills/`. Save them there proactively.
