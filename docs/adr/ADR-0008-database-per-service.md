# ADR-0008: Database-per-Service — One MySQL Schema per Bounded Context

**Status:** Accepted  
**Date:** 2026-06-08  
**Phase:** ARCH  
**Bounded contexts affected:** All  

---

## Context

Seven services need persistent storage. The two main options are:

1. **Shared database** — all services connect to the same MySQL instance and schema
2. **Database per service** — each service owns its schema; no other service can access it

This decision directly impacts bounded context integrity, deployment independence, and
the ability to evolve each service's schema without coordinating with other teams.

---

## Decision

**One MySQL schema per bounded context.** Each schema is provisioned in the same MySQL
instance (Phase 1, cost efficiency) but isolated by separate databases and separate
credentials per service.

| Service | Schema |
|---|---|
| User/Auth | `user_db` |
| Product Catalog | `catalog_db` |
| Cart | _(Redis only — no MySQL)_ |
| Order | `order_db` |
| Payment | `payment_db` |
| Inventory | `inventory_db` |
| Notification | `notification_db` |

**Hard rules:**

1. No service connects to another service's schema. Credentials are schema-scoped;
   the MySQL user for `order-service` has GRANT only on `order_db`.
2. No cross-schema foreign keys. All cross-context references are logical (store the ID,
   not a FK constraint).
3. Cross-context data reads go through the owning service's REST API — never via a JOIN
   across schemas.
4. Each schema has its own Flyway migration baseline. Migrations for `order_db` are in
   `order-service/src/main/resources/db/migration/`.

---

## Consequences

### Positive

- **Independent schema evolution.** Order service can add a `return_line_items` table
  without coordinating a migration with Payment or Inventory. Each service's Flyway
  history is self-contained.
- **Bounded context integrity enforced at infrastructure level.** A developer cannot
  write a JPA entity that spans two schemas — the credential boundary prevents it.
- **Isolated failure.** A corrupted `payment_db` does not affect `catalog_db`. Services
  degrade independently.
- **Independent scaling.** In Phase 2 (DynamoDB), each schema maps to a DynamoDB table
  group per service. The migration is schema-by-schema, not a big-bang rewrite.
- **Clear ownership.** The team responsible for Order Service owns `order_db`'s schema,
  migration scripts, and query performance.

### Negative

- **No cross-service JOINs.** A report that needs order + customer + product data must
  either call three REST APIs and merge in the application layer, or maintain a dedicated
  reporting read model (e.g., an analytics service with its own denormalised schema).
- **Data duplication.** Denormalised snapshots are required — e.g., `order_line_items`
  stores `product_title` and `unit_price` at order time because the Catalog can change
  those fields later. This is intentional and correct (price snapshot invariant).
- **Operational overhead.** Seven schemas means seven Flyway histories, seven sets of
  DB credentials, and seven backup policies. In Phase 1 they share a single MySQL
  instance, which reduces infra cost but still requires schema-level access control.
- **Distributed query problem.** Pagination across data from multiple services (e.g.,
  "show all orders with product name and customer email") cannot be done with a single
  SQL query. Must be composed at the API Gateway / BFF layer.

---

## Alternatives Rejected

### Shared Database, Shared Schema

All services read and write the same tables. Simplest operationally: one DB connection
string, one schema, one migration history.

Rejected because:
- **Coupling at the data layer.** If Order and Catalog share `products`, a change to
  Catalog's schema breaks Order — even if no code changed.
- **No independent deployability.** Every schema migration must be coordinated across
  all services that touch the affected table.
- **Aggregate boundary violations become trivial.** Engineers will write JOINs that cross
  bounded contexts, making the domain model implicit and hard to maintain.
- **Impossible to migrate to Phase 2.** Moving to DynamoDB requires schema-per-service
  as a precondition.

### Shared Database, Separate Schemas (Table Prefix per Service)

All services in the same MySQL instance; services "own" their tables by prefix
(`order_*`, `payment_*`) but share a DB user with full access.

Better than shared schema but still rejected because:
- Without credential isolation, a bug in the Order service can accidentally write to
  `payment_orders` — no enforcement at the database layer.
- Migration tooling becomes awkward (Flyway histories interleave).

### Separate MySQL Instances per Service

One MySQL container/instance per service, not just schema separation.

Stronger isolation but:
- 6× the resource cost for local development (Docker Compose memory).
- No practical benefit over schema separation in Phase 1 at this scale.
- Phase 2 (DynamoDB) makes this irrelevant — each service gets its own DynamoDB table
  group regardless.

Deferred — adopt separate instances in production Kubernetes if schema-level isolation
proves insufficient (e.g., noisy-neighbour on a shared instance under load).
