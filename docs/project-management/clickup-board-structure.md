# ClickUp Board Structure — Ecommerce Platform

**Role:** Project Manager
**Operation:** Board design — all 8 phases
**Version:** 1.0
**Status:** Ready for ClickUp setup

---

## Space

| Field | Value |
|---|---|
| Space name | `Ecommerce Platform` |
| Icon | 🛒 |
| Colour | Blue (#0052CC) |
| Members | All team members with role-based permissions |
| Default view | Board (Kanban) |

---

## Phase Map

| # | Phase Label | Full Name | Primary Role | Key Output |
|---|---|---|---|---|
| 1 | SETUP | Project Setup & Kickoff | PM | Repo, board, ceremonies, tooling |
| 2 | RE | Requirement Engineering | Requirements Engineer | Event storming, FRs, NFRs, stories, API stubs |
| 3 | HLD | High-Level Design | Architect | C4 diagrams, system context, ADRs |
| 4 | LLD | Low-Level Design | Architect + RE | Per-service domain model, schema, sequence diagrams |
| 5 | IMPL | Implementation | Developer | Working microservices per bounded context |
| 6 | TEST | Testing | QA + Developer | Unit, integration, contract, E2E tests |
| 7 | CICD | CI/CD & DevOps | DevOps | Pipelines, Docker, Kubernetes, observability |
| 8 | AWS | AWS Serverless | Architect + Developer | Lambda, DynamoDB, Step Functions migration |

> **Note:** ARCH from the PM role file is split into HLD + LLD here because they produce different artefacts, involve different reviewers, and run partially in parallel for different bounded contexts.

---

## Custom Fields (Space-Level — Applied to All Tasks)

| Field Name | Type | Options / Format | Required |
|---|---|---|---|
| **Phase** | Dropdown | SETUP · RE · HLD · LLD · IMPL · TEST · CICD · AWS | Yes |
| **Bounded Context** | Dropdown | User/Auth · Product Catalog · Cart · Order · Payment · Inventory · Notification · Cross-Cutting | Yes |
| **Role** | Dropdown | PM · Requirements Engineer · Architect · Developer · QA · DevOps | Yes |
| **Story Points** | Number | Fibonacci: 1, 2, 3, 5, 8, 13 | Yes |
| **Sprint** | Dropdown | SP-00 · SP-01 · SP-02 · … (add as needed) | Yes |
| **Risk Link** | Text | RISK-NNN reference | No |
| **Artefact Path** | Text | File path of output document or code | No |
| **Depends On** | Relationship | Link to blocking task(s) | No |

---

## Status Workflow (All Lists)

```
BACKLOG → READY → IN PROGRESS → IN REVIEW → BLOCKED → DONE
```

| Status | Colour | Meaning |
|---|---|---|
| BACKLOG | Grey | Defined but not sprint-ready |
| READY | Blue | Fully defined, unblocked, eligible to pull |
| IN PROGRESS | Yellow | Actively being worked |
| IN REVIEW | Purple | Document in peer review / PR open |
| BLOCKED | Red | Waiting on external dependency — must have blocker note |
| DONE | Green | Acceptance criteria met, artefact delivered |

---

## Board Hierarchy — Full Structure

```
Space: Ecommerce Platform
│
├── Folder: Phase 1 — Project Setup (SETUP)
│   └── List: Project Kickoff
│
├── Folder: Phase 2 — Requirement Engineering (RE)
│   ├── List: RE — Cross-Cutting
│   ├── List: RE — User/Auth
│   ├── List: RE — Product Catalog
│   ├── List: RE — Cart
│   ├── List: RE — Order
│   ├── List: RE — Payment
│   ├── List: RE — Inventory
│   └── List: RE — Notification
│
├── Folder: Phase 3 — High-Level Design (HLD)
│   ├── List: HLD — System Context & Containers
│   ├── List: HLD — Architecture Decision Records
│   └── List: HLD — Deployment Architecture
│
├── Folder: Phase 4 — Low-Level Design (LLD)
│   ├── List: LLD — User/Auth
│   ├── List: LLD — Product Catalog
│   ├── List: LLD — Cart
│   ├── List: LLD — Order
│   ├── List: LLD — Payment
│   ├── List: LLD — Inventory
│   └── List: LLD — Notification
│
├── Folder: Phase 5 — Implementation (IMPL)
│   ├── List: IMPL — Infrastructure & Shared
│   ├── List: IMPL — User/Auth Service
│   ├── List: IMPL — Product Catalog Service
│   ├── List: IMPL — Cart Service
│   ├── List: IMPL — Order Service
│   ├── List: IMPL — Payment Service
│   ├── List: IMPL — Inventory Service
│   └── List: IMPL — Notification Service
│
├── Folder: Phase 6 — Testing (TEST)
│   ├── List: TEST — Unit Tests
│   ├── List: TEST — Integration Tests
│   ├── List: TEST — Contract Tests
│   ├── List: TEST — End-to-End Tests
│   └── List: TEST — Performance & Load
│
├── Folder: Phase 7 — CI/CD & DevOps (CICD)
│   ├── List: CICD — Containerisation
│   ├── List: CICD — Kubernetes
│   ├── List: CICD — Pipelines
│   └── List: CICD — Observability
│
└── Folder: Phase 8 — AWS Serverless (AWS)
    ├── List: AWS — Infrastructure as Code
    ├── List: AWS — User/Auth (Cognito + Lambda)
    ├── List: AWS — Product Catalog (Lambda + DynamoDB)
    ├── List: AWS — Cart (Lambda + DynamoDB)
    ├── List: AWS — Order + Payment (Step Functions)
    ├── List: AWS — Inventory (Lambda + DynamoDB)
    └── List: AWS — Notification (SQS + SNS + EventBridge)
```

---

## Phase 1 — Project Setup (SETUP)

### List: Project Kickoff

| Epic ID | Epic Name | Role |
|---|---|---|
| EP-SETUP-001 | Repository & Tooling Setup | PM + Developer |
| EP-SETUP-002 | ClickUp Board Configuration | PM |
| EP-SETUP-003 | Sprint 0 Ceremonies Setup | PM |
| EP-SETUP-004 | Risk Register Initialisation | PM |

#### EP-SETUP-001: Repository & Tooling Setup

| Task ID | Task | Role | Points | Priority | Status |
|---|---|---|---|---|---|
| TASK-SETUP-001 | Initialise GitHub repository with branch strategy | Developer | 1 | Urgent | DONE |
| TASK-SETUP-002 | Create CLAUDE.md with project context | PM + RE | 2 | Urgent | DONE |
| TASK-SETUP-003 | Configure MCP servers (filesystem, GitHub) | Developer | 1 | High | DONE |
| TASK-SETUP-004 | Create docs/ directory structure | PM | 1 | High | DONE |
| TASK-SETUP-005 | Create skills/roles/ directory and role files | PM + RE | 2 | High | DONE |

#### EP-SETUP-002: ClickUp Board Configuration

| Task ID | Task | Role | Points | Priority | Status |
|---|---|---|---|---|---|
| TASK-SETUP-010 | Create Space and all Folders in ClickUp | PM | 2 | Urgent | READY |
| TASK-SETUP-011 | Configure all custom fields (Phase, Context, Role, Points, Sprint) | PM | 2 | Urgent | READY |
| TASK-SETUP-012 | Set up status workflow on all lists | PM | 1 | Urgent | READY |
| TASK-SETUP-013 | Create all Epics from this document | PM | 3 | High | READY |
| TASK-SETUP-014 | Import Phase 2 RE tasks from user-stories.md | PM | 2 | High | READY |

#### EP-SETUP-003: Sprint 0 Ceremonies Setup

| Task ID | Task | Role | Points | Priority | Status |
|---|---|---|---|---|---|
| TASK-SETUP-020 | Create recurring Google Calendar events (all ceremonies) | PM | 1 | High | READY |
| TASK-SETUP-021 | Set up Slack channels (#standup, #blockers, #sprint-planning, #releases, #architecture) | PM | 1 | High | READY |
| TASK-SETUP-022 | Publish Sprint 0 kick-off message to #sprint-planning | PM | 1 | Normal | READY |

#### EP-SETUP-004: Risk Register Initialisation

| Task ID | Task | Role | Points | Priority | Status |
|---|---|---|---|---|---|
| TASK-SETUP-030 | Create docs/pm/risk-register.md | PM | 1 | Urgent | READY |
| TASK-SETUP-031 | Seed 8 ADR-candidate hotspots as RISK-001 to RISK-008 | PM | 3 | Urgent | READY |
| TASK-SETUP-032 | Assign risk owners from team | PM | 1 | High | READY |

---

## Phase 2 — Requirement Engineering (RE)

### Epics Across All Lists

| Epic ID | Epic Name | List | Artefact Path | Points Total |
|---|---|---|---|---|
| EP-RE-001 | Event Storming — all contexts | RE — Cross-Cutting | `docs/requirements/event-storming.md` | 13 |
| EP-RE-002 | Functional Requirements | RE — Cross-Cutting | `docs/requirements/functional-requirements.md` | 13 |
| EP-RE-003 | Non-Functional Requirements | RE — Cross-Cutting | `docs/requirements/non-functional-requirements.md` | 8 |
| EP-RE-004 | User Stories — all contexts | RE — Cross-Cutting | `docs/requirements/user-stories.md` | 21 |
| EP-RE-005 | Acceptance Criteria — critical stories | RE — Cross-Cutting | `docs/requirements/acceptance-criteria.md` | 21 |
| EP-RE-006 | API Contract Stubs — all services | RE — Cross-Cutting | `docs/api-specs/[context]-service-api.yaml` | 21 |
| EP-RE-007 | Use Case Diagrams — all contexts | RE — Cross-Cutting | `docs/requirements/use-cases/` | 14 |

> **Current status:** All RE epics DONE. Phase 2 is complete and committed to GitHub.

---

## Phase 3 — High-Level Design (HLD)

### List: HLD — System Context & Containers

| Epic ID | Epic Name | Points Total |
|---|---|---|
| EP-HLD-001 | C4 Level 1 — System Context Diagram | 8 |
| EP-HLD-002 | C4 Level 2 — Container Diagram | 13 |
| EP-HLD-003 | Service Communication Design | 8 |
| EP-HLD-004 | Data Store Assignment per Service | 5 |

#### EP-HLD-001: C4 Level 1 — System Context Diagram

| Task ID | Task | Role | Points | Priority |
|---|---|---|---|---|
| TASK-HLD-001 | Identify all external actors (Customer, Admin, Payment GW, Email, SMS, Push) | Architect | 2 | High |
| TASK-HLD-002 | Identify all external systems and dependencies | Architect | 2 | High |
| TASK-HLD-003 | Draw C4 L1 diagram (Mermaid or draw.io) | Architect | 2 | High |
| TASK-HLD-004 | Review and sign-off HLD-001 | Architect + RE | 2 | High |

#### EP-HLD-002: C4 Level 2 — Container Diagram

| Task ID | Task | Role | Points | Priority |
|---|---|---|---|---|
| TASK-HLD-010 | Map all 7 microservices as containers | Architect | 2 | Urgent |
| TASK-HLD-011 | Map shared infrastructure (Kafka, MySQL, Redis, Kubernetes) | Architect | 2 | Urgent |
| TASK-HLD-012 | Document sync vs async communication on diagram | Architect | 2 | High |
| TASK-HLD-013 | Document API Gateway as entry point | Architect | 2 | High |
| TASK-HLD-014 | Review and sign-off HLD-002 | Architect + Team | 3 | High |

#### EP-HLD-003: Service Communication Design

| Task ID | Task | Role | Points | Priority |
|---|---|---|---|---|
| TASK-HLD-020 | Define Kafka topic naming convention and partitioning strategy | Architect | 3 | Urgent |
| TASK-HLD-021 | Define REST API versioning and routing strategy | Architect | 2 | High |
| TASK-HLD-022 | Design JWT propagation across services (inter-service auth) | Architect | 3 | High |

#### EP-HLD-004: Data Store Assignment per Service

| Task ID | Task | Role | Points | Priority |
|---|---|---|---|---|
| TASK-HLD-030 | Assign primary datastore per bounded context (MySQL schema isolation) | Architect | 2 | High |
| TASK-HLD-031 | Document Redis usage per service (session, cache, idempotency keys) | Architect | 2 | Normal |
| TASK-HLD-032 | Document Kafka topic ownership map (producer → topic → consumer) | Architect | 1 | High |

### List: HLD — Architecture Decision Records

| Epic ID | Epic Name | Points Total |
|---|---|---|
| EP-HLD-010 | ADR — 8 high-severity hotspot decisions | 26 |

#### EP-HLD-010: Architecture Decision Records

| Task ID | ADR Title | Hotspot(s) | Role | Points | Priority |
|---|---|---|---|---|---|
| TASK-HLD-040 | ADR-001: Monetary amount precision — integer paise storage | H-PM-3 | Architect | 2 | **Urgent** |
| TASK-HLD-041 | ADR-002: Stock reservation concurrency — optimistic locking strategy | H-IN-1, H-CT-1 | Architect | 3 | **Urgent** |
| TASK-HLD-042 | ADR-003: Checkout saga pattern — choreography vs orchestration | H-OR-1, H-OR-2 | Architect | 5 | **Urgent** |
| TASK-HLD-043 | ADR-004: Payment webhook idempotency — deduplication on transactionId | H-PM-1, H-PM-2 | Architect | 3 | **Urgent** |
| TASK-HLD-044 | ADR-005: Stock reservation TTL and expiry compensation | H-IN-2 | Architect | 3 | **Urgent** |
| TASK-HLD-045 | ADR-006: Notification deduplication — idempotency on sourceEventId | H-NT-1 | Architect | 2 | High |
| TASK-HLD-046 | ADR-007: Coupon usage atomic enforcement strategy | H-CT-4 | Architect | 3 | High |
| TASK-HLD-047 | ADR-008: Late payment webhook after order FAILED — auto-refund policy | H-PM-1 | Architect | 3 | **Urgent** |
| TASK-HLD-048 | ADR-009: Database-per-service — schema isolation strategy | Cross-cutting | Architect | 2 | High |
| TASK-HLD-049 | ADR-010: Caching strategy — Redis TTL and eviction policy per service | Cross-cutting | Architect | 2 | Normal |

### List: HLD — Deployment Architecture

| Epic ID | Epic Name | Points Total |
|---|---|---|
| EP-HLD-020 | Kubernetes cluster and service mesh design | 16 |
| EP-HLD-021 | Infrastructure sizing and HA design | 10 |

#### EP-HLD-020: Kubernetes Cluster Design

| Task ID | Task | Role | Points | Priority |
|---|---|---|---|---|
| TASK-HLD-060 | Define Kubernetes namespace strategy (dev, staging, prod) | DevOps + Architect | 2 | High |
| TASK-HLD-061 | Design Kubernetes RBAC and service account model | DevOps | 2 | High |
| TASK-HLD-062 | Design HPA (Horizontal Pod Autoscaling) thresholds per service | DevOps + Architect | 3 | High |
| TASK-HLD-063 | Design Ingress routing and TLS termination | DevOps | 3 | High |
| TASK-HLD-064 | Design secrets management strategy (Kubernetes Secrets + Vault/SSM) | DevOps + Architect | 3 | High |
| TASK-HLD-065 | Document deployment architecture diagram | Architect | 3 | High |

#### EP-HLD-021: Infrastructure HA Design

| Task ID | Task | Role | Points | Priority |
|---|---|---|---|---|
| TASK-HLD-070 | MySQL HA design — primary + read replica, automatic failover | DevOps + Architect | 3 | High |
| TASK-HLD-071 | Redis HA design — Sentinel vs Cluster mode decision | DevOps + Architect | 2 | Normal |
| TASK-HLD-072 | Kafka cluster design — 3 brokers, replication factor 3, topic partitions | DevOps + Architect | 3 | High |
| TASK-HLD-073 | Observability stack design — Prometheus, Grafana, Loki, OpenTelemetry | DevOps | 2 | Normal |

---

## Phase 4 — Low-Level Design (LLD)

### Epics Summary (one per bounded context)

| Epic ID | Epic Name | List | Artefact Path | Points Total |
|---|---|---|---|---|
| EP-LLD-001 | LLD — User/Auth Service | LLD — User/Auth | `docs/lld/user-auth-lld.md` | 21 |
| EP-LLD-002 | LLD — Product Catalog Service | LLD — Product Catalog | `docs/lld/product-catalog-lld.md` | 21 |
| EP-LLD-003 | LLD — Cart Service | LLD — Cart | `docs/lld/cart-lld.md` | 18 |
| EP-LLD-004 | LLD — Order Service | LLD — Order | `docs/lld/order-lld.md` | 29 |
| EP-LLD-005 | LLD — Payment Service | LLD — Payment | `docs/lld/payment-lld.md` | 23 |
| EP-LLD-006 | LLD — Inventory Service | LLD — Inventory | `docs/lld/inventory-lld.md` | 18 |
| EP-LLD-007 | LLD — Notification Service | LLD — Notification | `docs/lld/notification-lld.md` | 16 |

#### EP-LLD-001: LLD — User/Auth

| Task ID | Task | Role | Points | Priority |
|---|---|---|---|---|
| TASK-LLD-001 | Domain model — User and AuthSession aggregates with invariants | Architect | 3 | High |
| TASK-LLD-002 | MySQL schema design — users, sessions, addresses, roles | Architect | 2 | High |
| TASK-LLD-003 | JWT design — RS256 key pair, access token payload, refresh token rotation | Architect | 3 | High |
| TASK-LLD-004 | Redis schema — refresh token storage, session cache | Architect | 2 | High |
| TASK-LLD-005 | Sequence diagrams — register, verify, login, refresh, logout, reset | Architect | 3 | High |
| TASK-LLD-006 | Password reset token storage and TTL design | Architect | 2 | Normal |
| TASK-LLD-007 | OWASP security review for User/Auth surface | Architect + RE | 3 | High |
| TASK-LLD-008 | LLD review and sign-off — User/Auth | Architect + RE | 3 | High |

#### EP-LLD-002: LLD — Product Catalog

| Task ID | Task | Role | Points | Priority |
|---|---|---|---|---|
| TASK-LLD-010 | Domain model — Product, Category, ProductVariant aggregates | Architect | 3 | High |
| TASK-LLD-011 | MySQL schema design — products, variants, categories, images | Architect | 3 | High |
| TASK-LLD-012 | Search indexing design — Elasticsearch document structure, index mapping | Architect | 5 | High |
| TASK-LLD-013 | Image storage design — S3 bucket structure, CDN URLs, upload flow | Architect | 2 | Normal |
| TASK-LLD-014 | Redis cache design — product detail TTL, cache invalidation on update | Architect | 2 | Normal |
| TASK-LLD-015 | Sequence diagrams — create, publish, price update, variant add | Architect | 3 | Normal |
| TASK-LLD-016 | LLD review and sign-off — Product Catalog | Architect + RE | 3 | High |

#### EP-LLD-003: LLD — Cart

| Task ID | Task | Role | Points | Priority |
|---|---|---|---|---|
| TASK-LLD-020 | Domain model — Cart and CartItem aggregates with price snapshot invariant | Architect | 2 | High |
| TASK-LLD-021 | Redis data structure — cart storage schema, TTL, guest vs user key strategy | Architect | 3 | High |
| TASK-LLD-022 | Guest cart to user cart merge algorithm design | Architect | 3 | High |
| TASK-LLD-023 | Price snapshot and staleness detection design | Architect | 2 | High |
| TASK-LLD-024 | Coupon validation integration — API contract with Coupon Service | Architect | 2 | Normal |
| TASK-LLD-025 | Sequence diagrams — add item, checkout, merge, reactivate | Architect | 3 | Normal |
| TASK-LLD-026 | LLD review and sign-off — Cart | Architect + RE | 3 | High |

#### EP-LLD-004: LLD — Order (highest complexity)

| Task ID | Task | Role | Points | Priority |
|---|---|---|---|---|
| TASK-LLD-030 | Domain model — Order, OrderLineItem, Return aggregates with state machine | Architect | 3 | **Urgent** |
| TASK-LLD-031 | MySQL schema design — orders, line items, returns, status timeline | Architect | 3 | High |
| TASK-LLD-032 | Order state machine implementation design — transitions, guards, actions | Architect | 5 | **Urgent** |
| TASK-LLD-033 | Choreography saga design — OrderPlaced → parallel Payment + Inventory flows | Architect | 8 | **Urgent** |
| TASK-LLD-034 | Optimistic locking design — version field on Order aggregate | Architect | 3 | High |
| TASK-LLD-035 | Sequence diagrams — place, confirm, cancel, ship, deliver, return | Architect | 5 | High |
| TASK-LLD-036 | Return window TTL scheduler design | Architect | 2 | Normal |
| TASK-LLD-037 | LLD review and sign-off — Order | Architect + RE | 5 | High |

#### EP-LLD-005: LLD — Payment

| Task ID | Task | Role | Points | Priority |
|---|---|---|---|---|
| TASK-LLD-040 | Domain model — Payment and Refund aggregates with state machine | Architect | 2 | **Urgent** |
| TASK-LLD-041 | MySQL schema design — payments, refunds, webhook log | Architect | 2 | High |
| TASK-LLD-042 | Payment gateway integration design — session init, webhook flow, HMAC verify | Architect | 5 | **Urgent** |
| TASK-LLD-043 | Idempotency key design — transactionId deduplication on webhook | Architect | 3 | **Urgent** |
| TASK-LLD-044 | Payment TTL expiry design — 15-min timer, OrderFailed compensation | Architect | 3 | **Urgent** |
| TASK-LLD-045 | Refund flow design — partial and full, multi-refund tracking | Architect | 3 | High |
| TASK-LLD-046 | PCI-DSS compliance checklist — no card data, gateway tokenisation | Architect + RE | 2 | **Urgent** |
| TASK-LLD-047 | Sequence diagrams — initiate, authorise, capture, fail, refund | Architect | 3 | High |
| TASK-LLD-048 | LLD review and sign-off — Payment | Architect + RE | 3 | High |

#### EP-LLD-006: LLD — Inventory

| Task ID | Task | Role | Points | Priority |
|---|---|---|---|---|
| TASK-LLD-050 | Domain model — InventoryItem and StockReservation aggregates | Architect | 2 | High |
| TASK-LLD-051 | MySQL schema design — inventory_items, stock_reservations with version field | Architect | 3 | **Urgent** |
| TASK-LLD-052 | Optimistic locking design — version column, retry-on-conflict pattern | Architect | 3 | **Urgent** |
| TASK-LLD-053 | Reservation TTL scheduler — 15-min expiry job, StockReleased compensation | Architect | 3 | High |
| TASK-LLD-054 | Bulk availability check API design — used by Cart at checkout | Architect | 2 | Normal |
| TASK-LLD-055 | Low stock and stockout flow design — alert, unpublish cascade | Architect | 2 | Normal |
| TASK-LLD-056 | Sequence diagrams — reserve, commit, release, expire, replenish | Architect | 3 | High |
| TASK-LLD-057 | LLD review and sign-off — Inventory | Architect + RE | 3 | High |

#### EP-LLD-007: LLD — Notification

| Task ID | Task | Role | Points | Priority |
|---|---|---|---|---|
| TASK-LLD-060 | Domain model — Notification and NotificationPreference aggregates | Architect | 2 | Normal |
| TASK-LLD-061 | MySQL schema design — notifications, preferences, DLQ records | Architect | 2 | Normal |
| TASK-LLD-062 | Idempotency design — deduplication on (userId, sourceEventId, channel) | Architect | 3 | High |
| TASK-LLD-063 | Retry strategy — exponential back-off (1m, 5m, 15m), DLQ routing | Architect | 2 | High |
| TASK-LLD-064 | Template system design — versioned templates, personalisation tokens | Architect | 2 | Normal |
| TASK-LLD-065 | Sequence diagrams — dispatch, fail, retry, abandon, suppress | Architect | 2 | Normal |
| TASK-LLD-066 | LLD review and sign-off — Notification | Architect + RE | 3 | Normal |

---

## Phase 5 — Implementation (IMPL)

### Epics Summary

| Epic ID | Epic Name | List | Points Total |
|---|---|---|---|
| EP-IMPL-001 | Shared infrastructure setup | IMPL — Infrastructure | 21 |
| EP-IMPL-002 | User/Auth Service implementation | IMPL — User/Auth | 34 |
| EP-IMPL-003 | Product Catalog Service implementation | IMPL — Product Catalog | 34 |
| EP-IMPL-004 | Cart Service implementation | IMPL — Cart | 28 |
| EP-IMPL-005 | Order Service implementation | IMPL — Order | 55 |
| EP-IMPL-006 | Payment Service implementation | IMPL — Payment | 42 |
| EP-IMPL-007 | Inventory Service implementation | IMPL — Inventory | 34 |
| EP-IMPL-008 | Notification Service implementation | IMPL — Notification | 28 |

> **Task breakdown for IMPL phase:** Generated at LLD sign-off. IMPL tasks are derived 1-to-1 from LLD sequence diagrams. Do not create IMPL tasks until the corresponding LLD epic is DONE.

---

## Phase 6 — Testing (TEST)

### Epics Summary

| Epic ID | Epic Name | List | Points Total |
|---|---|---|---|
| EP-TEST-001 | Unit tests — all 7 services | TEST — Unit | 42 |
| EP-TEST-002 | Integration tests — per service | TEST — Integration | 42 |
| EP-TEST-003 | Contract tests — Kafka events + REST APIs | TEST — Contract | 28 |
| EP-TEST-004 | End-to-end tests — 5 critical user journeys | TEST — E2E | 28 |
| EP-TEST-005 | Performance and load testing | TEST — Performance | 21 |

#### E2E Test Coverage (EP-TEST-004)

| Journey | Bounded Contexts Spanned | Priority |
|---|---|---|
| Full order placement — happy path | Cart → Order → Payment → Inventory → Notification | Urgent |
| Order placement — payment failure compensation | Cart → Order → Payment → Inventory | Urgent |
| Order cancellation with refund | Order → Payment → Inventory → Notification | High |
| User registration and first purchase | User/Auth → Cart → Order → Payment | High |
| Inventory stockout → catalog unpublish | Inventory → Product Catalog → Notification | Normal |

---

## Phase 7 — CI/CD & DevOps (CICD)

### Epics Summary

| Epic ID | Epic Name | List | Points Total |
|---|---|---|---|
| EP-CICD-001 | Docker containerisation — all services | CICD — Containerisation | 21 |
| EP-CICD-002 | Kubernetes manifests and Helm charts | CICD — Kubernetes | 28 |
| EP-CICD-003 | CI pipeline — build, test, lint, security scan | CICD — Pipelines | 21 |
| EP-CICD-004 | CD pipeline — staging and production promotion | CICD — Pipelines | 21 |
| EP-CICD-005 | Observability stack — Prometheus, Grafana, Loki, OpenTelemetry | CICD — Observability | 21 |

---

## Phase 8 — AWS Serverless (AWS)

### Epics Summary

| Epic ID | Epic Name | List | Points Total |
|---|---|---|---|
| EP-AWS-001 | IaC — CDK or Terraform project setup | AWS — IaC | 13 |
| EP-AWS-002 | Cognito — auth migration from JWT to Cognito | AWS — User/Auth | 21 |
| EP-AWS-003 | Lambda + API Gateway — User/Auth | AWS — User/Auth | 21 |
| EP-AWS-004 | Lambda + API Gateway — Product Catalog | AWS — Product Catalog | 21 |
| EP-AWS-005 | Lambda + DynamoDB — Cart (single-table design) | AWS — Cart | 21 |
| EP-AWS-006 | Step Functions — Order + Payment saga orchestration | AWS — Order + Payment | 34 |
| EP-AWS-007 | Lambda + DynamoDB — Inventory | AWS — Inventory | 21 |
| EP-AWS-008 | SQS + SNS + EventBridge — Notification | AWS — Notification | 21 |

> **Phase comparison gate:** Each AWS epic must include a comparison document (`docs/lld/[context]-phase-comparison.md`) contrasting the Phase 1 (containerised) and Phase 2 (serverless) design decisions. This is the primary architect learning output.

---

## Sprint Planning Guide

### Recommended Sprint Allocation

| Sprint | Phase | Focus |
|---|---|---|
| SP-00 | SETUP | Board setup, tooling, risk register |
| SP-01 | RE | Event storming, FRs, NFRs |
| SP-02 | RE | User stories, acceptance criteria, API stubs ← **DONE** |
| SP-03 | HLD | C4 diagrams, ADRs 1–5 |
| SP-04 | HLD | ADRs 6–10, deployment architecture |
| SP-05 | LLD | User/Auth, Product Catalog |
| SP-06 | LLD | Cart, Order (first half) |
| SP-07 | LLD | Order (second half), Payment |
| SP-08 | LLD | Inventory, Notification |
| SP-09–14 | IMPL | One service per sprint (User/Auth → Notification) |
| SP-15–16 | TEST | Contract + E2E tests |
| SP-17 | CICD | Pipelines, Docker, K8s |
| SP-18 | CICD | Observability, staging deploy |
| SP-19–22 | AWS | AWS migration per context pair |

### Velocity Assumption

- Solo developer with architect hat: 15–20 points/sprint
- Adjust velocity after SP-03 retrospective with actual data

---

## Point Totals by Phase

| Phase | Epic Count | Estimated Points |
|---|---|---|
| SETUP | 4 | 21 |
| RE | 7 | 111 ✅ DONE |
| HLD | 8 | 106 |
| LLD | 7 | 146 |
| IMPL | 8 | 276 |
| TEST | 5 | 161 |
| CICD | 5 | 112 |
| AWS | 8 | 173 |
| **Total** | **52** | **~1,106** |

> At 18 points/sprint average: approximately **61 sprints (~2.3 years)** for a solo developer.
> This is a portfolio project — velocity is secondary to quality of artefacts.

---

## Output Checklist — Board Design

- [x] Space name and structure defined
- [x] 8 phases with Folders and Lists
- [x] All 52 epics defined with IDs and artefact paths
- [x] Full task breakdown for Phase 3 HLD (55 tasks with points and priorities)
- [x] Full task breakdown for Phase 4 LLD (58 tasks with points and priorities)
- [x] Phase 1 SETUP tasks defined (current status reflected)
- [x] Custom fields specified (Phase, Bounded Context, Role, Points, Sprint, Risk Link, Artefact Path, Depends On)
- [x] Status workflow defined
- [x] Sprint allocation guide produced
- [x] Point totals and timeline estimate provided
- [x] E2E test journey coverage mapped

---

## Open Questions

| # | Question | Owner | Impact |
|---|---|---|---|
| OQ-PM-1 | Will IMPL tasks be created before or after LLD sign-off? Recommend: after | PM + Architect | Sprint planning for SP-09 |
| OQ-PM-2 | Is AWS phase a full rewrite or selective migration? Affects EP-AWS-* scope | Architect | Points estimate for Phase 8 |
| OQ-PM-3 | Should Product Catalog use Elasticsearch (external) or MySQL full-text search? ADR needed | Architect | TASK-LLD-012 depends on this |
| OQ-PM-4 | Solo developer or team? Velocity assumption drives sprint count significantly | PM | Roadmap |

## Next Recommended Action

1. **Immediate:** Run `TASK-SETUP-010` to `TASK-SETUP-032` — create the ClickUp board from this document.
2. **This sprint (SP-03):** Begin HLD epics — start with `TASK-HLD-040` (ADR-001, monetary precision) and `TASK-HLD-041` (ADR-002, stock reservation) as both are Urgent blockers for LLD.
3. **Risk register:** Seed RISK-001 to RISK-008 from event-storming.md hotspots H-CT-1, H-CT-4, H-OR-1, H-OR-2, H-PM-1, H-PM-3, H-IN-1, H-IN-2, H-NT-1 before any IMPL task goes READY.
