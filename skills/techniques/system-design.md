# System Design Documentation Standards

**Purpose:** Codifies the HLD/LLD/C4 conventions already established across `docs/hld/` (system-context, container-diagram, component-diagrams, er-diagrams, sequence-diagrams, order-state-machine) and the ADRs they implement — so every new diagram or LLD follows the same structure, notation, and cross-referencing discipline.

**Status:** Living document — extracted from HLD artefacts produced 2026-06-08 onward.

---

## 1. Document Header Block (every HLD/LLD file)

```markdown
# {Title} — High-Level Design

**Artefact type:** {C4 Level 1 | C4 Level 2 | C4 Level 3 | ER Diagrams | Sequence Diagrams | LLD}
**Phase:** ARCH
**Status:** Draft | Accepted
**Version:** 0.1
**Date:** YYYY-MM-DD
**Author:** System Architect
**Inputs:** `path/to/upstream-doc.md` v{version}, ...

---

## 1. Scope

{What this document covers, what it explicitly does NOT cover and which other doc owns that.}
```

**Rules:**
- **Inputs** lists every upstream document this design depends on, with version numbers — when an input changes version, re-check this document for drift.
- **Scope §1** always states the negative space explicitly ("It does not show X — that is the responsibility of Y") to prevent overlapping/duplicated diagrams across documents.
- Version starts at `0.1` (Draft) and becomes `1.0` on `Status: Accepted`.

---

## 2. NFR Targets Table (every HLD document)

Immediately after Scope, every HLD document that makes a design decision constrained by NFRs includes:

```markdown
## 2. NFR Targets This Design Must Satisfy

| ID | Requirement | Target | Design implication |
|---|---|---|---|
| NFR-AVAIL-001 | Overall uptime | 99.9% | No single point of failure; Kafka replicated (RF=3) |
```

- **ID/Requirement/Target** copied verbatim from `docs/requirements/non-functional-requirements.md` — never restate with different numbers.
- **Design implication** is new content: the concrete consequence *for this document's diagrams*. This column is what makes the NFR traceable into an actual architectural choice.

---

## 3. C4 Model — Level-to-Document Mapping

| C4 Level | Document | Shows | Does NOT show |
|---|---|---|---|
| Level 1 — System Context | `docs/hld/system-context.md` | Actors, the platform as one box, external systems, protocols | Internal services, databases |
| Level 2 — Container | `docs/hld/container-diagram.md` | All 7 microservices, API Gateway, Kafka, Redis, MySQL instances, Search Index, communication patterns | Internal class/component structure |
| Level 3 — Component | `docs/hld/component-diagrams.md` | Per-service: Controllers → Services → Repositories → Infrastructure adapters | Class fields, method signatures (→ LLD) |
| Level 4 — Code | `docs/lld/{context}-lld.md` | Aggregate model, DB schema, sequence diagrams for key operations, API contract reference | — |

**Diagram tooling:** Mermaid, embedded directly in the Markdown file (not external draw.io files) — keeps diagrams version-controlled and diff-able. `graph TB` for structural diagrams, `sequenceDiagram` for flows, `erDiagram` for data models, `stateDiagram-v2` (or hand-drawn ASCII state machine, as in `order-state-machine.md`) for aggregate lifecycles.

---

## 4. Component Diagram Convention (C4 Level 3)

Every Spring Boot service follows the **same package structure**, stated once in `component-diagrams.md` §2 and assumed by every per-service section:

```
com.ecommerce.[context]/
├── api/              ← Controllers (REST handlers)
├── application/      ← Services (use cases, aggregate operations)
├── domain/           ← Aggregates, Entities, Value Objects, Domain Events
├── infrastructure/
│   ├── persistence/  ← JPA Repositories, Redis adapters
│   ├── messaging/    ← Kafka producers, Kafka consumers
│   └── client/       ← External HTTP clients (gateway, email, SMS)
└── config/           ← Spring configuration, beans
```

- Diagram arrows always flow `Controller → Service → Domain / Repository / Adapter` — never `Controller → Repository` directly, and never `Domain → Infrastructure` (domain layer has no outward dependencies; adapters implement domain-defined ports).
- Each per-service section states: **Bounded context**, **Aggregates**, **DB** (schema name), **Cache** (Redis usage if any) — before the diagram.

---

## 5. Sequence Diagram Conventions

From `sequence-diagrams.md` §1 — apply to **every** sequence diagram in HLD and LLD:

| Notation | Meaning |
|---|---|
| `→` (solid arrow) | Synchronous REST/HTTP call — caller waits for response |
| `-->` (dashed arrow) | Asynchronous Kafka event — fire and forget, consumer processes independently |
| `par ... and ... end` | Parallel branches (used for saga fan-out, e.g., Order → Inventory + Payment simultaneously) |

**Mandatory annotations:**
- Every flow carries a correlation identifier: `X-Correlation-ID` header on HTTP, `correlationId` field in Kafka event payloads — shown in the diagram or stated in a note.
- Monetary amounts shown in diagrams are in **integer minor units (paise/cents)** per ADR-0001 — never display `1500.00` in a diagram, use `150000`.
- A **Flow Index table** precedes the diagrams, listing: `# | Flow name | Type (Auth/Read/Write/Saga/Infra) | Services involved` — `Type: Saga` flows must reference the corresponding Saga letter (A–E) from `event-storming.md`.

---

## 6. ER Diagram Conventions

From `er-diagrams.md` §1 — these rules apply to **every** table in **every** service schema:

| Rule | Rationale |
|---|---|
| All monetary columns are `BIGINT` (minor units) | ADR-0001 — no floating-point for money |
| No foreign keys across schema boundaries | One schema per service (ADR-0008) — cross-context refs are by ID only (logical, not enforced) |
| All tables have `created_at`, `updated_at` (`TIMESTAMP DEFAULT CURRENT_TIMESTAMP`) | Audit trail and soft-delete support |
| Soft deletes via `deleted_at TIMESTAMP NULL` where stated | Retain records for order history referential integrity |
| Outbox tables (`*_outbox`) for saga-participant services (Order, Payment) | Transactional outbox pattern (container-diagram.md §7) |
| `version BIGINT DEFAULT 0` on mutable aggregates | Optimistic locking — incremented on every write (ADR pending: H-IN-1/H-CT-1) |

- Cart Service has **no ER diagram** — it is Redis-only (ADR-0010, cart storage). State this explicitly rather than omitting the section silently.
- Diagrams use Mermaid `erDiagram` syntax with inline column comments for constraints (`UK "unique platform-wide"`, `"bcrypt cost >= 12"`).

---

## 7. State Machine Convention (Aggregate Lifecycles)

From `order-state-machine.md` and the `Payment` state machine in `event-storming.md`:

- Every aggregate with a lifecycle (`Order`, `Payment`, `Product`, `Return`) gets an explicit state diagram — ASCII or `stateDiagram-v2` — showing **all** states and **only the legal transitions**.
- Every transition is annotated with the **triggering event or command** (`[CartCheckedOut]`, `Webhook: AUTHORISED`).
- Terminal/failure states (`FAILED`, `CANCELLED`, `EXPIRED`) are shown explicitly, including which earlier states can reach them — this is the design surface for compensating-action logic in sagas.
- The state machine document is an **input** to the corresponding LLD's "Domain model" section — the LLD must show how the state column + `version` field implement the diagram (see `agile-docs.md` ADR convention for how LLDs cite the state machine doc as an Input).

---

## 8. Naming and Cross-Cutting Conventions Reference Table

| Concern | Convention | Defined in |
|---|---|---|
| Money representation | Integer minor units + ISO 4217 currency code | ADR-0001 |
| Kafka topic naming | `{context}.{entity}.{event}`, ~50 topics, partition key per topic group | ADR-0002 |
| Service package layout | `api / application / domain / infrastructure / config` | §4 above, component-diagrams.md §2 |
| Optimistic locking | `version BIGINT DEFAULT 0` column | §6 above (ADR pending) |
| Correlation tracing | `X-Correlation-ID` (HTTP) / `correlationId` (Kafka) | §5 above |
| Schema-per-service | No cross-schema FKs; IDs only | ADR-0008 |

When starting a new LLD, read this table first — it answers most "how do I represent X" questions before they become inconsistencies across services.

---

## 9. Phase 1 / Phase 2 Comparison Convention

Per CLAUDE.md, "Phase comparison... is the core learning output." Every HLD document that has a Phase 2 equivalent includes a closing section:

```markdown
## {N}. Phase 2 Delta (AWS Serverless)

| Phase 1 container | Phase 2 equivalent | Key difference |
|---|---|---|
| Spring Cloud Gateway | AWS API Gateway (HTTP API) | Managed; no Kubernetes Ingress needed |
```

- Every row must state the **driver** for the change (cost, operational model, managed-service capability) — not just "what's different" but "why it's different here."
- Where a Phase 1 pattern changes fundamentally in Phase 2 (e.g., choreography → orchestration saga), this is flagged with a forward-reference to the ADR that will formalise it (`ADR-003`, planned).
- LLDs follow the same convention via a sibling `docs/lld/{context}-phase-comparison.md` file (per `clickup-board-structure.md` Phase 8 gate) — produced during the AWS phase, not during Phase 1 LLD.

---

## 10. "Next Artefacts" Convention

Every HLD/LLD document ends with a **Next Artefacts** table (not just Open Questions):

```markdown
## {N}. Next Artefacts

| Artefact | Description |
|---|---|
| **docs/lld/order-lld.md** | First LLD to write — Order is the saga coordinator and has the highest design complexity |
```

This is what makes the documentation set self-driving — the next session can read the most recent HLD/LLD's final section and know exactly what to produce next without re-deriving the project plan.
