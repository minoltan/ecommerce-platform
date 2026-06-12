# ADR-0010: Cart State Storage — Redis over Relational Database

**Status:** Accepted  
**Date:** 2026-06-08  
**Phase:** ARCH  
**Bounded contexts affected:** Cart  

---

## Context

The Cart bounded context must store user shopping carts (line items, quantities, price
snapshots) for two user types:

- **Authenticated users** — cart must persist across sessions and devices
- **Guest users** — cart is session-scoped; merged into the authenticated cart on login

Cart characteristics that drive the storage decision:

- **High read/write frequency.** Every item add/remove/quantity change mutates the cart.
  A user browsing actively may trigger 20–50 cart mutations per session.
- **Temporary data.** Carts are not long-lived records. The vast majority of carts are
  abandoned or converted to orders. Retention beyond 30 days has no value.
- **Simple structure.** A cart is a list of `{variantId, quantity, priceSnapshot}` items.
  No complex relational structure; no aggregations; no JOIN requirements.
- **TTL-driven expiry.** Guest carts expire after session timeout; abandoned user carts
  should expire after 30 days automatically.
- **Fast response expected.** Add-to-cart must respond in < 50 ms at the 99th percentile.

---

## Decision

**Redis is the primary (and sole) store for cart state.** No MySQL table for cart data.

### Redis key structure

| Key | TTL | Content |
|---|---|---|
| `cart:user:{userId}` | 30 days (sliding) | JSON: `{items: [{variantId, qty, priceSnapshot, addedAt}]}` |
| `cart:guest:{sessionId}` | 24 hours | JSON: same structure |

### Operations

- `GET cart:user:{userId}` → retrieve cart (entire JSON blob)
- `SET cart:user:{userId} {json} EX {ttl}` → full cart replace on every mutation
- `DEL cart:user:{userId}` → checkout clears cart after OrderPlaced published
- **Cart merge on login:** `GET cart:guest:{sessionId}` → merge items into `cart:user:{userId}` → `DEL cart:guest:{sessionId}`

### Price snapshot rule

When a line item is added to the cart, the current price is fetched from the Product
Catalog (synchronous REST call) and stored as `priceSnapshot` in the Redis value.
The price displayed to the user is the snapshot; a stale-price banner is shown if the
live price has changed by more than 5% when the user views the cart (checked on cart load,
not on every add).

### Checkout

At checkout, Order Service reads the cart from Redis (via a Cart Service REST endpoint),
creates the order with price snapshots, and publishes `OrderPlaced`. Cart Service then
deletes the Redis key after receiving confirmation from the caller.

---

## Consequences

### Positive

- **Sub-millisecond latency.** Redis GET/SET on a local cluster is < 1 ms. Add-to-cart
  responses are dominated by the Product Catalog price lookup (< 5 ms), not storage.
- **Automatic TTL expiry.** Redis key expiry handles guest cart cleanup and abandoned cart
  purging without a cron job or soft-delete column.
- **Simple data model.** Cart is a self-contained JSON blob per user. No schema
  migrations; cart structure changes are JSON-schema changes, not DDL.
- **Elastic memory.** Redis can hold thousands of concurrent carts in memory; a 100-item
  cart JSON is < 5 KB. At 10K concurrent users: ~50 MB — trivial for Redis.
- **No cross-context DB dependency.** Cart data never enters any service's MySQL schema,
  reinforcing schema isolation (ADR-0008).

### Negative

- **Redis is not a durable store.** If Redis restarts without persistence (RDB/AOF), all
  carts are lost. Mitigated by enabling Redis RDB snapshots (every 60 seconds); acceptable
  data loss window is < 60 seconds of cart activity.
- **No complex queries.** Cannot query "all carts containing variantId X" without a full
  Redis scan (O(N)). Acceptable — such queries are not needed for cart operations. If
  needed for analytics, they would go to a separate analytics store.
- **Price snapshot can become stale.** If a product's price drops after add-to-cart, the
  customer pays the higher snapshot price until they reload the cart. Cart load explicitly
  checks for stale snapshots (> 5% deviation) and shows a banner. The Order Service
  validates prices at checkout against the live catalog.
- **No transaction support across keys.** Cart merge (guest → user) must be implemented
  as an atomic MULTI/EXEC or Lua script to prevent partial merges if Redis fails mid-
  operation.

---

## Alternatives Rejected

### MySQL cart table

Store carts in `cart_db.carts` and `cart_db.cart_items`. Full relational model with
foreign keys.

Pros: ACID guarantees; cart survives Redis restart; queryable for analytics.

Cons:
- Every add-to-cart hits MySQL; at 50 mutations/session × 1K concurrent users =
  50K writes/minute — MySQL can handle it but adds unnecessary load for temporary data.
- No native TTL; requires a cron job to purge expired carts.
- Schema migrations required when cart structure changes.

Rejected. The cart's temporary, high-frequency, schema-free nature is a better fit for
Redis than MySQL.

### MySQL + Redis hybrid (write-through cache)

Write to both MySQL (durable) and Redis (fast read). Consistency maintained by write-
through on mutation, invalidation on checkout.

More resilient than Redis-only but:
- Doubles write overhead.
- Introduces consistency complexity (what if MySQL write succeeds and Redis fails?).
- Complexity not justified for temporary cart data with < 60s acceptable loss window.

Rejected in favour of Redis-only with RDB snapshots.

### DynamoDB (Phase 1)

Phase 2 uses DynamoDB for cart storage. Using it in Phase 1 would eliminate the
Redis → DynamoDB migration step.

Rejected:
- Phase 1 runs on Kubernetes; tight AWS SDK dependency complicates local development.
- Redis is already in the stack (rate limiting, token blacklist); no additional infra.
- Redis TTL semantics map perfectly to cart expiry; DynamoDB TTL has up to 48-hour
  lag on actual deletion.

---

## Amendment (cross-cutting HLD sync PR, SA-021)

`docs/lld/cart-lld.md` (SA-014) refined three details of this ADR's Decision section
during LLD-level design. The core decision — **Redis as the sole cart store, no MySQL
table** — is unchanged. Per this project's amend-don't-renumber convention (see
ADR-0011/ADR-0012 amendment notes), the original Decision/Consequences sections above
are left as written for historical context; the refinements are:

1. **TTL values** — `cart:user:{userId}` is **7 days** (not 30 days) and
   `cart:guest:{sessionId}` is **30 minutes** (not 24 hours), both **sliding** (reset on
   each mutation, not fixed-window). Rationale (cart-lld.md §7.2): a 30-day authenticated
   TTL retains far more abandoned-cart memory than the 24h/7d split needs, and a 24h
   guest TTL is generous for a session-scoped cart that drives no SLO — 30 minutes
   matches typical browsing-session length and bounds guest-cart memory more tightly.
   `CartAbandoned` (cart-lld.md §3) fires on `cart:user:*` TTL expiry only.

2. **Storage shape** — each cart is a **Redis `HASH`**, not a single JSON blob key.
   Fields: `meta` (JSON: `couponCode`, `discountAmount`, `currency`, `ownerType`) and one
   `item:{itemId}` field per line item (JSON). Rationale (cart-lld.md §7): a single-item
   quantity change (the most frequent mutation) becomes `HSET cart:user:{userId}
   item:{itemId} {json}` instead of a full-cart `GET` + JSON-decode + mutate + `SET`
   round trip — avoids the read-modify-write race this ADR's §93 "atomic MULTI/EXEC or
   Lua script" caveat was written to guard against, for the common case.

3. **New `itemId` field** — each `LineItem` (cart-lld.md §3) gains a server-generated
   `itemId` (UUID), used as the Hash field suffix above and as the stable identifier for
   `PATCH /cart/items/{itemId}` / `DELETE /cart/items/{itemId}` operations. Previously
   line items were addressed by `(variantId)` alone (INV-CT-01 still enforces at most one
   LineItem per `(productId, variantId)` — `itemId` is an addressing convenience, not a
   new uniqueness axis).

These refinements resolve OQ-LLD-CT-01, OQ-LLD-CT-02, and OQ-LLD-CT-05 from
`cart-lld.md` §13.
