# ADR-0002: Stock Reservation Strategy — Time-Bounded DB Reservation with Redis Idempotency

**Status:** Accepted  
**Date:** 2026-06-08  
**Phase:** ARCH  
**Bounded contexts affected:** Inventory, Order, Payment  

---

## Context

When a customer checks out, stock must be held for that order while payment is authorised.
Without a reservation mechanism, two concurrent checkouts for the last unit of stock can
both succeed, leading to oversell.

The reservation must be:

1. **Atomic** — reserve or fail; no partial state.
2. **Time-bounded** — released automatically if payment does not complete within a window.
3. **Idempotent** — a retry of the same reservation must not double-deduct stock.
4. **Consistent with available stock** — `available_qty` visible to buyers must reflect
   reserved stock.

The Inventory Service owns stock data in `inventory_db` (MySQL). The Order Service drives
the checkout saga via Kafka events.

---

## Decision

Stock reservations are stored as **rows in `stock_reservations` (MySQL)** with a **15-minute
TTL column** (`expires_at`). A scheduled job (every 60 seconds) releases expired rows.
Idempotency is enforced by a **`UNIQUE(product_variant_id, order_id)`** constraint.

Key design details:

- `inventory_items.available_qty` is a **generated column**:
  `available_qty = on_hand_qty - reserved_qty` where `reserved_qty` is derived from
  active (non-expired) `stock_reservations` rows via a maintained denormalised counter.
- The denormalised `reserved_qty` on `inventory_items` is updated in the same transaction
  as the `stock_reservations` INSERT/DELETE, using `SELECT ... FOR UPDATE` on the
  `inventory_items` row to serialise concurrent reservations for the same SKU.
- A **Redis idempotency key** (`idem:{idempotencyKey}`) with 24-hour TTL is checked
  before processing any `StockReserve` command. If the key exists, the cached result is
  returned without touching MySQL.
- On payment success (`PaymentAuthorised`): reservation converts to a fulfilment pick
  (`stock_movements` INSERT, `reserved_qty` decremented, `on_hand_qty` decremented).
- On order failure/cancellation: `StockReleased` event triggers reservation DELETE and
  `reserved_qty` decrement.

---

## Consequences

### Positive

- **Oversell prevented.** `SELECT ... FOR UPDATE` on `inventory_items` serialises
  concurrent reservations per SKU; the `UNIQUE` constraint on `stock_reservations`
  prevents double-reservation for the same order.
- **Automatic TTL release.** A 15-minute window covers normal payment latency; expired
  reservations are swept by a DB job without Kafka involvement — no event needed for
  timeout cleanup.
- **Idempotent retries.** Kafka at-least-once delivery can retrigger `StockReserve`
  commands. Redis key prevents double-deduction; MySQL `UNIQUE` constraint is a
  belt-and-braces backstop.
- **Readable available stock.** `available_qty` as a generated column ensures buyers
  always see stock minus active reservations in real-time.
- **Simple audit trail.** `stock_movements` is an append-only ledger; every fulfilment
  pick and release is traceable.

### Negative

- **`SELECT ... FOR UPDATE` is a bottleneck for hot SKUs.** High-concurrency flash sales
  on a single SKU will serialise all checkout attempts. Mitigated in Phase 1 by limiting
  checkout concurrency via API Gateway rate limiting. Phase 2 mitigation: DynamoDB
  conditional writes distribute locking.
- **Sweeper job adds operational complexity.** The 60-second expiry job must be monitored;
  if it falls behind, `available_qty` is understated. Alert on sweeper lag > 5 minutes.
- **15-minute window is opinionated.** Too short and slow payment gateways cause false
  releases; too long and popular items are unavailable. 15 minutes is calibrated to
  typical payment gateway SLA (< 30 seconds) plus retry budget.
- **Redis cache invalidation.** If Redis is unavailable, idempotency falls back to the
  MySQL `UNIQUE` constraint, which is safe but slower.

---

## Alternatives Rejected

### Pessimistic lock for the full payment flow (hold until PaymentAuthorised)

Keep stock locked in DB (`SELECT ... FOR UPDATE`) for the duration of payment
authorisation (up to 30 seconds). Guarantees no phantom reads but holds a row lock
across a network call to the payment gateway — severely limits throughput and risks
lock timeout under load. Rejected.

### Optimistic locking only (version check at commit)

Use `version` column on `inventory_items`; check and increment at reservation time.
Concurrent checkouts on the last unit would cause `OptimisticLockException` for all but
one, requiring retry at the application layer. Simple but produces high retry chatter
under load. Also provides no TTL mechanism — abandoned reservations persist until
manual cleanup. Rejected in favour of the DB-row reservation with TTL.

### Redis-only reservation (no MySQL persistence)

Store reservations entirely in Redis with a 15-minute key TTL. Fast and lock-free, but:

- Redis is not the system of record for inventory; a Redis restart or eviction during
  payment would silently release stock mid-saga.
- Reporting and audit queries require joining Redis state with MySQL — operationally
  unworkable.
- Redis Cluster split-brain can result in double-reservations.

Rejected because MySQL is the single source of truth for stock levels; Redis is
supplemental (idempotency only).

### Saga with compensation only (no reservation)

Allow checkout to proceed without reserving stock; if stock is unavailable at fulfilment
time, cancel the order and refund. Requires payment to be voided after capture — poor UX,
higher payment gateway fees, and regulatory complexity for refunds. Rejected.
