# ADR-0014: Saga-Join State Tracking via Per-Aggregate Side Tables

**Status:** Accepted  
**Date:** 2026-06-12  
**Phase:** ARCH  
**Bounded contexts affected:** Order, Inventory (Payment affected indirectly — see Consequences)  

---

## Context

Saga A (Order Confirmation, `order-state-machine.md` §"Saga Boundaries") requires the
Order Service to wait for **two independent, parallel responses** —
`PaymentAuthorised` (from Payment) and `StockReserved` (from Inventory) — before
transitioning `PENDING_PAYMENT → CONFIRMED` (T-04). Symmetrically, Saga E (Return
Flow) requires **both** `RefundProcessed`/`RefundIssued` (Payment) and
`StockRestored` (Inventory) before `RETURN_APPROVED → RETURN_COMPLETED` (T-19).

Choreography-based sagas over Kafka give **no ordering guarantee** between events
published by two different services to two different topics. Either event can arrive
first, and — per `order-lld.md` §13 (OQ-SD-01) — a naive implementation
("transition to CONFIRMED when `PaymentAuthorised` arrives, provided
`stockReserved` flag is already true on the `Order` row") is **not commutative**: if
`StockReservationFailed` arrives *after* `PaymentAuthorised` has already been
recorded, the system must still detect this and trigger Saga C (void the
already-authorised payment, T-06) — a check that is easy to omit if the join logic is
written ad hoc per consumer.

`order-lld.md` §6.2/§8.2 already designed and implemented a concrete solution for
Saga A — the `order_saga_state` side table plus `SagaJoinService` — and flagged
formalising it as a reusable pattern (§14, "ADR-0014... referenced by Payment and
Inventory LLDs"). `payment-lld.md` §11 and `inventory-lld.md` §11 both reference this
ADR as pending. `inventory-lld.md` §10 additionally raised a related but distinct
concern: the saga-join inputs (`StockReserved`, `StockReservationFailed`,
`StockReleased`, `StockRestored`) are published by Inventory **without a
transactional outbox** (`er-diagrams.md` §8 marks Inventory as
"Kafka publish acceptable loss"), creating a risk of a permanently stuck order if one
of these events is lost between DB commit and Kafka publish.

This ADR formalises both: (1) the **saga-join side-table pattern** as the standard
mechanism for any aggregate awaiting N≥2 independent async responses, and (2) the
**delivery-guarantee requirement** for events that feed a saga join.

---

## Decision

### 1. Saga-join side-table pattern

Any aggregate that must wait for **two or more independent events** before performing
a state transition uses a dedicated **1:1 side table** (`{aggregate}_saga_state`),
written to by a dedicated **`SagaJoinService`** component, with the following shape:

```sql
CREATE TABLE {aggregate}_saga_state (
    {aggregate}_id  CHAR(36)  PRIMARY KEY,  -- FK to the aggregate's own table, same schema
    {condition_1}             BOOLEAN NOT NULL DEFAULT FALSE,
    {condition_1}_at          TIMESTAMP NULL,
    {condition_2}             BOOLEAN NOT NULL DEFAULT FALSE,
    {condition_2}_at          TIMESTAMP NULL,
    {failure_condition}       BOOLEAN NOT NULL DEFAULT FALSE,
    -- ... one boolean (+ optional timestamp) per awaited condition
);
```

**Per-event handler contract** (this is the part that makes the join commutative):

1. On consuming an event that satisfies condition `C`, the handler:
   - Writes `{C} = TRUE, {C}_at = now()` to the side table.
   - **Re-reads the full current state** of the side table (all conditions, including
     ones it didn't just write) within the same transaction.
   - Evaluates **all** transition rules against this complete state — not just the
     rule "naturally associated" with event `C`.
2. The aggregate's status update, the side-table write, and any outbox insert happen
   in a **single DB transaction**.
3. Every transition rule must be written assuming **either** order of arrival —
   i.e., for each pair of awaited conditions `(A, B)`, there must be an explicit rule
   for "B arrives, A already TRUE" *and* "A arrives, B already TRUE", and these two
   rules must be consistent (commutative).
4. Failure conditions (e.g., `StockReservationFailed`) must be checked **even when
   arriving after a success condition has already been recorded** — this is the
   specific failure mode OQ-SD-01 identified, and is the reason step 1's "re-read
   full state" requirement exists: a handler that only checks "did my own condition
   just become satisfiable" would miss late-arriving failures.

`order-lld.md` §6.2's table (reproduced below) is the reference implementation of
this pattern for Saga A:

| Event consumed | Write | Then check |
|---|---|---|
| `PaymentAuthorised` | `payment_authorised = TRUE` | If `stock_reserved` → CONFIRMED (T-04). If `stock_reservation_failed` → void payment (Saga C, T-06) |
| `StockReserved` | `stock_reserved = TRUE` | If `payment_authorised` → CONFIRMED (T-04) |
| `StockReservationFailed` | `stock_reservation_failed = TRUE` | If `payment_authorised` → void payment immediately (Saga C, T-06). Else wait |

### 2. Saga-join inputs require an outbox (closes the Inventory gap)

Any event consumed by a `SagaJoinService` (i.e., any event in the "Event consumed"
column of a `{aggregate}_saga_state` table) **must** be published via the
transactional outbox pattern (`er-diagrams.md` §1) by its publishing service —
**regardless of that service's general outbox policy**.

Concretely, this **adds a scoped `inventory_outbox`** to `inventory_db`, covering
exactly the four events `inventory-lld.md` §9.2 identified as saga-critical:
`StockReserved`, `StockReservationFailed`, `StockReleased`, `StockRestored`.
`ProductOutOfStock` and `LowStockAlertTriggered` remain direct-publish (no saga
consumes them) — `inventory-lld.md` §10 Option B, adopted here as the formal
decision.

`er-diagrams.md` §8 (Schema Isolation Summary) is updated:

| Service | Schema | Outbox (revised) |
|---|---|---|
| Inventory | `inventory_db` | `inventory_outbox` (StockReserved, StockReservationFailed, StockReleased, StockRestored **only**) |

Payment is unaffected — `payment_outbox` already covers all of Payment's published
events (`payment-lld.md` §7.1), including the saga-critical `PaymentAuthorised` /
`PaymentFailed` / `RefundProcessed` / `StockRestored`'s sibling `RefundIssued`.

---

## Consequences

### Positive

- **Commutativity is now a checklist, not a design exercise.** Any future saga join
  (e.g., a hypothetical Saga F) can be implemented by following the side-table
  contract in Decision §1 rather than re-deriving the OQ-SD-01 reasoning from
  scratch — directly serves CLAUDE.md's "Saga awareness" guidance for this user's
  architect-transition portfolio.
- **Closes the stuck-order gap identified in `inventory-lld.md` §10.** With
  `inventory_outbox` covering the 4 saga-critical events, a crash between Inventory's
  DB commit and Kafka publish no longer permanently strands an order in
  `PENDING_PAYMENT` (Saga A) or `RETURN_APPROVED` (Saga E) — `OutboxRelay` retries
  until the publish succeeds, exactly as it already does for Order and Payment.
- **Minimal new surface area.** `inventory_outbox` reuses the exact schema and
  `OutboxRelay` component pattern already implemented twice (`order_outbox`,
  `payment_outbox`) — no new pattern, just a third instance of an existing one.
- **`er-diagrams.md` §8's "acceptable loss" framing is now scoped correctly.**
  `ProductOutOfStock`/`LowStockAlertTriggered` (genuinely informational, no saga
  depends on them) keep the simpler direct-publish path — the ADR doesn't
  over-engineer the entire Inventory service, only the part that needed it.

### Negative

- **`inventory_db` gains a table and a background job (`OutboxRelay`) it didn't have
  before**, increasing Inventory's operational surface to match Order/Payment
  (an additional poller to monitor, an additional `published=FALSE` backlog metric).
  Mitigation: this is the same `OutboxRelay` codebase/config as Order and Payment —
  no new operational *pattern* to learn, just one more deployment of an existing one.
- **The side-table pattern adds one extra table per saga-joining aggregate.**
  For aggregates with only one awaited condition (i.e., no actual join), this pattern
  is unnecessary overhead — the ADR's scope is explicitly limited to **N≥2
  independent conditions**. `Order` (Saga A and E) is currently the only aggregate
  that needs it; `Payment` and `Inventory` aggregates do not themselves perform
  saga-joins (they react to single events), so they do **not** get their own
  `_saga_state` tables under this ADR.
- **Re-reading full side-table state on every event handler invocation** is one extra
  `SELECT` per event compared to a naive "check only my own condition" handler.
  Negligible at Phase 1 scale (single-row PK lookup within an already-open
  transaction); flagged here only so it isn't mistaken for an oversight if profiling
  ever surfaces it.

---

## Alternatives Rejected

### Orchestration (Step Functions / Spring State Machine) for Saga A in Phase 1

A central orchestrator could call Payment and Inventory, wait for both responses, and
then transition the order — making the join explicit in code rather than via a side
table.

Rejected because:
- CLAUDE.md's saga-awareness guidance defaults Phase 1 to choreography, reserving
  orchestration for cases where compensating logic is complex enough to justify it.
  The Saga A join, once the side-table pattern is applied, is *not* complex enough —
  it's two booleans and four transition rules.
- Introducing a Spring State Machine (or similar) orchestrator for just this one join
  would create an architectural inconsistency: every other saga (B, C, D) in
  `order-state-machine.md` is choreography-based. Phase 2's Step Functions
  orchestration (already planned per `order-state-machine.md` §"Saga coordination")
  is the more natural place to introduce orchestration, where the Phase 1→2 delta
  becomes the explicit learning artefact (`order-lld.md` §12).

### Order Service polls Inventory/Payment REST APIs instead of consuming events

Order Service, on receiving `PaymentAuthorised`, could synchronously call
`GET /inventory/reservations/{orderId}` to check reservation status instead of
maintaining `order_saga_state`.

Rejected because:
- Reintroduces a **synchronous cross-context call** in the critical checkout path,
  which CLAUDE.md explicitly flags as something to avoid where events should suffice
  ("Flag any design that would require... synchronous calls where events should
  suffice").
- Couples Order's saga-join availability to Inventory's real-time API availability —
  defeats the purpose of choreography (services remain independently deployable and
  available).

### Single shared `saga_state` table across all bounded contexts

A cross-context `saga_orchestration_db` schema holding saga state for all sagas
(A–E), queried/written by whichever service needs to check join status.

Rejected because:
- Directly violates `er-diagrams.md` §6's "no foreign keys / no shared schemas across
  contexts" rule (ADR-0008, database-per-service) — `order_saga_state` and any future
  `{aggregate}_saga_state` table must live in the owning aggregate's own schema
  (`order_db`), not a shared one.
- A shared schema becomes a single point of coordination/contention exactly where the
  architecture is trying to achieve independence — the antithesis of choreography.

### Leave Inventory's "no outbox" policy unchanged; rely on Kafka producer `acks=all` + retries only

`inventory-lld.md` §10 Option A — accept the residual risk, since `acks=all` +
retries already covers most failure modes, and the probability of the narrow
crash-window loss is low.

Rejected (in favour of Option B, per `inventory-lld.md` §10's own recommendation)
because:
- The *impact* (an order permanently stuck in `PENDING_PAYMENT` with no automated
  recovery — `order-state-machine.md` defines no timeout transition out of
  `PENDING_PAYMENT` other than via `PaymentFailed`/`StockReservationFailed`, neither
  of which fires if the event is simply lost) is severe enough that "low probability"
  is not sufficient justification, given the fix is a known, already-twice-implemented
  pattern.
