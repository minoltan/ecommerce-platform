# ADR-0015: Concurrency Control Strategy — DB/Redis Atomicity Over JVM-Local Locking

**Status:** Accepted
**Date:** 2026-06-23
**Phase:** IMPL
**Bounded contexts affected:** User/Auth (originating case); applies platform-wide

---

## Context

`user-service` (and every Phase 1 service) runs as multiple stateless replicas behind a
load balancer / k8s Service. Implementation of `AuthService.register` and
`RefreshTokenRepository.rotate` surfaced two check-then-act races:

1. **Registration** (`AuthService.register`): `userRepository.existsByEmail(email)` is
   checked, then `userRepository.save(user)` inserts. Two concurrent requests with the
   same email can both pass the check before either inserts.
2. **Refresh-token rotation** (`RefreshTokenRepository.rotate`, LLD §6.2): the old
   implementation did `GET` the stored hash, validate it, then `DEL` the key. Two
   concurrent rotations of the same presented token could both `GET` the value before
   either `DEL`eted it, both pass validation, and both issue a new session for one
   rotation request.

The instinctive fix — wrap the check-then-act in a Java `synchronized` block or a
`java.util.concurrent.ReentrantLock` — does not work here: `synchronized` only
serialises threads inside **one JVM's heap**. With N replicas, two racing requests are
just as likely to land on two different pods as on the same one; an in-JVM lock would
do nothing to prevent the cross-pod race, while still serialising (and therefore
throttling) same-pod traffic for no correctness benefit. The actual race is over shared
state in MySQL and Redis, which live outside any single JVM.

## Decision

Concurrency correctness for cross-instance check-and-mutate operations is enforced at
the data store, never in JVM memory:

1. **Insert-time uniqueness (MySQL):** the `existsByEmail` check is kept as a fast-path
   only — it gives the common case (no race) a clean, immediate 409 without waiting on
   password hashing and an INSERT round-trip — but it is not the correctness guard. The
   actual guard is the `uq_users_email` UNIQUE constraint. The user `save` is changed to
   `saveAndFlush` so the INSERT — and any constraint violation — happens synchronously
   inside `register()`, where a
   `DataIntegrityViolationException` is caught and translated to the existing
   `EmailAlreadyRegisteredException` (→ `409 EMAIL_ALREADY_REGISTERED`), instead of
   leaking out of the transaction boundary as an unhandled 500.
2. **Cross-instance check-and-mutate (Redis):** where a key must be read and
   invalidated as one logical step (refresh-token rotation), use a single atomic Redis
   command instead of separate GET + DEL. `RefreshTokenRepository.rotate` now uses
   `ValueOperations.getAndDelete` (Redis `GETDEL`, available since Redis 6.2; this
   platform runs `redis:7-alpine`). Whichever request's `GETDEL` executes first gets the
   stored hash; any other request hitting the same key afterward gets `null` and
   correctly fails validation.
3. **Update-time conflicts (MySQL):** already covered by the `@Version` optimistic-lock
   column on `User` (`User.java`) — unrelated to this ADR but recorded here as the third
   leg of the strategy: two concurrent updates to the same row raise
   `OptimisticLockingFailureException` rather than silently overwriting each other.

`synchronized` / `java.util.concurrent` primitives (`ReentrantLock`, `Semaphore`,
`AtomicInteger`, etc.) remain the *right* tool only for protecting genuinely in-JVM-only
mutable state (e.g. a local cache, a connection-pool counter) — not present anywhere in
this service today. None are introduced by this decision.

## Consequences

### Positive

- Correctness holds regardless of replica count — the guarantee lives in MySQL/Redis,
  which is the actual point of contention, not in any one pod's heap.
- No throughput cost: `saveAndFlush` and `GETDEL` are single round-trips, not locks held
  across multiple operations; same-pod concurrent requests are not serialised.
- `EmailAlreadyRegisteredException` (409) is now the response for *every* duplicate-email
  path — both the common case (`existsByEmail` hit) and the rare race-loser case
  (constraint violation) — instead of the race-loser getting an opaque 500.
- `GETDEL` is one Redis round trip instead of two (GET + DEL), a minor latency win on top
  of the correctness fix.

### Negative

- `saveAndFlush` forces an immediate flush, giving up Hibernate's batching for this one
  insert. Acceptable: registration is not a hot, high-throughput path, and the
  alternative (deferring the flush to commit time) is exactly what hid the bug, since the
  constraint violation would otherwise surface after `register()` had already returned,
  outside any local try/catch.
- `GETDEL` requires Redis ≥ 6.2; pin the `redis:7-alpine` image (already the case in
  `docker-compose.infra.yml`) and carry this constraint into any future Redis upgrade/
  downgrade decision.
- This ADR does not cover `RateLimitRepository.tryConsume`'s non-atomic
  `INCREMENT`-then-`EXPIRE` (a key without a TTL if the process dies between the two
  calls). Lower severity — a missing-TTL counter is a durability nuisance, not a
  cross-instance correctness break — and is left as a follow-up rather than bundled here.

## Alternatives Rejected

### `synchronized` / `java.util.concurrent` locks in the service layer

Rejected as the primary mechanism: only locks within one JVM. With horizontally-scaled
replicas, the lock holder in pod A is invisible to pod B, so the race reproduces exactly
as before across pods while same-pod requests pay an unnecessary serialisation cost.

### Distributed lock (Redisson `RLock` / Redis `SET NX` lock)

Would work for the rotation race, but is strictly more machinery than needed: the
rotation race is a single read-modify-delete over one key, which Redis already provides
atomically via `GETDEL`. A distributed lock adds a second round trip, a lease-expiry
failure mode, and a dependency (Redisson) for a problem a built-in atomic command already
solves. Reserved for cases where the critical section spans multiple keys/operations that
no single atomic command covers.

### Pessimistic DB locking (`SELECT ... FOR UPDATE`) for registration

Would serialise concurrent registrations against the same email, but requires a
`SELECT ... FOR UPDATE` against a row that doesn't exist yet for the race to even apply,
i.e. it doesn't help here. The UNIQUE constraint already gives the same guarantee for
free, with no explicit locking and no held row/gap locks to manage.

### Letting `existsByEmail` be the sole guard

Rejected as relying on a check-then-act read with no atomicity guarantee — the precise
bug being fixed. Kept only as a fast-path UX optimisation in front of the real guard.
