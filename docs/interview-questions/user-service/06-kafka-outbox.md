# Apache Kafka & Outbox Pattern — Interview Questions & Answers

---

### Basic

**1. What is Apache Kafka? What problem does it solve?**

Kafka is a distributed event streaming platform. It solves reliable, high-throughput, decoupled communication between services:
- Producers write events to topics; consumers read at their own pace.
- Events are persisted on disk and replicated — not lost if a consumer is down.
- Multiple consumers can independently read the same topic (fan-out).
- Supports replay — consumers can reprocess historical events.

In this project, Kafka carries domain events (`UserRegistered`, `UserLoggedIn`, etc.) between bounded contexts.

---

**2. What is a topic, partition, and consumer group?**

- **Topic** — a named, ordered stream of records (e.g., `user-auth.user-registered`).
- **Partition** — a topic is split into N partitions. Records within one partition are strictly ordered. Records with the same key always go to the same partition.
- **Consumer group** — a set of consumers that jointly consume a topic. Each partition is assigned to exactly one consumer in the group. This enables parallel consumption while guaranteeing ordered processing per partition.

---

**3. What is the difference between at-least-once, at-most-once, and exactly-once delivery?**

- **At-most-once** — producer sends without confirmation; consumer auto-commits offsets before processing. Events may be lost, never duplicated.
- **At-least-once** — producer waits for ack; consumer commits offset after processing. Events may be duplicated on retry but never lost. Used in this project (outbox + `acks: all`).
- **Exactly-once** — transactional producers + idempotent consumers. No duplicates, no loss. More complex and slower; requires consumer idempotency.

---

**4. What is a domain event? Give an example from this project.**

A domain event is a record of something significant that happened in the domain. From `AuthService`:
- `UserRegistered` — fired in `register()`, carries `userId`, `email`, `verificationToken`.
- `UserLoggedIn` — fired in `login()`, carries `userId`, `email`.

These are written to `user_auth_outbox` as `OutboxEvent` rows and published to Kafka by `OutboxRelay` as `OutboxEventEnvelope` JSON with `eventId`, `eventType`, `occurredAt`, `correlationId`, `schemaVersion`, and `data`.

---

### Intermediate

**5. What is the Transactional Outbox pattern? Why is it used?**

The dual-write problem: you can't atomically write to a database AND publish to Kafka in one transaction (they are separate systems). Naïve approach — save to DB then publish — risks the publish failing after the DB commit (event lost). Or publish first then DB fails (event sent without DB change).

The outbox pattern solves this:
1. Write the domain change (e.g., new `User`) AND the event (`OutboxEvent`) in **one DB transaction**.
2. A separate `OutboxRelay` polls the outbox table and publishes events to Kafka.
3. If Kafka publish fails, the relay retries — the DB row is not marked `published = true`.

This guarantees at-least-once delivery to Kafka with no event loss.

---

**6. Walk through the lifecycle of a `UserRegistered` event.**

1. `POST /v1/auth/register` arrives at `AuthController`.
2. `AuthService.register()` begins a `@Transactional` DB transaction.
3. `User.register()` creates the aggregate → saved to `users` table.
4. `EmailVerification.issue()` → saved to `email_verifications` table.
5. `writeOutboxEvent("UserRegistered", {...})` → saved to `user_auth_outbox` with `published = false`.
6. Transaction commits — all three rows land atomically.
7. `OutboxRelay.publishUnpublishedEvents()` fires within ≤500ms (scheduled poll).
8. Reads the `UserRegistered` row, calls `kafkaTemplate.send("user-auth.user-registered", userId, envelope)`.
9. Waits up to 5 seconds for Kafka ack.
10. On success: `event.markPublished(now)` → saves `published = true`, `published_at = now`.
11. Notification service consumer receives the event and sends the verification email.

---

**7. Why is `OutboxRelay` annotated with `@Scheduled` rather than publishing directly in `AuthService`?**

Direct publish in `AuthService.register()` would require Kafka to be available at write time:
- If Kafka is down, registration fails even though the DB write succeeded.
- No retry mechanism — the event is lost on failure.

`OutboxRelay` decouples the write path from the publish path:
- Registration always succeeds as long as MySQL is available.
- Kafka publish is retried automatically on each poll cycle until it succeeds.
- The system is resilient to Kafka downtime for up to the outbox retention window.

---

**8. What is the `aggregateId` used as the Kafka message key, and why?**

`OutboxRelay` publishes with `key = event.getAggregateId().toString()` (the `userId`). Per ADR-0002, all events for the same user go to the same Kafka partition (keyed by `userId`). This guarantees **ordering within a user's event stream** — `UserRegistered` is always consumed before `UserLoggedIn` for the same user. Without a key, events would be distributed round-robin across partitions and ordering would not be guaranteed.

---

**9. What does `kafkaTemplate.send(...).get(5, TimeUnit.SECONDS)` do?**

`kafkaTemplate.send()` returns a `CompletableFuture`. `.get(5, TimeUnit.SECONDS)` blocks the relay thread for up to 5 seconds waiting for the Kafka broker to acknowledge the message. If the broker does not ack within 5 seconds, a `TimeoutException` is thrown and the outbox row remains `published = false` for retry. This makes the publish synchronous per event — simpler but lower throughput than async batching.

---

**10. What happens if the Kafka send fails in `OutboxRelay`?**

The `catch (Exception e)` block logs the error and continues to the next event. The failed event's `published` column remains `false`. On the next poll (≤500ms), `findTop100ByPublishedFalseOrderByCreatedAtAsc()` picks it up again for retry. The event is retried indefinitely until Kafka acknowledges it. This gives at-least-once delivery semantics — the event will eventually be published. Consumers must be idempotent to handle duplicates (e.g., if the Kafka ack was received but the DB update failed).

---

**11. What is `acks: all` in the Kafka producer config?**

`acks: all` (equivalent to `acks: -1`) means the broker leader waits for acknowledgement from **all in-sync replicas (ISR)** before sending ack to the producer. This provides the strongest durability guarantee — the message is not lost even if the leader crashes immediately after the produce, as long as at least one replica is up. Trade-off: higher latency than `acks: 1` (leader only) or `acks: 0` (no ack).

---

**12. What is KRaft mode in Kafka? How does it differ from ZooKeeper mode?**

KRaft (Kafka Raft Metadata) is Kafka's built-in consensus mechanism, replacing the dependency on Apache ZooKeeper for metadata management. The `docker-compose.infra.yml` runs Kafka in KRaft mode (`KAFKA_PROCESS_ROLES: broker,controller`). Benefits:
- Single process — no separate ZooKeeper cluster to manage.
- Faster controller failover.
- Simpler deployment for local dev.

ZooKeeper mode required running a separate ZooKeeper ensemble (minimum 3 nodes for HA) alongside Kafka.

---

**13. What is `auto.create.topics.enable`? Why is it `true` locally but might be `false` in production?**

`auto.create.topics.enable=true` (set in `docker-compose.infra.yml`) lets Kafka create topics automatically when a producer first writes to them. Convenient for local dev — no manual topic creation needed.

In production, `false` is preferred:
- Prevents accidental topic creation due to typos in topic names.
- Topics should be created deliberately with correct partition counts and replication factors via infrastructure-as-code.
- A misconfigured topic (wrong partitions) is hard to fix after data is in it.

---

### Advanced

**14. What is the dual-write problem that the outbox pattern solves?**

You need to atomically update the database AND publish an event. These are two separate systems — there is no distributed transaction spanning both. Possible failure modes:
1. DB succeeds, Kafka publish fails → event lost, downstream services never notified.
2. Kafka publish succeeds, DB fails → event sent without the corresponding DB change.

The outbox pattern collapses both writes into **one DB transaction** (the event is written to the outbox table, not directly to Kafka). The relay then reads from the outbox and publishes. The DB is the source of truth — if Kafka fails, retry from the DB. No event is ever lost.

---

**15. If `OutboxRelay` polls every 500ms and publishes up to 100 events per poll, what is the max throughput?**

Maximum: 100 events / 0.5s = **200 events/second** per relay instance. Bottlenecks:
- `.get(5, TimeUnit.SECONDS)` is synchronous — each event is published sequentially. With 5s timeout per event, the actual max is min(100/0.5s, 100/avg_kafka_latency).
- Kafka latency is typically <10ms locally → ~10,000 events/s theoretical max, but limited to 200/s by batch size.

To increase throughput: (1) increase batch size from 100, (2) use async `kafkaTemplate.send()` with callbacks instead of `.get()`, (3) run multiple relay instances with partitioned polling.

---

**16. What is consumer idempotency? Why must consumers of `UserRegistered` be idempotent?**

The outbox relay may publish the same event more than once (if Kafka acked but the DB update to `published = true` failed before commit). Consumers must handle duplicate events without side effects. Example: the Notification service receiving `UserRegistered` twice must not send two verification emails. Idempotency strategies:
- Check if the `eventId` has already been processed (store processed event IDs).
- Make the operation naturally idempotent (e.g., `INSERT IGNORE` or `UPSERT`).

---

**17. What is log compaction in Kafka? Would it be appropriate for `user-auth.user-registered`?**

Log compaction retains only the **latest record per key** within a topic. Older records with the same key are deleted during compaction. This is appropriate for **state topics** (e.g., a topic representing current user profile).

For `user-auth.user-registered`, compaction is **not appropriate** — it is an event topic, not a state topic. Every `UserRegistered` event is a distinct occurrence that must be delivered to all consumers. Compacting it would cause downstream consumers to miss registration events for users who later had their key overwritten.

---

**18. How would you implement exactly-once semantics end-to-end?**

1. **Idempotent producer** — `enable.idempotence=true` on the Kafka producer. Kafka deduplicates retries by producer ID + sequence number.
2. **Transactional producer** — wrap produce + consumer offset commit in a Kafka transaction (`producer.beginTransaction()`, `producer.commitTransaction()`).
3. **Idempotent consumer** — consumer checks a processed-events table before acting on an event.

Exactly-once is complex and adds latency. For this project's requirements, at-least-once + idempotent consumers is the pragmatic choice per ADR-0012.

---

**19. What is ADR-0002's decision on partition key for `user-auth.*` topics?**

ADR-0002 establishes `userId` as the partition key for all `user-auth.*` topics. This guarantees that all events for a given user are processed in order by the same consumer partition. Example: `UserRegistered` → `UserLoggedIn` → `UserDeactivated` for user X will always arrive in that order to the same consumer, enabling correct state reconstruction. Without this, events for the same user could land in different partitions and be processed out of order.
