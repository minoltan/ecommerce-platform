# Domain-Driven Design (DDD) — Interview Questions & Answers

---

### Basic

**1. What is Domain-Driven Design (DDD)?**

DDD is a software design approach that centres the model on the business domain. Key ideas:
- The codebase reflects the language and concepts of the business (Ubiquitous Language).
- Complex domains are divided into Bounded Contexts, each with its own model.
- Domain logic lives in domain objects (aggregates, entities, value objects), not service classes.
- Technical concerns (persistence, messaging) are kept out of the domain layer.

---

**2. What is a bounded context? Name the seven bounded contexts in this project.**

A bounded context is an explicit boundary within which a particular domain model applies. The same word can mean different things in different contexts. The seven in this project:

| Context | Responsibility |
|---|---|
| User / Auth | Identity, authentication, JWT |
| Product Catalog | Listings, search, pricing |
| Cart | Session cart, line items |
| Order | Order lifecycle, state machine |
| Payment | Processing, refunds |
| Inventory | Stock levels, reservations |
| Notification | Email/SMS/push delivery |

---

**3. What is an aggregate? What is an aggregate root?**

An aggregate is a cluster of domain objects treated as a single unit for data changes. The aggregate root is the only entry point to the cluster — external code may only hold references to the root, never to internal entities directly.

In this project, `User` is the aggregate root. `UserAddress` is an internal entity that can only be accessed and modified through `User.addAddress()`, `User.setDefaultAddress()`, `User.removeAddress()`.

---

**4. What is the difference between an entity and a value object?**

- **Entity** — has a unique identity that persists over time. Two `User` objects with the same email but different IDs are different entities. Identity matters more than attribute values.
- **Value object** — defined entirely by its attributes; has no identity. Two `Email("x@y.com")` instances are equal if the email string is equal. Value objects are immutable.

In this project: `User`, `UserAddress`, `EmailVerification` are entities. `Email`, `PasswordHash`, `UserId` are value objects.

---

**5. What is a domain event?**

A domain event is something significant that happened in the domain. It is named in past tense and carries the data needed by consumers. Examples in this project: `UserRegistered`, `UserLoggedIn`, `UserDeactivated`. They are written to the `user_auth_outbox` table as `OutboxEvent` rows and published to Kafka by `OutboxRelay`.

---

### Intermediate

**6. In this project, `User` is an aggregate root. What rules does it enforce that a plain data class would not?**

`User` enforces state transition invariants:
- `verifyEmail()` — throws if status is `DEACTIVATED`; is a no-op if already `ACTIVE`.
- `login()` — throws `IllegalStateTransitionException` if not `ACTIVE`; throws `InvalidCredentialsException` if password doesn't match.
- `deactivate()` — throws if already `DEACTIVATED` (terminal state — no reactivation).
- `setDefaultAddress()` — atomically clears `isDefault` on all other addresses before setting it on the target.

A plain data class (POJO with setters) would allow any caller to set `status = ACTIVE` directly, bypassing all validation.

---

**7. `Email` and `PasswordHash` are value objects. What makes them value objects rather than entities?**

- They have no identity — there is no `id` field.
- They are immutable — no setters.
- Equality is by value — two `Email` instances with the same string are equal.
- `Email` validates format on construction (throws if invalid).
- `PasswordHash` encapsulates bcrypt hashing — `PasswordHash.hash(raw)` produces the hash; `matches(raw)` verifies. The raw password never leaks outside the value object.

---

**8. Why is `User`'s constructor `private`, with a static factory method `User.register(...)`?**

This enforces the invariant that every new `User` starts in `UNVERIFIED` status with role `CUSTOMER`. If the constructor were public, any caller could create a `User` in any state. The factory method name `register` also communicates business intent — it maps directly to the `UserRegistered` domain event and the `T-UA` transition in the LLD.

---

**9. What is an `AttributeConverter` (`EmailConverter`, `PasswordHashConverter`)? Why is this pattern used?**

`AttributeConverter<X, Y>` is a JPA interface that converts between a domain type `X` and a database column type `Y`. It keeps value objects in the domain model while storing raw strings in the DB:
- `EmailConverter` — converts `Email` ↔ `String` (column `varchar(255)`).
- `PasswordHashConverter` — converts `PasswordHash` ↔ `String` (column `varchar(255)`).

Without converters, JPA would require the entity to use `String` for email and password hash directly, leaking persistence concerns into the domain.

---

**10. Why does `User.verifyEmail()` check the current status before transitioning?**

It enforces the state machine defined in the LLD §5:
- `UNVERIFIED → ACTIVE` is the only valid transition for `verifyEmail`.
- Re-verifying an `ACTIVE` account is treated as a no-op (idempotent) because verification links can legitimately be clicked twice.
- Verifying a `DEACTIVATED` account is explicitly rejected.

This is the **State pattern** applied at the domain level — the object knows its valid transitions and guards them.

---

**11. What does "terminal transition" mean for `User.deactivate()`?**

Once a `User` is `DEACTIVATED`, there is no path back to `ACTIVE` or `UNVERIFIED`. The state machine has no reactivation transition per the LLD §5. The code enforces this by throwing `IllegalStateTransitionException` if `deactivate()` is called on an already-deactivated account. The `deactivated_at` timestamp is set and never cleared.

---

**12. Why does `AuthService.register()` save `EmailVerification` and the outbox event instead of `User` doing it directly?**

DDD separates concerns between the **domain layer** (`User` aggregate) and the **application layer** (`AuthService`). The `User` aggregate only knows about its own state — it does not know about email verification tokens, Kafka topics, or Redis. `AuthService` orchestrates: it calls `User.register()` (domain), then creates `EmailVerification` and `OutboxEvent` (infrastructure side effects). This keeps the aggregate free of infrastructure dependencies and makes it easier to test.

---

**13. What is the difference between a domain service and an application service?**

- **Domain service** — contains domain logic that doesn't naturally fit one aggregate. It only uses domain objects and has no infrastructure dependencies (no DB calls, no Kafka).
- **Application service** — orchestrates use cases. It loads aggregates from repositories, calls domain methods, saves results, publishes events. `AuthService` and `AdminUserService` are application services — they call `userRepository.findBy...`, invoke domain methods like `user.login()`, then save and produce outbox events.

---

**14. What is the Ubiquitous Language principle?**

The same terms used by domain experts should be used in code. Examples from this project:
- `User.register()` — not `createUser()`.
- `User.verifyEmail()` — not `activateUser()`.
- `User.deactivate()` — not `disableUser()` or `deleteUser()`.
- `OutboxEvent`, `EmailVerification`, `PasswordHash` — all named after domain concepts.
- Status values: `UNVERIFIED`, `ACTIVE`, `DEACTIVATED` — match the LLD state machine labels.

---

**15. What does `@Version` on `User.version` prevent in a concurrent system?**

It prevents the **lost update problem**. If two requests both read a `User` at version 5 (e.g., two concurrent login attempts that trigger `save(user)` after resetting rate limit), only one will succeed with the `WHERE version = 5` update. The second will see 0 rows updated and throw `OptimisticLockException`. This is safer than a database lock (`SELECT FOR UPDATE`) and scales better under read-heavy loads.

---

### Advanced

**16. Why does cross-context communication happen via domain events (Kafka) rather than direct DB queries or REST calls?**

- **DB sharing** violates bounded context isolation — another service depending on `user_db` couples its schema to the User context.
- **Synchronous REST calls** create temporal coupling — if the Notification service is down, the User service can't complete registration.
- **Domain events via Kafka** achieve loose coupling: the User service publishes `UserRegistered` and continues independently; the Notification service consumes it when ready. Each service owns its data. Failure is localised.

---

**17. What is an anti-corruption layer (ACL)? When would you add one?**

An ACL is a translation layer between two bounded contexts with incompatible models. It prevents concepts from one context from leaking into another. Example: if the Order service receives a `UserRegistered` event but its internal model uses `Customer` instead of `User`, an ACL would translate `UserRegistered → CustomerCreated` and map the fields. In this project, ACLs would be needed when a consuming service (e.g., Cart) has a different vocabulary for the same concepts emitted by User/Auth.

---

**18. What is the difference between choreography-based and orchestration-based sagas?**

- **Choreography** — each service reacts to events and emits its own events; no central coordinator. Used in this project (Kafka-based). Example: `OrderPlaced` → Payment service charges → `PaymentAuthorised` → Inventory service reserves stock → `StockReserved` → Order service marks `CONFIRMED`.
- **Orchestration** — a central saga orchestrator sends commands to services and handles the flow. Used when compensation logic is complex (e.g., Step Functions in Phase 2).

This project uses choreography per ADR-0014 because the flows are straightforward enough and choreography avoids a single point of failure.

---

**19. If you needed to add a `PasswordResetService`, where would it live and what aggregate would it operate on?**

It would be an **application service** in `com.ecommerce.userauth.service`. It would operate on the `User` aggregate (calling `user.resetPassword(newRaw)`) and the `PasswordResetToken` entity (already modelled in `V1__init.sql` as `password_reset_tokens`). The service would: (1) validate the token from `password_reset_tokens`, (2) call `user.resetPassword()`, (3) save the user, (4) call `refreshTokenRepository.revokeAll(userId)` to invalidate all sessions, (5) write a `PasswordResetCompleted` outbox event.

---

**20. What consistency guarantees does the User aggregate give, and where does it break down?**

**Within a single transaction:** strong consistency — `User`, `EmailVerification`, and `OutboxEvent` are saved atomically. Either all commit or all roll back.

**Across services:** only eventual consistency. The `UserRegistered` Kafka event may be delayed by up to 500ms (the outbox relay poll interval). The Notification service may receive it seconds later. If the Notification service is temporarily down, the event is retried from the outbox. There is no distributed transaction across service boundaries — compensating transactions (sagas) handle failures.
