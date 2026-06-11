# Ecommerce Platform — Architect Journey Workflow

> Owner: Mino | Goal: Software Architect role preparation
> Stack: Java Spring Boot (Phase 1) · AWS Serverless (Phase 2)
> Started: June 2026

---

## How This Project Works

### Virtual Team Model
Each phase is driven by a **role skill** loaded into Claude Code.
Every role reads its skill file first, then produces artifacts to disk.

```
Claude Code session
  → Read skills/roles/[role].md
  → Act as that role
  → Produce artifacts → save to docs/
  → Commit with [ROLE] prefix
  → Push to GitHub
  → Move to next role/phase
```

### Three-Way Link — ClickUp + GitHub + Artifacts
Every piece of work must be traceable across all three:

```
ClickUp Task created (PM role)
       ↓
GitHub branch opened  (name matches task)
       ↓
Work done → artifacts saved to docs/
       ↓
PR opened → merged to develop
       ↓
ClickUp task → marked Done
       ↓
Phase complete → PR develop → main → git tag
```

**Rule:** No branch opens without a ClickUp task.
**Rule:** No ClickUp task closes without a merged PR.
**Rule:** No phase releases without all tasks in that list marked Done.

---

## ClickUp Board

### Board Location
```
Workspace:  Work Partition (ID: 90070152708)
Folder:     Ecommerce Platform (ID: 901814488482)
URL:        https://app.clickup.com/31085602/v/f/901814488482
```

### Phase Lists

| List | ID | Phase | Status |
|------|----|-------|--------|
| Phase-1-RE | 901818617887 | Requirements Engineering | ✅ Complete |
| Phase-2-SA | 901818617888 | System Architecture | ⏳ Current |
| Phase-3-DEV | 901818617889 | Java Implementation | ⬜ |
| Phase-4-QA | 901818617891 | Testing | ⬜ |
| Phase-5-CICD | 901818617892 | CI/CD & DevOps | ⬜ |
| Phase-6-AWS | 901818617893 | AWS Serverless | ⬜ |

### Task Structure Per Phase

```
Phase List
  └── Epic (group of related tasks)
        └── Task (one unit of work = one GitHub branch)
              ├── Description
              ├── Role (who does it)
              ├── GitHub branch
              ├── Linked artifact path
              └── Status: To Do → In Progress → In Review → Done
```

### Custom Fields to Set on Every Task

| Field | Values |
|-------|--------|
| Role | [RE] [SA] [PM] [DEV] [QA] [OPS] [AWS] |
| Phase | Phase 1 through Phase 6 |
| Bounded Context | Auth / Catalog / Cart / Order / Payment / Inventory / Notification / Infrastructure |
| GitHub Branch | feature/[ROLE]-[task-name] |
| Artifact Path | docs/[folder]/[filename] |

### PM Workflow Per Sprint

```
1. PM opens Claude Code
2. Read skills/roles/project-manager.md
3. Create tasks in ClickUp for the next sprint
4. Each task gets: name, role, branch name, artifact path
5. Developer picks task → opens GitHub branch
6. Work done → PR merged → task marked Done
7. PM posts sprint summary to Slack
8. PM schedules next sprint on Google Calendar
```

---

## Branching Strategy

```
main          ← production-ready, protected, PR only
develop       ← integration branch, protected, default
feature/RE-*  ← Requirement Engineering
feature/SA-*  ← System Architecture
feature/PM-*  ← Project Management
feature/DEV-* ← Service Implementation
feature/QA-*  ← Testing Artifacts
feature/OPS-* ← DevOps / CI-CD
feature/AWS-* ← Phase 2 AWS Serverless
release/*     ← release candidates → merges to main
hotfix/*      ← emergency fixes
```

### PR Rules

| From | To | Strategy |
|------|----|----------|
| `feature/*` | `develop` | Squash and merge |
| `release/*` | `main` | Merge commit |
| `hotfix/*` | `main` + `develop` | Merge commit |

---

## Commit Convention

```
[INIT]   project initialization
[SKILL]  skill/role file changes
[RE]     Requirements Engineer output
[SA]     System Architect output
[PM]     Project Manager output
[DEV]    Backend Developer output
[QA]     QA Engineer output
[OPS]    DevOps Engineer output
[AWS]    AWS Architect output
[FIX]    Bug fix
[RELEASE] Release commits
```

---

## Skill & Role Files

### Technique Skills (reusable across roles)

| File | Purpose | Status |
|------|---------|--------|
| `skills/techniques/agile-docs.md` | User story, AC, NFR, ADR formats | ✅ Done |
| `skills/techniques/system-design.md` | HLD, LLD, C4, draw.io standards | ✅ Done |
| `skills/techniques/design-patterns.md` | Architectural pattern catalogue with rationale | ✅ Done |
| `skills/techniques/java-springboot.md` | Project structure, patterns, conventions | ⬜ |
| `skills/techniques/docker-k8s.md` | Dockerfile, Helm, K8s manifest patterns | ⬜ |
| `skills/techniques/aws-serverless.md` | CDK, Lambda, DynamoDB, Step Functions | ⬜ |
| `skills/techniques/testing-standards.md` | JUnit, Testcontainers, Pact, k6 | ⬜ |
| `skills/techniques/cicd-patterns.md` | GitHub Actions, ArgoCD, pipeline stages | ⬜ |

### Role Skills (virtual team members)

| File | Phase | Produces | Status |
|------|-------|---------|--------|
| `skills/roles/requirements-engineer.md` | Both | RE docs, user stories, AC, OpenAPI stubs | ✅ Done |
| `skills/roles/project-manager.md` | Both | Sprint plans, ClickUp tasks, Slack updates | ✅ Done |
| `skills/roles/system-architect.md` | Both | HLD, LLD, C4 diagrams, ADRs | ✅ Done |
| `skills/roles/backend-developer.md` | Phase 1 | Spring Boot services, tests, Kafka | ⬜ |
| `skills/roles/devops-engineer.md` | Both | Dockerfiles, Helm, GitHub Actions, ArgoCD | ⬜ |
| `skills/roles/aws-architect.md` | Phase 2 | CDK stacks, Lambda, DynamoDB design | ⬜ |
| `skills/roles/qa-engineer.md` | Both | Test strategy, Pact contracts, k6 scripts | ⬜ |

### Plugins Connected

| Plugin | Status | Used By | Purpose |
|--------|--------|---------|---------|
| ClickUp MCP | ✅ Connected | PM, RE, QA | Task creation, sprint tracking |
| Slack MCP | ✅ Connected | PM, DevOps | Sprint summaries, alerts |
| Google Drive MCP | ✅ Connected | All roles | Docs storage |
| draw.io MCP | ✅ Connected | Architect, Dev | Diagrams |
| Gmail MCP | ✅ Connected | PM, RE | Stakeholder comms |
| Google Calendar MCP | ✅ Connected | PM | Sprint ceremonies |
| GitHub MCP | ⏳ Pending | Dev, DevOps, AWS | Code, PRs, branches |
| Filesystem MCP | ✅ Via Claude Code | Dev, DevOps, AWS | Local file read/write |

---

## Phase Progress

### ✅ Phase 0 — Project Setup
**Branch:** `main`
**Status:** Complete

- [x] Project vision and roadmap defined
- [x] Virtual team structure designed (7 skills, 7 roles, 8 plugins)
- [x] Claude Code v2.1.126 installed on Linux (HP EliteBook 840 G5)
- [x] Project folder structure created
- [x] CLAUDE.md created (project memory)
- [x] WORKFLOW.md created (this file)
- [x] GITHUB_BEST_PRACTICES.md created
- [x] GitHub repo created (`ecommerce-platform`)
- [x] `main` branch protected (PR required, no force push, no bypass)
- [x] `develop` branch created, protected, set as default
- [x] GitFlow branching strategy established
- [x] ClickUp board created (Work Partition → Ecommerce Platform)
- [x] All 6 phase lists created in ClickUp

---

### ✅ Phase 1 — Requirement Engineering
**Branch:** `feature/RE-*` → merged to `develop` → `release/phase-1-requirements` → `main`
**Role:** `skills/roles/requirements-engineer.md`
**ClickUp List:** Phase-1-RE (ID: 901818617887)
**Git Tag:** `v0.1.0`
**Status:** Complete ✅

#### Artifacts Produced

| Artifact | Path | ClickUp Task | Status |
|----------|------|--------------|--------|
| Event storming | `docs/requirements/event-storming.md` | — | ✅ Done |
| Functional requirements | `docs/requirements/functional-requirements.md` | — | ✅ Done |
| Non-functional requirements | `docs/requirements/non-functional-requirements.md` | — | ✅ Done |
| User stories | `docs/requirements/user-stories.md` | — | ✅ Done |
| Acceptance criteria | `docs/requirements/acceptance-criteria.md` | — | ✅ Done |
| OpenAPI stubs (7 services) | `docs/api-specs/[service]-api.yaml` | — | ✅ Done |
| Use case diagrams (7 contexts) | `docs/requirements/use-cases/[context]-use-cases.md` | — | ✅ Done |

#### Bounded Contexts Covered
- [x] User & Auth
- [x] Product Catalog
- [x] Cart & Session
- [x] Order Management
- [x] Payment Processing
- [x] Inventory Management
- [x] Notification Service

---

### ⏳ Phase 2 — System Architecture
**Branch:** `feature/SA-*` → `develop`
**Role:** `skills/roles/system-architect.md`
**ClickUp List:** Phase-2-SA (ID: 901818617888)
**Git Tag:** `v0.2.0` (on completion)
**Status:** In Progress ⏳

#### Artifacts to Produce

| Artifact | Path | ClickUp Task | Branch | Status |
|----------|------|--------------|--------|--------|
| C4 Level 1 — System context | `docs/hld/system-context.md` | SA-001 | `feature/SA-001-system-context-diagram` | ✅ Done |
| C4 Level 2 — Container diagram | `docs/hld/container-diagram.md` | SA-002 | `feature/SA-002-container-diagram` | ✅ Done |
| C4 Level 3 — Component diagrams | `docs/hld/component-diagrams.md` | SA-003 | merged (#11) | ✅ Done |
| Sequence diagrams | `docs/hld/sequence-diagrams.md` | SA-004 | merged (#12) | ✅ Done |
| ER diagrams | `docs/hld/er-diagrams.md` | SA-005 | merged (#13) | ✅ Done |
| Order state machine | `docs/hld/order-state-machine.md` | SA-006 | merged (#14) | ✅ Done |
| ADR-0001: Monetary precision (integer minor units) | `docs/adr/ADR-0001-monetary-precision.md` | SA-007 | `adr/0001-0002-monetary-and-kafka-topics` | ✅ Done |
| ADR-0002: Kafka topic design and partitioning | `docs/adr/ADR-0002-kafka-topic-partitioning.md` | SA-008 | `adr/0001-0002-monetary-and-kafka-topics` | ✅ Done |
| ADR-0006: Microservices over monolith | `docs/adr/ADR-0006-microservices-vs-monolith.md` | SA-009 | merged (#15/#16) | ✅ Done |
| ADR-0007: Kafka over RabbitMQ | `docs/adr/ADR-0007-kafka-vs-rabbitmq.md` | SA-009 | merged (#15/#16) | ✅ Done |
| ADR-0008: Database-per-service | `docs/adr/ADR-0008-database-per-service.md` | SA-009 | merged (#15/#16) | ✅ Done |
| ADR-0009: Payment idempotency | `docs/adr/ADR-0009-payment-idempotency.md` | SA-010 | merged (#15/#16) | ✅ Done |
| ADR-0010: Cart storage (Redis vs DB) | `docs/adr/ADR-0010-cart-storage.md` | SA-011 | merged (#15/#16) | ✅ Done |
| ADR-0011: JWT strategy | `docs/adr/ADR-0011-jwt-strategy.md` | SA-011 | merged (#15/#16) | ✅ Done |
| ADR-0012: Notification delivery guarantee | `docs/adr/ADR-0012-notification-delivery-guarantee.md` | SA-011 | merged (#15/#16) | ✅ Done |
| ADR-0013: Catalog search strategy | `docs/adr/ADR-0013-catalog-search-strategy.md` | SA-011 | merged (#15/#16) | ✅ Done |
| LLD — User/Auth | `docs/lld/user-auth-lld.md` | SA-012 | `feature/SA-012-lld-user-auth` | ⬜ |
| LLD — Product Catalog | `docs/lld/product-catalog-lld.md` | SA-013 | `feature/SA-013-lld-catalog` | ⬜ |
| LLD — Cart | `docs/lld/cart-lld.md` | SA-014 | `feature/SA-014-lld-cart` | ⬜ |
| LLD — Order | `docs/lld/order-lld.md` | SA-015 | `feature/SA-015-lld-order` | ✅ Done |
| LLD — Payment | `docs/lld/payment-lld.md` | SA-016 | `feature/SA-016-lld-payment` | ✅ Done |
| LLD — Inventory | `docs/lld/inventory-lld.md` | SA-017 | `feature/SA-017-lld-inventory` | ⬜ |
| LLD — Notification | `docs/lld/notification-lld.md` | SA-018 | `feature/SA-018-lld-notification` | ⬜ |

> **Note:** ADR numbers 0003–0005 were not used — ADR-0001/0002 (monetary precision, Kafka topics) and ADR-0006–0013 (8 cross-cutting decisions) cover the SA-007–SA-011 scope. Next up: LLDs (SA-012 onward), starting with Order (`docs/lld/order-lld.md`) per `container-diagram.md` §13.

---

### ⬜ Phase 3 — Java Microservices Implementation
**Branch:** `feature/DEV-[service]-*`
**Role:** `skills/roles/backend-developer.md`
**ClickUp List:** Phase-3-DEV (ID: 901818617889)
**Git Tag:** `v0.3.0` (on completion)
**Status:** Not started

#### Services to Build (in order)

| Service | Branch | ClickUp Epic | Status |
|---------|--------|--------------|--------|
| User & Auth Service | `feature/DEV-001-auth-service` | DEV-EPIC-001 | ⬜ |
| Product Catalog Service | `feature/DEV-002-catalog-service` | DEV-EPIC-002 | ⬜ |
| Cart & Session Service | `feature/DEV-003-cart-service` | DEV-EPIC-003 | ⬜ |
| Order Service | `feature/DEV-004-order-service` | DEV-EPIC-004 | ⬜ |
| Payment Service | `feature/DEV-005-payment-service` | DEV-EPIC-005 | ⬜ |
| Inventory Service | `feature/DEV-006-inventory-service` | DEV-EPIC-006 | ⬜ |
| Notification Service | `feature/DEV-007-notification-service` | DEV-EPIC-007 | ⬜ |
| API Gateway | `feature/DEV-008-api-gateway` | DEV-EPIC-008 | ⬜ |

#### Tech Stack
```
Language:    Java 17
Framework:   Spring Boot 3
Database:    MySQL 8 (per service)
Cache:       Redis 7
Messaging:   Apache Kafka
Resilience:  Resilience4j
Testing:     JUnit 5 + Mockito + Testcontainers
```

---

### ⬜ Phase 4 — Testing Strategy
**Branch:** `feature/QA-*`
**Role:** `skills/roles/qa-engineer.md`
**ClickUp List:** Phase-4-QA (ID: 901818617891)
**Git Tag:** `v0.4.0` (on completion)
**Status:** Not started

| Test Type | Tool | ClickUp Task | Status |
|-----------|------|--------------|--------|
| Unit tests | JUnit 5 + Mockito | QA-001 | ⬜ |
| Integration tests | Testcontainers | QA-002 | ⬜ |
| Contract tests | Pact | QA-003 | ⬜ |
| Static analysis | SonarQube | QA-004 | ⬜ |
| Load tests | k6 / Gatling | QA-005 | ⬜ |
| Security scan | OWASP + Trivy | QA-006 | ⬜ |

---

### ⬜ Phase 5 — CI/CD Pipeline & DevOps
**Branch:** `feature/OPS-*`
**Role:** `skills/roles/devops-engineer.md`
**ClickUp List:** Phase-5-CICD (ID: 901818617892)
**Git Tag:** `v0.5.0` (on completion)
**Status:** Not started

| Artifact | Tool | ClickUp Task | Status |
|----------|------|--------------|--------|
| Docker multi-stage builds | Docker | OPS-001 | ⬜ |
| Helm charts per service | Helm 3 | OPS-002 | ⬜ |
| K8s manifests | Kubernetes | OPS-003 | ⬜ |
| CI pipeline | GitHub Actions | OPS-004 | ⬜ |
| CD pipeline | ArgoCD | OPS-005 | ⬜ |
| Monitoring | Prometheus + Grafana | OPS-006 | ⬜ |
| Logging | ELK Stack | OPS-007 | ⬜ |
| Tracing | Jaeger | OPS-008 | ⬜ |

#### CI Pipeline Stages
```
build → unit-test → integration-test → sonarqube →
docker-build → trivy-scan → push-to-registry → deploy-staging → smoke-test
```

---

### ⬜ Phase 6 — AWS Serverless
**Branch:** `feature/AWS-*`
**Role:** `skills/roles/aws-architect.md`
**ClickUp List:** Phase-6-AWS (ID: 901818617893)
**Git Tag:** `v0.6.0` (on completion)
**Status:** Not started

| AWS Service | Maps To (Phase 1) | ClickUp Task | Status |
|-------------|-------------------|--------------|--------|
| Cognito | Spring Security + JWT | AWS-001 | ⬜ |
| API Gateway + Lambda | Spring Boot + K8s | AWS-002 | ⬜ |
| DynamoDB | MySQL per service | AWS-003 | ⬜ |
| DAX | Redis | AWS-004 | ⬜ |
| SQS + SNS | Kafka | AWS-005 | ⬜ |
| Step Functions | Saga orchestration | AWS-006 | ⬜ |
| EventBridge | Kafka topics | AWS-007 | ⬜ |
| S3 + CloudFront | Static assets | AWS-008 | ⬜ |
| CDK (Java) | Helm + K8s manifests | AWS-009 | ⬜ |
| X-Ray | Jaeger | AWS-010 | ⬜ |

---

### ⬜ Phase 7 — Observability & Production Hardening
**Branch:** `feature/OPS-observability`
**Git Tag:** `v0.9.0` (on completion)
**Status:** Not started

- [ ] SLI/SLO definitions per service
- [ ] Grafana dashboards
- [ ] Alert rules (Slack notifications)
- [ ] Runbooks per alert
- [ ] Chaos engineering basics
- [ ] AWS X-Ray traces (Phase 2)
- [ ] Cost optimization report (Phase 2)

---

### ⬜ Phase 8 — Portfolio & Comparison Write-up
**Branch:** `main` (final merge)
**Git Tag:** `v1.0.0`
**Status:** Not started

- [ ] Architecture comparison: K8s microservices vs AWS Serverless
- [ ] Trade-off analysis document
- [ ] Load test results comparison
- [ ] Cost analysis
- [ ] README.md (public-facing project summary)
- [ ] Architecture blog post draft
- [ ] Interview prep: key design decisions

---

## Release History

| Version | Phase | Date | Status |
|---------|-------|------|--------|
| v0.0.1 | Project initialized | June 2026 | ✅ |
| v0.1.0 | Phase 1: Requirements complete | June 2026 | ✅ |
| v0.2.0 | Phase 2: Architecture complete | — | ⬜ |
| v0.3.0 | Phase 3: Implementation complete | — | ⬜ |
| v0.4.0 | Phase 4: Testing complete | — | ⬜ |
| v0.5.0 | Phase 5: CI/CD complete | — | ⬜ |
| v0.6.0 | Phase 6: AWS Serverless complete | — | ⬜ |
| v0.9.0 | Phase 7: Production hardening | — | ⬜ |
| v1.0.0 | Phase 8: Full system complete | — | ⬜ |

---

## Folder Structure Reference

```
ecommerce-platform/
├── CLAUDE.md                          ← project memory for Claude Code
├── WORKFLOW.md                        ← this file
├── GITHUB_BEST_PRACTICES.md           ← Git/GitHub standards
├── .gitignore
├── skills/
│   ├── roles/
│   │   ├── requirements-engineer.md  ✅
│   │   ├── project-manager.md        ✅
│   │   ├── system-architect.md       ✅
│   │   ├── backend-developer.md      ⬜
│   │   ├── devops-engineer.md        ⬜
│   │   ├── aws-architect.md          ⬜
│   │   └── qa-engineer.md            ⬜
│   └── techniques/
│       ├── agile-docs.md             ✅
│       ├── system-design.md          ✅
│       ├── design-patterns.md        ✅
│       ├── java-springboot.md        ⬜
│       ├── docker-k8s.md             ⬜
│       ├── aws-serverless.md         ⬜
│       ├── testing-standards.md      ⬜
│       └── cicd-patterns.md          ⬜
├── docs/
│   ├── requirements/                 ✅ Complete (event-storming, FRs, NFRs, user-stories, AC, 7 use-cases)
│   ├── api-specs/                    ✅ Complete (7 OpenAPI stubs)
│   ├── project-management/           ✅ Complete (clickup-board-structure.md)
│   ├── hld/                          ✅ Complete (system-context, container, component, sequence, ER, order-state-machine)
│   ├── lld/                          ⬜ (next: order-lld.md)
│   └── adr/                          ✅ ADR-0001, 0002, 0006–0013 (10 ADRs)
├── phase1/
│   ├── user-service/                 ⬜
│   ├── catalog-service/              ⬜
│   ├── cart-service/                 ⬜
│   ├── order-service/                ⬜
│   ├── payment-service/              ⬜
│   ├── inventory-service/            ⬜
│   ├── notification-service/         ⬜
│   ├── api-gateway/                  ⬜
│   └── infrastructure/
│       ├── docker/                   ⬜
│       ├── kubernetes/               ⬜
│       └── helm/                     ⬜
└── phase2/
    ├── functions/                    ⬜
    └── infrastructure/
        └── cdk/                      ⬜
```

---

## Quick Command Reference

### Start new work
```bash
git checkout develop && git pull origin develop
git checkout -b feature/[ROLE]-[task]
git push origin feature/[ROLE]-[task]
# Mark ClickUp task → In Progress
```

### Activate a role in Claude Code
```
Read skills/roles/[role].md then act as that role.
Input: [artifacts you are consuming]
Task:  [what to produce]
Save to: [file path]
```

### Finish work
```bash
git add docs/[relevant folder]/
git commit -m "[ROLE] scope: description"
git push origin feature/[ROLE]-[task]
# Open PR on GitHub → develop
# After merge: mark ClickUp task → Done
```

### Release a phase
```bash
git checkout develop && git pull origin develop
git checkout -b release/phase-[N]-[name]
git push origin release/phase-[N]-[name]
# Open PR on GitHub → main (merge commit)
git checkout main && git pull origin main
git tag -a v0.N.0 -m "Phase N complete: [name]"
git push origin v0.N.0
git checkout develop && git merge main && git push origin develop
git branch -d release/phase-[N]-[name]
git push origin --delete release/phase-[N]-[name]
```

### Update CLAUDE.md after each phase
After completing a phase, update the Completed Artifacts
checklist in CLAUDE.md to keep Claude Code context current.

---

## ID Reference

| Artifact | Format | Example |
|----------|--------|---------|
| Functional requirement | FR-[CONTEXT]-001 | FR-ORDER-001 |
| Non-functional requirement | NFR-[CONTEXT]-001 | NFR-ORDER-001 |
| User story | US-[CONTEXT]-001 | US-ORDER-001 |
| Acceptance criteria | AC-[US-ID]-001 | AC-US-ORDER-001-001 |
| Architecture decision | ADR-NNNN (4-digit) | ADR-0001 |
| Domain event | PascalCase past tense | OrderPlaced |
| ClickUp task | [ROLE]-[number] | SA-001 |

---

*Last updated: 2026-06-08 — Phase 1 (RE) complete, Phase 2 (SA) in progress (SA-001 system-context, SA-002 container-diagram done — PRs open)*
