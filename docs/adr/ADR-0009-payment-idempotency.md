# ADR-0009: Payment Idempotency Key Design

**Status:** Accepted  
**Date:** 2026-06-08  
**Phase:** ARCH  
**Bounded contexts affected:** Payment, Order  

---

## Context

Payment operations (authorise, capture, void, refund) interact with an external payment
gateway (e.g., Stripe, Razorpay). Network failures between the Payment service and the
gateway can leave the system in an ambiguous state:

- Request sent, gateway processed, response lost → Payment service retries → **double charge**
- Request sent, gateway timed out → retry → second authorisation on the same card

At-least-once delivery from Kafka (the saga trigger) means the Payment service may
receive the same `OrderPlaced` event more than once. Each delivery must produce the same
outcome, not a new payment authorisation.

Two failure modes to guard against:
1. **Double charge** — customer billed twice for one order
2. **Double refund** — customer refunded twice for one return

---

## Decision

Every payment operation carries a **client-generated idempotency key** that is:

- Deterministically derived from `orderId` + `operationType`:
  - Authorise: `auth-{orderId}`
  - Capture: `cap-{orderId}`
  - Void: `void-{orderId}`
  - Refund: `ref-{orderId}-{returnId}`
- Stored in `payments.idempotency_key` (UNIQUE constraint in MySQL)
- Sent as a header to the payment gateway (`Idempotency-Key: {key}`)
- Cached in Redis with 24-hour TTL: key `idem:{idempotencyKey}` → cached gateway response

### Processing flow

```
Receive PaymentCommand(orderId, amount, operation)
  → idempotencyKey = "{operation}-{orderId}"
  → check Redis: idem:{idempotencyKey}
      HIT  → return cached result (no gateway call)
      MISS → check MySQL: SELECT * FROM payments WHERE idempotency_key = ?
          FOUND  → return stored result (no gateway call)
          NOT FOUND → call gateway with Idempotency-Key header
                   → store result in MySQL (INSERT)
                   → cache result in Redis (SET EX 86400)
                   → publish PaymentAuthorised | PaymentFailed event
```

### Gateway-side idempotency

Stripe and Razorpay both honour a client-sent `Idempotency-Key` header: if the same key
is sent within 24 hours, the gateway returns the original response without creating a
new charge. This provides defence-in-depth: even if our DB check fails, the gateway
prevents a duplicate charge.

---

## Consequences

### Positive

- **Double charge eliminated.** The UNIQUE constraint on `payments.idempotency_key`
  prevents two rows for the same `auth-{orderId}`. The Redis cache provides a fast path
  that avoids the gateway call entirely on retries.
- **Deterministic keys, no coordination.** The Payment service can compute the key from
  `orderId` alone; no shared counter or UUID coordination needed.
- **Kafka at-least-once safe.** Any number of redeliveries of the same `OrderPlaced`
  event produce the same outcome: the Redis cache or MySQL UNIQUE constraint rejects
  duplicates without a gateway call.
- **Auditability.** `payments.idempotency_key` is stored alongside the gateway response,
  providing a clear record of which key produced which payment.
- **Gateway-level backup.** Even if the Payment service crashes after the gateway
  succeeds but before MySQL is written, a retry with the same key will get the original
  gateway response.

### Negative

- **24-hour Redis TTL gap.** If Redis is cleared and the same key is retried after TTL
  but before the MySQL record is queryable (e.g., read replica lag), the MySQL lookup
  provides the backstop. This path is slower but safe.
- **refund key requires returnId.** Unlike auth/capture/void (which are 1:1 with orderId),
  refunds can be partial (multiple `returnId`s per order). The `ref-{orderId}-{returnId}`
  key handles this. A partial refund race would be caught by the MySQL UNIQUE constraint.
- **Key collision across environments.** If dev and prod share the same payment gateway
  account, keys like `auth-order-123` can collide. Mitigated by prefixing keys with
  environment: `prod-auth-{orderId}`, `dev-auth-{orderId}`.

---

## Alternatives Rejected

### UUID idempotency key (random, not deterministic)

Generate a new UUID for each payment request; store it in the Order's outbox event
payload. On retry, the re-published event carries the same UUID.

Pros: No key derivation logic in Payment service.  
Cons: If the outbox event is replayed from scratch (relay restart), a new UUID is
generated, breaking idempotency protection at the event layer. The deterministic key
approach is safer because it is computable from stable data (orderId + operation) with
no dependency on which specific event delivery is being processed.

Rejected in favour of deterministic keys.

### Database-only idempotency (no Redis cache)

Check only `payments.idempotency_key` (MySQL) before every gateway call. No Redis.

Safer from a cache-invalidation perspective but:
- Every retry hits MySQL + gateway network latency.
- Under high retry volume (e.g., gateway outage causing mass retries), the MySQL lookup
  becomes a bottleneck.
- Redis adds a < 1 ms hot path for the common case (Kafka retry within 24 hours).

Redis is retained as the fast-path cache; MySQL UNIQUE is the durable backstop.

### Exactly-once Kafka (Kafka transactions + EOS)

Use Kafka's exactly-once semantics (idempotent producer + transactional consumer) to
guarantee the payment command is delivered once. Eliminates the need for application-
layer idempotency.

Rejected because:
- Kafka EOS only covers the Kafka delivery itself; it does not prevent the gateway call
  from being made twice if the consumer crashes between the Kafka commit and the gateway
  response being persisted.
- Kafka EOS requires `enable.idempotence=true` and `isolation.level=read_committed`
  across all consumers — significant configuration and throughput overhead.
- Application-layer idempotency is needed regardless (gateway timeout → retry scenario);
  EOS would be belt-and-braces but not sufficient alone.
