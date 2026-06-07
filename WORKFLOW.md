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

### Branching Strategy
```
main          ← production-ready, protected, PR only
develop       ← integration branch, protected
feature/RE-*  ← Requirement Engineering
feature/SA-*  ← System Architecture
feature/PM-*  ← Project Management
feature/DEV-* ← Service Implementation
feature/QA-*  ← Testing Artifacts
feature/OPS-* ← DevOps / CI-CD
feature/AWS-* ← Phase 2 AWS Serverless
release/*     ← release candidates
hotfix/*      ← emergency fixes
```

### Commit Convention
```
[INIT]  project: description    → project initialization
[SKILL] scope: description      → skill/role file changes
[RE]    scope: description      → Requirements Engineer output
[SA]    scope: description      → System Architect output
[PM]    scope: description      → Project Manager output
[DEV]   scope: description      → Backend Developer output
[QA]    scope: description      → QA Engineer output
[OPS]   scope: description      → DevOps Engineer output
[AWS]   scope: description      → AWS Architect output
```

---

## Skill & Role Files

### Technique Skills (reusable across roles)
| File | Purpose |
|------|---------|
| `skills/techniques/agile-docs.md` | User story, AC, NFR, ADR formats |
| `skills/techniques/system-design.md` | HLD, LLD, C4, draw.io standards |
| `skills/techniques/java-springboot.md` | Project structure, patterns, conventions |
| `skills/techniques/docker-k8s.md` | Dockerfile, Helm, K8s manifest patterns |
| `skills/techniques/aws-serverless.md` | CDK, Lambda, DynamoDB, Step Functions |
| `skills/techniques/testing-standards.md` | JUnit, Testcontainers, Pact, k6 |
| `skills/techniques/cicd-patterns.md` | GitHub Actions, ArgoCD, pipeline stages |

### Role Skills (virtual team members)
| File | Phase | Produces |
|------|-------|---------|
| `skills/roles/requirements-engineer.md` | Both | RE docs, user stories, AC, OpenAPI stubs |
| `skills/roles/project-manager.md` | Both | Sprint plans, risk register, status reports |
| `skills/roles/system-architect.md` | Both | HLD, LLD, C4 diagrams, ADRs |
| `skills/roles/backend-developer.md` | Phase 1 | Spring Boot services, tests, Kafka |
| `skills/roles/devops-engineer.md` | Both | Dockerfiles, Helm, GitHub Actions, ArgoCD |
| `skills/roles/aws-architect.md` | Phase 2 | CDK stacks, Lambda, DynamoDB design |
| `skills/roles/qa-engineer.md` | Both | Test strategy, Pact contracts, k6 scripts |

### Plugins Connected
| Plugin | Status | Used By |
|--------|--------|---------|
| ClickUp MCP | ✅ Connected | PM, RE, QA |
| Slack MCP | ✅ Connected | PM, DevOps |
| Google Drive MCP | ✅ Connected | All roles |
| draw.io MCP | ✅ Connected | Architect, Dev |
| Gmail MCP | ✅ Connected | PM, RE |
| Google Calendar MCP | ✅ Connected | PM |
| GitHub MCP | ⏳ Pending | Dev, DevOps, AWS |
| Filesystem MCP | ✅ Via Claude Code | Dev, DevOps, AWS |

---

## Phase Progress

### ✅ Phase 0 — Project Setup
**Branch:** `main`
**Status:** Complete

- [x] Project vision and roadmap defined
- [x] Virtual team structure designed (7 skills, 7 roles, 8 plugins)
- [x] Claude Code v2.1.126 installed on Linux
- [x] Project folder structure created
- [x] CLAUDE.md created (project memory)
- [x] GitHub repo created (`ecommerce-platform`)
- [x] `main` branch protected (PR required, no force push, no bypass)
- [x] `develop` branch created and protected
- [x] GitFlow branching strategy established

---

### ✅ Phase 1 — Requirement Engineering
**Branch:** `feature/RE-*` → merged to `develop`
**Role:** `skills/roles/requirements-engineer.md`
**Status:** Complete

#### Artifacts Produced
| Artifact | Path | Status |
|----------|------|--------|
| Event storming | `docs/requirements/event-storming.md` | ✅ Done |
| Functional requirements | `docs/requirements/functional-requirements.md` | ✅ Done |
| Non-functional requirements | `docs/requirements/non-functional-requirements.md` | ✅ Done |
| User stories | `docs/requirements/user-stories.md` | ✅ Done |
| Acceptance criteria | `docs/requirements/acceptance-criteria.md` | ✅ Done |
| OpenAPI stubs | `docs/api-specs/[service]-api.yaml` | ✅ Done |

#### Bounded Contexts Covered
- [x] User & Auth
- [x] Product Catalog
- [x] Cart & Session
- [x] Order Management
- [x] Payment Processing
- [x] Inventory Management
- [x] Notification Service

#### How to Resume This Phase
```
Read skills/roles/requirements-engineer.md then act as that role.
Input: docs/requirements/event-storming.md
Task: [describe what needs updating]
```

---

### ⏳ Phase 2 — System Architecture
**Branch:** `feature/SA-001-hld`
**Role:** `skills/roles/system-architect.md`
**Status:** Not started

#### Artifacts to Produce
| Artifact | Path | Status |
|----------|------|--------|
| C4 Level 1 — System context | `docs/hld/c4-system-context.drawio` | ⬜ |
| C4 Level 2 — Container diagram | `docs/hld/c4-container.drawio` | ⬜ |
| C4 Level 3 — Component diagrams | `docs/hld/c4-components/[service].drawio` | ⬜ |
| Sequence diagrams | `docs/lld/sequences/[flow].drawio` | ⬜ |
| ER diagrams | `docs/lld/er-diagrams/[service].drawio` | ⬜ |
| State machine — Order lifecycle | `docs/lld/state-machines/order-states.drawio` | ⬜ |
| ADR-001: Why microservices | `docs/adr/ADR-001-microservices.md` | ⬜ |
| ADR-002: Why Kafka over RabbitMQ | `docs/adr/ADR-002-kafka.md` | ⬜ |
| ADR-003: Database per service | `docs/adr/ADR-003-db-per-service.md` | ⬜ |
| ADR-004: Sync vs async comms | `docs/adr/ADR-004-communication.md` | ⬜ |
| ADR-005: Saga pattern for orders | `docs/adr/ADR-005-saga-pattern.md` | ⬜ |

#### How to Start
```bash
git checkout develop
git checkout -b feature/SA-001-hld
```
```
Read skills/roles/system-architect.md then act as that role.
Input: docs/requirements/ (all RE artifacts)
Task: Create C4 Level 1 system context diagram for the
      ecommerce platform. Save to docs/hld/c4-system-context.drawio
```

---

### ⬜ Phase 3 — Phase 1 Implementation (Java Microservices)
**Branch:** `feature/DEV-[service]-*`
**Role:** `skills/roles/backend-developer.md`
**Status:** Not started

#### Services to Build (in order)
| Service | Branch | Status |
|---------|--------|--------|
| User & Auth Service | `feature/DEV-auth-service` | ⬜ |
| Product Catalog Service | `feature/DEV-catalog-service` | ⬜ |
| Cart & Session Service | `feature/DEV-cart-service` | ⬜ |
| Order Service | `feature/DEV-order-service` | ⬜ |
| Payment Service | `feature/DEV-payment-service` | ⬜ |
| Inventory Service | `feature/DEV-inventory-service` | ⬜ |
| Notification Service | `feature/DEV-notification-service` | ⬜ |
| API Gateway | `feature/DEV-api-gateway` | ⬜ |

#### Tech Stack Per Service
```
Language:    Java 17
Framework:   Spring Boot 3
Database:    MySQL 8 (per service)
Cache:       Redis 7
Messaging:   Apache Kafka
Resilience:  Resilience4j (circuit breaker, retry, bulkhead)
Testing:     JUnit 5 + Mockito + Testcontainers
```

---

### ⬜ Phase 4 — Testing Strategy
**Branch:** `feature/QA-*`
**Role:** `skills/roles/qa-engineer.md`
**Status:** Not started

| Test Type | Tool | Status |
|-----------|------|--------|
| Unit tests | JUnit 5 + Mockito | ⬜ |
| Integration tests | Testcontainers | ⬜ |
| Contract tests | Pact | ⬜ |
| Static analysis | SonarQube | ⬜ |
| Load tests | k6 / Gatling | ⬜ |
| Security scan | OWASP Dependency-Check + Trivy | ⬜ |

---

### ⬜ Phase 5 — CI/CD Pipeline
**Branch:** `feature/OPS-cicd-*`
**Role:** `skills/roles/devops-engineer.md`
**Status:** Not started

| Artifact | Tool | Status |
|----------|------|--------|
| Docker multi-stage builds | Docker | ⬜ |
| Helm charts per service | Helm 3 | ⬜ |
| K8s manifests | Kubernetes | ⬜ |
| CI pipeline | GitHub Actions | ⬜ |
| CD pipeline | ArgoCD | ⬜ |
| Monitoring | Prometheus + Grafana | ⬜ |
| Logging | ELK Stack | ⬜ |
| Tracing | Jaeger | ⬜ |

#### CI Pipeline Stages
```
build → unit-test → integration-test → sonarqube → 
docker-build → trivy-scan → push-to-registry → deploy-staging → smoke-test
```

---

### ⬜ Phase 6 — Phase 2 AWS Serverless
**Branch:** `feature/AWS-*`
**Role:** `skills/roles/aws-architect.md`
**Status:** Not started

| AWS Service | Maps To (Phase 1) | Status |
|-------------|-------------------|--------|
| Cognito | Spring Security + JWT | ⬜ |
| API Gateway + Lambda | Spring Boot + K8s | ⬜ |
| DynamoDB | MySQL per service | ⬜ |
| DAX | Redis | ⬜ |
| SQS + SNS | Kafka | ⬜ |
| Step Functions | Saga orchestration | ⬜ |
| EventBridge | Kafka topics | ⬜ |
| S3 + CloudFront | Static assets | ⬜ |
| CDK (Java) | Helm + K8s manifests | ⬜ |
| X-Ray | Jaeger | ⬜ |

---

### ⬜ Phase 7 — Observability & Production Hardening
**Branch:** `feature/OPS-observability`
**Status:** Not started

- [ ] SLI/SLO definitions per service
- [ ] Grafana dashboards
- [ ] Alert rules (PagerDuty/Slack)
- [ ] Runbooks per alert
- [ ] Chaos engineering basics (pod failure, latency injection)
- [ ] AWS X-Ray traces (Phase 2)
- [ ] Cost optimization report (Phase 2)

---

### ⬜ Phase 8 — Portfolio & Comparison Write-up
**Branch:** `main` (final merge)
**Status:** Not started

- [ ] Architecture comparison: K8s microservices vs AWS Serverless
- [ ] Trade-off analysis document
- [ ] Load test results comparison
- [ ] Cost analysis (K8s infra vs AWS serverless pricing)
- [ ] README.md (public-facing project summary)
- [ ] Architecture blog post draft
- [ ] Interview prep: key design decisions to talk through

---

## Folder Structure Reference

```
ecommerce-platform/
├── CLAUDE.md                          ← project memory for Claude Code
├── WORKFLOW.md                        ← this file
├── .gitignore
├── skills/
│   ├── roles/
│   │   ├── requirements-engineer.md  ✅
│   │   ├── project-manager.md        ⬜
│   │   ├── system-architect.md       ⬜
│   │   ├── backend-developer.md      ⬜
│   │   ├── devops-engineer.md        ⬜
│   │   ├── aws-architect.md          ⬜
│   │   └── qa-engineer.md            ⬜
│   └── techniques/
│       ├── agile-docs.md             ✅
│       ├── system-design.md          ⬜
│       ├── java-springboot.md        ⬜
│       ├── docker-k8s.md             ⬜
│       ├── aws-serverless.md         ⬜
│       ├── testing-standards.md      ⬜
│       └── cicd-patterns.md          ⬜
├── docs/
│   ├── requirements/                 ✅ Complete
│   ├── api-specs/                    ✅ Complete
│   ├── hld/                          ⬜
│   ├── lld/                          ⬜
│   └── adr/                          ⬜
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

### Start a new phase
```bash
git checkout develop
git pull origin develop
git checkout -b feature/[ROLE]-[task]
```

### Activate a role in Claude Code
```
Read skills/roles/[role].md and skills/techniques/[skill].md
then act as that role.
Input: [what artifacts you are consuming]
Task: [what you need produced]
Save to: [file path]
```

### Finish a phase
```bash
git add docs/[relevant folder]/
git commit -m "[ROLE] scope: description of what was produced"
git push origin feature/[branch-name]
# Open PR → develop on GitHub
```

### Update CLAUDE.md progress
After each phase, update the Completed Artifacts checklist in CLAUDE.md
to keep Claude Code context current.

---

## ID Reference

| Artifact | Format | Example |
|----------|--------|---------|
| Functional requirement | FR-[CONTEXT]-001 | FR-ORDER-001 |
| Non-functional requirement | NFR-[CONTEXT]-001 | NFR-ORDER-001 |
| User story | US-[CONTEXT]-001 | US-ORDER-001 |
| Acceptance criteria | AC-[US-ID]-001 | AC-US-ORDER-001-001 |
| Architecture decision | ADR-001 | ADR-001 |
| Domain event | PascalCase past tense | OrderPlaced |

---

*Last updated: June 2026 — Phase 1 (RE) complete, Phase 2 (SA) starting next*
