# MySQL, JPA & Flyway — Interview Questions & Answers

---

### Basic

**1. What is Flyway? What problem does it solve?**

Flyway is a database migration tool. It tracks which SQL scripts have been applied to a database and applies pending ones in version order. Without it, DB schema changes are manual and error-prone across environments (dev, staging, prod). With Flyway, the schema evolves alongside the code — each migration is versioned, checksummed, and recorded in the `flyway_schema_history` table.

---

**2. What is the naming convention for Flyway migration files?**

`V{version}__{description}.sql` — two underscores between version and description.
- `V1__init.sql` — version 1, creates the initial schema.
- `V2__add_column.sql` — version 2, adds a column.

Flyway applies migrations in ascending version order. The checksum of each applied script is stored; modifying an applied script causes Flyway to fail at startup with a checksum mismatch (protecting against accidental edits to applied migrations).

---

**3. What is `ddl-auto: validate` in JPA?**

With `validate`, Hibernate reads the database schema at startup and compares it against the entity mappings. If any column type, name, or nullability mismatches, startup fails with `SchemaManagementException`. Other modes:
- `none` — no action (production default when using Flyway).
- `update` — Hibernate alters the schema to match entities (dangerous in production).
- `create` — drops and recreates schema (test only).
- `create-drop` — creates on startup, drops on shutdown (test only).

This project uses `validate` so that entity/migration mismatches are caught immediately at startup.

---

**4. What is the difference between `CHAR` and `VARCHAR` in MySQL?**

- `CHAR(N)` — fixed-length. Always stores exactly N bytes (right-padded with spaces). Faster for fixed-size values because MySQL can compute row offsets without scanning.
- `VARCHAR(N)` — variable-length. Stores only the actual length + 1-2 bytes for length prefix. More space-efficient for variable-length strings.

UUIDs are always 36 characters, so `CHAR(36)` is appropriate — no padding waste, and Hibernate's `preferred_uuid_jdbc_type: CHAR` maps UUID fields to `CHAR(36)`.

---

**5. What is a foreign key constraint? Give an example from `V1__init.sql`.**

A foreign key ensures referential integrity between tables — the value in the child column must exist in the parent column.

```sql
CONSTRAINT fk_addr_user FOREIGN KEY (user_id) REFERENCES users(id)
```

`user_addresses.user_id` must reference an existing `users.id`. MySQL enforces this: inserting an address for a non-existent user fails, and deleting a user with addresses fails (or cascades, depending on config).

---

**6. What is an index? Why does `user_auth_outbox` have an index on `(published, created_at)`?**

An index is a data structure (B-Tree by default in MySQL) that speeds up queries by avoiding full table scans. The outbox table index:
```sql
INDEX idx_outbox_unpublished (published, created_at)
```
`OutboxRelay` runs `findTop100ByPublishedFalseOrderByCreatedAtAsc()` every 500ms. Without an index, MySQL would scan every row. The composite index on `(published, created_at)` lets MySQL quickly find all rows where `published = FALSE` ordered by `created_at`, making the query O(log n + k) instead of O(n).

---

### Intermediate

**7. Why does this project store UUIDs as `CHAR(36)` instead of `BINARY(16)`?**

`BINARY(16)` is more space-efficient (16 bytes vs 36), but `CHAR(36)` is chosen because:
- Human-readable in `SELECT` queries — no hex conversion needed.
- Compatible with `CHAR` type expected by Hibernate's `preferred_uuid_jdbc_type: CHAR`.
- Consistent across all tables (`id`, `user_id`, `aggregate_id`, `correlation_id`).

Trade-off: 20 bytes more per UUID. For the current scale this is acceptable. `BINARY(16)` would be preferred at very high scale.

---

**8. What is `preferred_uuid_jdbc_type: CHAR` in Hibernate configuration?**

By default, Hibernate 6 maps `UUID` Java fields to `BINARY` JDBC type. MySQL doesn't have a native UUID type, so Hibernate stores UUIDs as `BINARY(16)`. This setting overrides that behaviour to use `CHAR` JDBC type instead, aligning with the `CHAR(36)` columns in `V1__init.sql`. Without this setting, Hibernate would expect `BINARY` columns and `ddl-auto: validate` would fail with type mismatch errors.

---

**9. What is the HikariCP connection pool? What is `HikariPool-1`?**

HikariCP is Spring Boot's default JDBC connection pool. Rather than opening a new DB connection for every request (expensive), HikariCP maintains a pool of reusable connections. `HikariPool-1` is the name of the pool instance logged at startup. Key settings (defaults): min-idle=10, max-pool-size=10, connection-timeout=30s. For the user-service, the pool connects to `user_db` on startup and keeps connections warm.

---

**10. What is optimistic locking vs pessimistic locking?**

- **Optimistic locking** — assumes conflicts are rare. Reads data without locking, checks a version number at write time. If the version changed, abort. Used in this project via `@Version` on `User.version`. No database lock is held — high concurrency.
- **Pessimistic locking** — assumes conflicts are common. Acquires a DB lock on read (`SELECT FOR UPDATE`). Other transactions block until the lock is released. Lower concurrency but guarantees no conflicts.

Optimistic locking is preferred for the `User` aggregate because conflicts (two concurrent writes to the same user) are rare.

---

**11. What is `open-in-view: false`? Why is it recommended to disable it?**

By default, Spring Boot keeps the JPA `EntityManager` (and DB connection) open for the entire HTTP request, including the view rendering phase. This is the "Open Session in View" anti-pattern:
- Holds a DB connection longer than necessary.
- Can trigger lazy loading in the view layer, causing N+1 queries silently.

With `open-in-view: false`, the `EntityManager` is closed after the `@Transactional` method completes. All data needed by the controller must be loaded within the transaction. This project sets it to `false` explicitly.

---

**12. What does `insertable = false, updatable = false` mean on `created_at`?**

```java
@Column(name = "created_at", insertable = false, updatable = false)
private Instant createdAt;
```

This tells Hibernate to never include `created_at` in `INSERT` or `UPDATE` statements. The column gets its value from the MySQL `DEFAULT CURRENT_TIMESTAMP(3)` definition. Hibernate reads it back after insert but never writes it — avoiding any mismatch between Java's clock and MySQL's clock.

---

**13. What is a soft delete? How is `deleted_at` used in the `users` table?**

Soft delete marks a record as deleted without physically removing it from the database. The `deleted_at` column is `NULL` for active records and set to the deletion timestamp for deleted ones. Benefits: audit trail, ability to restore, referential integrity preserved. In this project, `deleted_at` is modelled on `users` and `user_addresses`. Queries should filter `WHERE deleted_at IS NULL` to exclude soft-deleted records.

---

**14. What does `DATETIME(3)` mean in MySQL?**

`DATETIME(3)` stores a date and time with **millisecond** precision (3 fractional seconds digits). Standard `DATETIME` has only second precision. Millisecond timestamps are important for:
- Event ordering in distributed systems.
- Accurate `created_at`/`published_at` timestamps in the outbox table.
- Matching Java's `Instant` which has nanosecond precision (stored as milliseconds in MySQL).

---

**15. What is `utf8mb4` charset in MySQL?**

MySQL's `utf8` is misleadingly only 3-byte UTF-8, which cannot store 4-byte Unicode characters (emoji, some CJK characters). `utf8mb4` is true 4-byte UTF-8. The `users` table uses `utf8mb4` with `utf8mb4_0900_ai_ci` collation (accent-insensitive, case-insensitive) so email addresses are case-insensitive by default and the schema supports full Unicode.

---

**16. What is ADR-0008 (database-per-service)?**

Each microservice owns its own MySQL schema and a MySQL user with `GRANT` scoped to only that schema. No service can query another service's schema directly.

Trade-offs documented in ADR-0008:
- **Pro:** independent deployability, schema evolution without coordination, bounded context isolation.
- **Con:** no cross-context joins — reporting queries require event-driven projections or a separate read model.

In `docker-compose.infra.yml`, a single MySQL container hosts all schemas, but each service connects with its own credentials.

---

### Advanced

**17. What is the N+1 query problem? How can it occur with `@OneToMany` on `User.addresses`?**

N+1 occurs when: 1 query fetches N parent records, then N additional queries fetch children for each. Example: loading 50 users and then accessing `user.getAddresses()` for each would fire 50 separate SELECT queries for addresses.

In this project, `addresses` is `@OneToMany` with default `LAZY` loading. Within a `@Transactional` method, accessing `user.getAddresses()` fires a lazy load query for that user's addresses. For single-user operations this is fine. For bulk admin queries returning many users, `JOIN FETCH` or `@EntityGraph` should be used to load addresses in a single query.

---

**18. If you needed to query users across bounded contexts, how would you handle it?**

Never via cross-schema SQL joins. Instead:
- **Event-driven projection** — downstream services consume `UserRegistered` and maintain their own read model (e.g., `customer_profiles` table in the Order service).
- **API call** — Order service calls `GET /v1/admin/users/{id}` on user-service synchronously (adds coupling but acceptable for read operations).
- **Shared read database** — a separate analytics DB that aggregates data from multiple services via CDC (Change Data Capture). Acceptable for reporting, not for transactional writes.

---

**19. What happens if a Flyway migration fails halfway through?**

MySQL DDL statements (`CREATE TABLE`, `ALTER TABLE`) are **not transactional** — they auto-commit. If `V2__add_column.sql` fails halfway, some statements may have already committed. Flyway marks the migration as failed in `flyway_schema_history` (state = FAILED). On next startup, Flyway refuses to proceed until the failed migration is resolved.

Resolution: manually fix the database to the expected state, then either run `flyway repair` (clears the failed entry) or apply the remaining statements manually.

---

**20. What is the checksum validation Flyway performs?**

When Flyway applies a migration, it calculates a CRC32 checksum of the SQL file and stores it in `flyway_schema_history`. On every subsequent startup, Flyway recalculates the checksum of each applied migration file and compares it against the stored value. If they differ, Flyway fails with `FlywayValidationErrorException`. This prevents accidental edits to migration files that have already been applied — a critical safety net in production. To fix: use `flyway repair` (resets checksums) or `flyway.validateOnMigrate=false` (not recommended).
