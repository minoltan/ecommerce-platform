# Agile Documentation Standards

**Purpose:** Codifies the formats already established in `docs/requirements/` and `docs/adr/` for user stories, acceptance criteria, non-functional requirements, and ADRs — so every future document (new epics, new ADRs, Phase 2 deltas) follows the same structure without re-deriving it.

**Status:** Living document — extracted from RE phase artefacts (`user-stories.md`, `acceptance-criteria.md`, `non-functional-requirements.md`) and HLD ADRs (`docs/adr/ADR-0006` onward).

---

## 1. User Stories

**File:** `docs/requirements/user-stories.md` (one file, organized by epic).

### Epic Index

Every story document opens with an epic index table:

```markdown
## Epic Index

| Epic ID | Name | Bounded Context | Priority |
|---|---|---|---|
| EP-01 | User Identity & Access | User/Auth | Must |
```

- **Epic ID:** `EP-NN`, sequential, no gaps.
- **Bounded Context:** must match one of the 7 contexts in CLAUDE.md (or `Cross-Cutting`).
- **Priority:** MoSCoW — `Must` / `Should` / `Could` / `Won't`.

### Story Format

Each story is a fenced block under a `**US-{CONTEXT}-{NN}**` heading, derived 1:1 from a command in the event-storming catalogue:

```markdown
**US-UA-01**
\`\`\`
As a Guest,
I want to register with my email and password,
So that I can create an account and start shopping.

context: User/Auth
priority: Must
type: functional
estimate: 3 points
tags: registration, onboarding
\`\`\`
```

**Rules:**
- ID format: `US-{CTX}-{NN}` where `{CTX}` is a 2–3 letter context code (`UA`, `PC`, `CT`, `OR`, `PM`, `IN`, `NT`) — same codes used in event-storming hotspot/command IDs for traceability.
- `As a / I want / So that` — exactly three lines, blank line before the metadata block.
- Metadata block is always present and always in this order: `context`, `priority`, `type`, `estimate`, `tags`.
- `type`: `functional` | `non-functional` | `technical` | `spike`.
- `estimate`: Fibonacci points (1, 2, 3, 5, 8, 13) — matches ClickUp Story Points field.
- `priority`: MoSCoW, inherited from the epic unless overridden.
- One story per command/event pair from `event-storming.md` — if a new domain event is added later, add a corresponding story here.

---

## 2. Acceptance Criteria

**File:** `docs/requirements/acceptance-criteria.md` — covers **critical/high-risk stories only** (saga paths, payment, auth, inventory concurrency), not every story.

### Format — Given/When/Then (Gherkin)

```markdown
## US-UA-01 — User Registration

**Story:** As a Guest, I want to register with my email and password.

\`\`\`
Scenario: Successful registration
  Given a guest provides a valid email not already registered
  And a password meeting the minimum policy (8 chars, 1 uppercase, 1 number)
  When the guest submits the registration form
  Then the system creates an account in UNVERIFIED state
  And sends a verification email to the provided address
  And returns HTTP 201 with the userId (no token issued yet)

Scenario: Registration with duplicate email
  Given an account already exists for the provided email
  When the guest submits the registration form
  Then the system returns HTTP 409
  And the response body contains error code USER_EMAIL_CONFLICT
\`\`\`
```

**Rules:**
- Section heading: `## {Story ID} — {Short Title}`, followed by a one-line restatement of the story.
- One `Scenario:` per distinct outcome — happy path **and** every named failure mode from the story's domain (validation errors, conflicts, auth failures).
- Every `Then` for an HTTP-facing operation states the **HTTP status code** and, for errors, an **error code** (`USER_EMAIL_CONFLICT`, `INVALID_CREDENTIALS`, etc.) — error codes become the contract for the OpenAPI spec's error schema.
- Domain events published as a side effect are named explicitly (`And a UserLoggedIn event is published`) — this is what links AC back to the event-storming catalogue and forward to Kafka contract tests.
- No implementation detail (no class names, no SQL) — AC describes observable behaviour only.

---

## 3. Non-Functional Requirements

**File:** `docs/requirements/non-functional-requirements.md` — one table per category.

### Format

```markdown
## 1. Performance / Latency

| ID | Requirement | Target | Measurement Point |
|---|---|---|---|
| NFR-PERF-001 | Product search response time | p95 < 200ms, p99 < 500ms | API Gateway → response |
```

**ID prefixes (fixed set — do not invent new ones without updating this file):**

| Prefix | Category |
|---|---|
| `NFR-PERF-*` | Performance / Latency |
| `NFR-SCALE-*` | Scalability / Throughput |
| `NFR-AVAIL-*` | Availability / Reliability |
| `NFR-CONS-*` | Consistency |
| `NFR-SEC-*` | Security |

**Rules:**
- IDs are sequential within their prefix and **never renumbered or reused** — HLD/LLD/ADR documents reference these IDs directly (e.g., `container-diagram.md` §2 cites NFR-AVAIL-001). Renumbering breaks every back-reference.
- **Target** must be numeric and measurable — no "fast", "scalable", "highly available" without a number. If a target is genuinely TBD, write `TBD — see OQ-{n}` and add the open question to the relevant doc's Open Questions table, never leave it blank.
- **Measurement Point / Scope / Notes** column states *where* the metric is measured (API Gateway, broker metrics, service-internal) — this becomes the basis for the Grafana dashboard / alert design later (CICD phase).
- Every NFR referenced by an HLD/LLD design decision must be cited by ID in that document's "NFR Targets This Design Must Satisfy" table (see `system-design.md` §2 for that convention).

---

## 4. Architecture Decision Records (ADRs)

**File:** `docs/adr/ADR-{NNNN}-{kebab-title}.md`, four-digit zero-padded number.

### Header Block

```markdown
# ADR-0006: Microservices Architecture over Monolith

**Status:** Accepted
**Date:** 2026-06-08
**Phase:** ARCH
**Bounded contexts affected:** All

---
```

- **Status:** `Proposed` | `Accepted` | `Superseded by ADR-NNNN` | `Deprecated`. Never delete or renumber a superseded ADR — link forward and backward.
- **Date:** the decision date (ISO 8601), not the file-creation date if they differ.
- **Phase:** `ARCH` for Phase-1 decisions; `ARCH-P2` for Phase-2-only decisions (see `system-design.md` §5 for the Phase 1/2 ADR split convention).
- **Bounded contexts affected:** explicit list, or `All` — used to cross-link from each LLD's "Related ADRs" section.

### Body Sections (in this order, every ADR)

1. **Context** — the problem, the constraints (cite NFR IDs and hotspot IDs from event-storming.md by their exact ID, e.g., `H-PM-3`, `NFR-CONS-001`), and why a decision is needed *now* (what it unblocks).
2. **Decision** — the chosen approach, stated concretely enough to implement from (concrete schema fragments, naming conventions, config values — not just "we will use X pattern").
3. **Consequences**, split into:
   - **Positive** — benefits, tied back to the NFRs/hotspots from Context.
   - **Negative** — costs/risks accepted, with a mitigation noted inline where one exists.
4. **Alternatives Rejected** — each alternative gets its own `###` subsection: one-line description, then "Rejected because: ...". Never omit this section — it's the primary artefact that demonstrates trade-off reasoning for the architect-transition portfolio.

### Cross-Referencing Convention

- ADRs reference event-storming hotspots by ID (`H-IN-1`), NFRs by ID (`NFR-SCALE-002`), and other ADRs by number (`ADR-0008`).
- HLD/LLD documents reference ADRs by number when a design choice implements that ADR's decision (e.g., `er-diagrams.md` §1: "All monetary columns are `BIGINT` (paise) | ADR-001").
- The Pattern Decision Log in `skills/techniques/design-patterns.md` cross-references ADRs — update both when a new pattern-defining ADR is accepted.

---

## 5. Open Questions Convention (all RE/HLD documents)

Every requirements/HLD document ends with an **Open Questions** table:

```markdown
## Open Questions

| # | Question | Owner | Impact |
|---|---|---|---|
| OQ-SC-01 | Stripe vs Razorpay — which gateway for Phase 1? | Architect | ADR if multi-currency |
```

- ID format: `OQ-{DOC}-{NN}` where `{DOC}` is a short code for the source document (`SC` = system-context, `CD` = container-diagram, `PM` = project-management).
- **Owner** is a role (`Architect`, `Product Owner`, `PM`), not a person — this is a solo/portfolio project, but the role-based ownership documents *whose competency* the decision falls under.
- When an open question is resolved, move it to the relevant ADR's Context section (as the trigger) rather than just deleting the row — keep a `Resolved → ADR-NNNN` note for traceability.
