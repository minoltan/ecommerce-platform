# ADR-0007: Apache Kafka over RabbitMQ for Async Messaging

**Status:** Accepted  
**Date:** 2026-06-08  
**Phase:** ARCH  
**Bounded contexts affected:** All (Kafka is the shared event bus)  

---

## Context

The platform requires an async messaging layer for:

1. Domain event publishing between bounded contexts (OrderPlaced → Payment → Inventory)
2. Choreography-based saga coordination (ADR-0005)
3. Durable event log for replay and audit

Two mainstream options were evaluated: **Apache Kafka** and **RabbitMQ**. Both are
battle-tested, have excellent Spring Boot integration (`spring-kafka`, `spring-amqp`),
and support at-least-once delivery.

---

## Decision

**Apache Kafka** is the async messaging layer for Phase 1.

- Topic naming: `{context}.{entity}.{event}` — e.g. `order.order.placed`
- 6 topic groups: `user-auth.*`, `catalog.*`, `cart.*`, `order.*`, `payment.*`, `inventory.*`
- Partition key: `orderId` for order/payment/inventory topics; `userId` for auth
- Replication factor: 3 (production); 1 (local Docker Compose)
- Retention: 7 days default; 30 days for `order.*` and `payment.*`
- Consumer groups: one per service consumer

---

## Consequences

### Positive

- **Durable log.** Kafka retains messages by time (not by acknowledgement). Consumers can
  replay from any offset — essential for rebuilding read models and debugging saga failures.
- **High throughput.** Kafka's sequential disk I/O handles millions of messages per second.
  At the platform's target load (1K orders/day → ~100 events/minute), Kafka is
  heavily over-provisioned — which is acceptable for a learning project.
- **Consumer independence.** Multiple consumer groups can read the same topic independently
  at their own pace. Adding a new consumer (e.g., Fraud Detection) requires zero changes
  to producers.
- **Phase 2 migration.** AWS EventBridge and SQS/SNS are the Phase 2 equivalents. The
  event-driven patterns learned with Kafka transfer directly; topic → EventBridge event
  bus, consumer group → SQS queue with EventBridge rule.
- **Ordering within partition.** All events for a given `orderId` land on the same
  partition (same key) and are processed in order. Critical for the Order state machine.
- **Spring ecosystem.** `spring-kafka` provides `@KafkaListener`, `KafkaTemplate`,
  `ErrorHandler`, and DLT (Dead Letter Topic) out of the box.

### Negative

- **Operationally heavier.** Kafka requires ZooKeeper (or KRaft in newer versions) and
  careful topic configuration. Local Docker Compose adds Kafka + ZooKeeper containers.
  RabbitMQ is a single container.
- **No built-in routing.** Kafka has no exchange/binding model; routing must be implemented
  by topic naming convention and consumer-side filtering. RabbitMQ's exchanges provide
  richer routing (headers, topic patterns) out of the box.
- **Message TTL is topic-level.** Kafka retention applies to all messages on a topic by
  time or size — not per-message TTL. RabbitMQ supports per-message and per-queue TTL,
  useful for expiring short-lived commands.
- **No native request/reply.** Kafka request/reply requires a correlation ID and reply
  topic — significantly more complex than RabbitMQ's `replyTo` property.

---

## Alternatives Rejected

### RabbitMQ

AMQP broker with exchanges (direct, fanout, topic, headers), per-message TTL, and
built-in request/reply (`replyTo` correlation).

Better suited for:
- Low-volume task queues (< 50K messages/sec)
- Rich routing (content-based, header-based)
- Request/reply RPC patterns
- Short-lived messages that should not be retained after ack

Rejected because:
- **No replay.** Once a message is acknowledged by a consumer, it is gone. Saga failure
  debugging and read model rebuilding require replay — a fundamental capability.
- **Consumer coupling.** In RabbitMQ, producers often know the queue or binding key.
  Kafka's log model allows consumers to be added without any producer change.
- **Phase 2 mismatch.** EventBridge is closer in concept to Kafka (event log, fan-out
  to multiple targets) than to RabbitMQ (queue-per-consumer). Kafka experience transfers
  better to the Phase 2 target architecture.

### AWS SNS + SQS (Phase 1)

Native AWS managed messaging, already the Phase 2 target. Starting with SNS/SQS in
Phase 1 would eliminate the Kafka → EventBridge migration step.

Rejected because:
- Phase 1 runs on Kubernetes (local and cloud-agnostic); tight AWS SDK dependency
  complicates local development.
- SNS/SQS has no replay (messages are consumed and deleted); EventBridge has limited
  replay via the event archive feature but with higher operational friction than Kafka.
- Kafka is the industry-standard skill to demonstrate for the architect portfolio.

### Redis Pub/Sub

Extremely low latency, already in the stack (Cart, rate limiting). No additional
infrastructure for simple fan-out.

Rejected because:
- **No durability.** Redis Pub/Sub is fire-and-forget; messages are lost if no subscriber
  is listening at publish time. Unacceptable for financial events.
- **No persistence.** Cannot replay events; cannot replay the Order saga.
- Redis Streams (an alternative) provides persistence but lacks the ecosystem maturity
  and consumer group semantics of Kafka.
