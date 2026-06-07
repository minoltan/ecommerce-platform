# RE-03: Non-Functional Requirements

**Phase:** Requirement Engineering
**Status:** Draft v0.1

---

## 1. Performance / Latency

| ID | Requirement | Target | Measurement Point |
|---|---|---|---|
| NFR-PERF-001 | Product search response time | p95 < 200ms, p99 < 500ms | API Gateway → response |
| NFR-PERF-002 | Product detail page load (API) | p95 < 150ms | API Gateway → response |
| NFR-PERF-003 | Add item to cart | p95 < 100ms | API Gateway → response |
| NFR-PERF-004 | Order placement (sync response) | p99 < 500ms | API Gateway → response |
| NFR-PERF-005 | Payment initiation (gateway call excluded) | p95 < 200ms | Service internal |
| NFR-PERF-006 | Stock reservation (Inventory service) | p99 < 300ms | Service internal |
| NFR-PERF-007 | JWT validation overhead | < 5ms | Per request, in-process |
| NFR-PERF-008 | Kafka event end-to-end latency (producer → consumer) | p99 < 1s | Broker metrics |
| NFR-PERF-009 | Notification dispatch (email queued after event) | < 5s | Event receipt → queue |

---

## 2. Scalability / Throughput

| ID | Requirement | Target | Notes |
|---|---|---|---|
| NFR-SCALE-001 | Concurrent active users (Phase 1 baseline) | 10,000 | Horizontal pod scaling |
| NFR-SCALE-002 | Peak orders per minute | 500 orders/min | Flash sale scenario |
| NFR-SCALE-003 | Product catalog read throughput | 5,000 req/s | Served from Redis cache |
| NFR-SCALE-004 | Cart service write throughput | 2,000 req/s | Redis-backed session |
| NFR-SCALE-005 | Kafka consumer lag at peak | < 10,000 messages | Per consumer group |
| NFR-SCALE-006 | Auto-scaling trigger (Kubernetes) | CPU > 60% for 90s | HPA threshold |
| NFR-SCALE-007 | Database connection pool per service | Max 50 connections | HikariCP |
| NFR-SCALE-008 | Phase 2 serverless burst capacity | 1,000 concurrent Lambdas | AWS account limit |

---

## 3. Availability / Reliability

| ID | Requirement | Target | Scope |
|---|---|---|---|
| NFR-AVAIL-001 | Overall platform uptime | 99.9% (≤ 8.7h downtime/year) | All customer-facing services |
| NFR-AVAIL-002 | Order and Payment services uptime | 99.95% (≤ 4.4h/year) | Revenue-critical path |
| NFR-AVAIL-003 | Notification service uptime | 99.5% | Non-blocking to order flow |
| NFR-AVAIL-004 | Kafka broker availability | 99.95% | 3-broker cluster, replication factor 3 |
| NFR-AVAIL-005 | MySQL availability | 99.95% | Primary + 1 replica, automatic failover |
| NFR-AVAIL-006 | Redis availability | 99.9% | Redis Sentinel or Cluster mode |
| NFR-AVAIL-007 | Graceful degradation — Catalog unavailable | Cart and Order still functional | Circuit breaker pattern |
| NFR-AVAIL-008 | Graceful degradation — Notification unavailable | Order flow unaffected | Async, non-blocking |
| NFR-AVAIL-009 | Maximum planned downtime window | < 30 min/month | Maintenance windows only |
| NFR-AVAIL-010 | Recovery Time Objective (RTO) | < 15 min | Per service |
| NFR-AVAIL-011 | Recovery Point Objective (RPO) | < 5 min | MySQL binlog replication |

---

## 4. Consistency

| ID | Requirement | Target | Notes |
|---|---|---|---|
| NFR-CONS-001 | Order placement: stock reservation atomicity | Saga completes or fully compensates | Choreography-based saga |
| NFR-CONS-002 | Payment idempotency | Zero duplicate charges on retry | Idempotency key per payment |
| NFR-CONS-003 | Cart price accuracy | Price locked at add-to-cart; warn if changed | Snapshot pattern |
| NFR-CONS-004 | Inventory: no negative stock | `availableQty` never below 0 | Optimistic locking |
| NFR-CONS-005 | Cross-service eventual consistency window | < 2s under normal load | Kafka-based propagation |
| NFR-CONS-006 | Order status consistency | Single source of truth in Order service | No direct DB reads across services |

---

## 5. Security

| ID | Requirement | Target | Notes |
|---|---|---|---|
| NFR-SEC-001 | Authentication | JWT (RS256), 15-min access token, 7-day refresh | Asymmetric key rotation supported |
| NFR-SEC-002 | Transport encryption | TLS 1.2+ on all service-to-service and client-to-gateway | No plaintext HTTP in prod |
| NFR-SEC-003 | Password storage | bcrypt, cost factor ≥ 12 | No plaintext or MD5/SHA1 |
| NFR-SEC-004 | PII data at rest | AES-256 encryption for PII fields (email, phone, address) | DB-level or app-level |
| NFR-SEC-005 | Payment data | No raw card data stored; PCI-DSS tokenisation via gateway | Gateway-side vaulting |
| NFR-SEC-006 | API rate limiting | 100 req/min per IP (unauth), 500 req/min per user (auth) | API Gateway / Nginx |
| NFR-SEC-007 | Role-based access control | Enforced at service level via JWT claims | `CUSTOMER`, `ADMIN`, `INVENTORY_MANAGER` |
| NFR-SEC-008 | Input validation | All inputs validated at API boundary; reject malformed payloads | OpenAPI schema validation |
| NFR-SEC-009 | Audit logging | All write operations logged with userId, timestamp, resource | Immutable audit trail |
| NFR-SEC-010 | Secrets management | No credentials in code or environment variables in plain text | Kubernetes Secrets / AWS Secrets Manager |
| NFR-SEC-011 | OWASP Top 10 | Address all applicable items before Phase 1 production launch | Security review gate |
| NFR-SEC-012 | Dependency vulnerability scanning | No critical CVEs in production dependencies | CI/CD gate: OWASP Dependency-Check |

---

## 6. Observability

| ID | Requirement | Target | Notes |
|---|---|---|---|
| NFR-OBS-001 | Structured logging | JSON format, correlation ID on every log line | Centralised log aggregation |
| NFR-OBS-002 | Distributed tracing | Trace ID propagated across all service calls | OpenTelemetry |
| NFR-OBS-003 | Metrics | RED metrics (Rate, Errors, Duration) per service endpoint | Prometheus + Grafana |
| NFR-OBS-004 | Health checks | `/actuator/health` liveness + readiness on all services | Kubernetes probes |
| NFR-OBS-005 | Alerting | PagerDuty / alert on: error rate > 1%, p99 > SLA, consumer lag | < 2 min detection |
| NFR-OBS-006 | Dashboard | Service dependency map, SLA burn rate, Kafka lag | Grafana |

---

## 7. Maintainability / Operability

| ID | Requirement | Target | Notes |
|---|---|---|---|
| NFR-MAINT-001 | Code coverage | ≥ 80% unit test coverage per service | Enforced in CI |
| NFR-MAINT-002 | API versioning | All APIs versioned under `/v1/`; no breaking changes without version bump | |
| NFR-MAINT-003 | Database migrations | Flyway for all schema changes; no manual DDL in production | |
| NFR-MAINT-004 | Zero-downtime deployment | Rolling update strategy in Kubernetes | maxUnavailable: 0 |
| NFR-MAINT-005 | Feature flag support | Critical features behind flags for safe rollout | (Phase 2) |
| NFR-MAINT-006 | Config externalisation | No hardcoded config; all via `application.yml` + ConfigMaps | 12-Factor App |

---

## 8. Compliance

| ID | Requirement | Target | Notes |
|---|---|---|---|
| NFR-COMP-001 | GDPR — right to erasure | User data anonymisable within 30 days of request | Soft delete + anonymise |
| NFR-COMP-002 | GDPR — data portability | User can export their data (profile, orders, addresses) | JSON export endpoint |
| NFR-COMP-003 | PCI-DSS | No card data in application layer | Gateway tokenisation only |
| NFR-COMP-004 | Audit log retention | Retain audit logs for 2 years | Immutable, append-only |
