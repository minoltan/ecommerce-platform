# API Gateway Design — High-Level Design

**Artefact type:** C4 Level 2 supplement — API Gateway cross-cutting design
**Phase:** ARCH
**Status:** Draft
**Version:** 0.1
**Date:** 2026-06-11
**Author:** System Architect
**Inputs:** `docs/hld/container-diagram.md` §3/§8/§9, `docs/hld/system-context.md`, `docs/requirements/non-functional-requirements.md` (NFR-PERF-*, NFR-SCALE-*, NFR-SEC-002/005/006), all seven `docs/api-specs/*-service-api.yaml`

---

## 1. Scope

`container-diagram.md` §8 already states the API Gateway's high-level responsibilities
(TLS termination, JWT validation, rate limiting, path-prefix routing, webhook routing,
admin routing) and §9 states the inter-service communication rules (Kubernetes DNS for
synchronous service-to-service calls, never via the public gateway).

This document expands those into an **implementable design**:

1. **API versioning strategy** — URL vs header versioning, and the deprecation policy
   for breaking changes.
2. **Full routing table** — every path prefix → target service, consolidated from the
   seven `*-service-api.yaml` specs (which were written independently and have not
   previously been cross-checked against each other for prefix collisions).
3. **Rate limiting policy** — tiers, keys, and the NFR-SEC-006 vs container-diagram.md
   §8 discrepancy (flagged below).
4. **CORS and request-handling policy** — needed because the system context
   (`system-context.md`) names a browser-based web client as the primary actor.
5. **Internal service discovery** — confirms Kubernetes DNS (no service mesh) for
   Phase 1, with the trade-off against Istio/Linkerd made explicit.

It does **not** redesign the gateway's container placement (Spring Cloud Gateway,
already decided in container-diagram.md §3/OQ-CD-01) or the JWT validation mechanism
itself (ADR-0011).

---

## 2. API Versioning Strategy

### Decision: URL path versioning (`/api/v1/...`), not header versioning

All seven `*-service-api.yaml` specs already use `/api/v1/` as the path prefix
(`servers:` block in each spec) — this document **formalises that existing,
de-facto choice** rather than introducing a new one.

| Approach | Why rejected / accepted |
|---|---|
| **URL path versioning (`/api/v1/products`)** — **chosen** | Visible in logs, browser dev tools, and API Gateway routing rules without inspecting headers — simplifies the gateway's path-prefix routing (§3) and makes side-by-side `v1`/`v2` deployments trivial (separate route rules, separate backend deployments or separate controller packages in the same service). Cacheable by URL (relevant for Product Catalog's `GET /products` — see `product-catalog-lld.md` §6, search cache `search:*`). |
| Header versioning (`Accept: application/vnd.ecommerce.v1+json`) | Rejected — invisible in URL-based tooling (curl examples, Postman collections, browser navigation for `GET` debugging), and every gateway route rule would need header-based matching instead of simple path-prefix matching, adding gateway config complexity for no benefit at this scale. |
| Query-param versioning (`?version=1`) | Rejected — easy to omit accidentally (defaults are ambiguous), and mixes versioning with legitimate query parameters (e.g., `GET /products?version=1&category=footwear`). |

### Deprecation policy

1. A new major version (`/api/v2/...`) is introduced only for **breaking changes**
   (field removal, type change, renamed required field, removed endpoint) — same
   threshold ADR-0002 §6 uses for Kafka topic versioning (`order.order.placed.v2`).
   Additive, backward-compatible changes (new optional fields, new endpoints) are
   shipped under the existing `/api/v1/` prefix.
2. When `/api/v2/` ships for a service, `/api/v1/` remains live for a **minimum 90-day
   migration window**. Responses on the deprecated version include a `Deprecation:
   true` and `Sunset: <date>` HTTP header (RFC 8594).
3. The API Gateway routes both `/api/v1/{service}/**` and `/api/v2/{service}/**` to the
   same backend service — version coexistence is handled by versioned controller
   packages within the service (`com.ecommerce.{context}.api.v1`,
   `...api.v2`), not separate deployments. This keeps the gateway routing table
   (§3) version-agnostic: one rule per service, not one per version.

---

## 3. Routing Table

Consolidated from the `servers:`/`paths:` blocks of all seven API specs and
`container-diagram.md` §3. All paths are prefixed `/api/v1` per §2 unless marked
**(no prefix)**.

| Path prefix | Target service | Auth | Notes |
|---|---|---|---|
| `/api/v1/auth/**` | User/Auth Service | Public (login/register) + Bearer (refresh/logout) | `user-service-api.yaml` |
| `/api/v1/users/**` | User/Auth Service | Bearer | Self-service profile; `/admin/users/**` sub-tree requires `ADMIN` role |
| `/api/v1/products/**` | Product Catalog Service | Public (read) + Bearer `ADMIN` (write) | `catalog-service-api.yaml`; includes `/products/{id}/variants`, `/products/{id}/publish`, `/products/{id}/unpublish`, `/products/{id}/price` (SA-021) |
| `/api/v1/categories/**` | Product Catalog Service | Public (read) + Bearer `ADMIN` (write) | Includes `/categories/{id}` PATCH/DELETE (SA-021) |
| `/api/v1/cart/**` | Cart Service | Bearer (user) or `X-Guest-Session-Id` header (guest) | `cart-service-api.yaml`; guest identification is **not** a JWT — see OQ-AGW-01 |
| `/api/v1/orders/**` | Order Service | Bearer | `order-service-api.yaml` |
| `/api/v1/payments/**` | Payment Service | **Internal only — not gateway-routed** | `container-diagram.md` §3 marks this "(internal)"; Order Service calls it via Kubernetes DNS (§5). Gateway returns 404 for any `/api/v1/payments/**` request from outside the cluster |
| `/webhooks/payment` **(no prefix)** | Payment Service | None (HMAC verified inside service) | Bypasses JWT validation per `container-diagram.md` §8; gateway forwards `POST /webhooks/payment` unauthenticated, payload-signature checked by Payment Service itself |
| `/api/v1/inventory/**` | Inventory Service | Bearer `ADMIN` (write/admin endpoints) + **internal-only read** | `inventory-service-api.yaml`; `GET /inventory/availability` (cart-lld.md §6, OQ-LLD-IN-05) is called by Cart Service via Kubernetes DNS, not the gateway — same internal/external split as Payment |
| `/api/v1/notifications/**` | Notification Service | Bearer `ADMIN` (admin) + Bearer (user preferences) | `notification-service-api.yaml` |

### Collision check

No two service specs claim an overlapping path prefix — each bounded context's API
spec uses its own top-level resource noun (`/products`, `/categories`, `/cart`,
`/orders`, `/payments`, `/inventory`, `/notifications`, `/auth`, `/users`), consistent
with `container-diagram.md` §3's "Exposes" column. **No routing conflicts found.**

### Internal-only endpoints

Two endpoint groups (`/api/v1/payments/**`, parts of `/api/v1/inventory/**`) exist in
their service's OpenAPI spec but must **not** be reachable through the public gateway —
this matches `container-diagram.md` §9's "Gateway call: Never service-to-service via
gateway" rule, applied in the *inbound* direction too: internal-only endpoints are
called via Kubernetes DNS by other services, and the gateway's routing config must
explicitly exclude them (deny-by-default for these prefixes, or split into
`{service}-internal` vs `{service}-external` OpenAPI tags so the gateway's route
generator only wires up `external`-tagged operations). **Tracked as OQ-AGW-02** —
the seven API specs do not currently distinguish internal vs external operations with
a tag, so gateway route generation cannot yet be automated from the specs.

---

## 4. Rate Limiting Policy

`container-diagram.md` §8 states: "1,000 req/min per authenticated user; 100 req/min
per IP for unauthenticated." `non-functional-requirements.md` NFR-SEC-006 states:
"100 req/min per IP (unauth), **500** req/min per user (auth)."

**This is a numeric discrepancy — tracked as OQ-AGW-03.** This document adopts
**container-diagram.md's 1,000 req/min (authenticated)** as canonical for the gateway
implementation, because:

- It is the more recent, implementation-adjacent artefact (per this project's
  reconciliation pattern — see `cart-lld.md` §6 precedent).
- 1,000 req/min ≈ 16.7 req/s per user comfortably covers the burst pattern of an
  active shopping session (cart mutations at NFR-SCALE-004's 2,000 req/s
  *system-wide* cart write throughput, divided across thousands of concurrent users).
- 500 req/min (NFR-SEC-006) would throttle a user mid-checkout if the SPA polls order
  status or cart state aggressively (e.g., 1 poll/sec = 60/min just for one feature).

`non-functional-requirements.md` NFR-SEC-006 should be amended to 1,000 req/min in a
follow-up requirements-doc sync (OQ-AGW-03).

### Implementation

| Tier | Key | Limit | Backing store |
|---|---|---|---|
| Unauthenticated | Client IP | 100 req/min | Redis token bucket (`rate:{ip}:{endpoint}`, per `container-diagram.md` §6) |
| Authenticated | `userId` (from validated JWT `sub` claim) | 1,000 req/min | Redis token bucket (`rate:{userId}:{endpoint}`) |
| Admin | `userId` + `ADMIN` role claim | 1,000 req/min (no separate tier — admin UI is low-volume relative to customer traffic) | Same as authenticated |
| Webhook (`/webhooks/payment`) | Source IP allowlist (payment gateway's documented IP ranges) | Not rate-limited at gateway — HMAC + idempotency key (ADR-0009) is the abuse control | N/A |

On limit exceeded: gateway returns `HTTP 429 Too Many Requests` with a `Retry-After`
header (seconds until the token bucket refills).

---

## 5. CORS Policy

`system-context.md` names a browser-based web client (and, per Phase 2 discussion, a
mobile app) as primary actors. The gateway terminates CORS centrally so individual
Spring Boot services do not each implement `@CrossOrigin`:

| Setting | Value |
|---|---|
| `Access-Control-Allow-Origin` | Configured allowlist (web app origin(s) per environment — `https://app.ecommerce.example` in prod, `http://localhost:5173` in local dev) |
| `Access-Control-Allow-Methods` | `GET, POST, PATCH, PUT, DELETE, OPTIONS` |
| `Access-Control-Allow-Headers` | `Authorization, Content-Type, X-Guest-Session-Id, X-Correlation-Id` |
| `Access-Control-Allow-Credentials` | `true` (cookies not used for auth — JWT is in `Authorization` header — but reserved for future refresh-token-as-cookie consideration, OQ-LLD-UA series) |
| `Access-Control-Max-Age` | `3600` (preflight cache) |

Mobile app clients (native iOS/Android, Phase 2 consideration) do not send an `Origin`
header and are unaffected by CORS — they are subject only to the JWT/rate-limit rules
above.

---

## 6. Internal Service Discovery: Kubernetes DNS (no service mesh)

### Decision: Kubernetes DNS, confirmed for Phase 1

`container-diagram.md` §9 already states the convention: synchronous inter-service
calls (e.g., Cart → Inventory `GET /inventory/availability`, cart-lld.md §6) use the
target's Kubernetes Service DNS name (`inventory-service.default.svc.cluster.local`),
not the public gateway. This document confirms that choice and makes the rejected
alternative explicit, since the ClickUp task for this artefact specifically asked for
a DNS-vs-mesh comparison.

| Approach | Assessment |
|---|---|
| **Kubernetes DNS + ClusterIP Services** — **chosen** | Zero additional infrastructure — every Kubernetes cluster provides this natively (CoreDNS). Sufficient for the project's east-west traffic needs: the only synchronous cross-service calls are Cart→Inventory (availability pre-check, cart-lld.md §6) and Order→Payment (payment authorisation, order-lld.md). Both are simple request/response with retries handled at the calling service's HTTP client (Resilience4j circuit breaker). |
| Service mesh (Istio / Linkerd) | Rejected for Phase 1. A mesh's value-add — mTLS between pods, fine-grained traffic shifting (canary/blue-green), per-service observability (sidecar metrics) — is real but: (1) mTLS is already covered end-to-end by NFR-SEC-002 at the ingress/gateway level for external traffic, and intra-cluster traffic is treated as a trusted zone for this portfolio's threat model; (2) the operational complexity of running and debugging a sidecar-injected mesh is disproportionate to **two** synchronous internal call paths; (3) canary/traffic-shifting is more naturally demonstrated via the API Gateway's versioned routing (§2) for this project's learning goals. **Revisit if Phase 1 grows beyond ~2 synchronous internal call paths**, or explicitly as a Phase 7 (Observability & Production Hardening) stretch goal — noted as OQ-AGW-04. |

### Resilience for the two internal call paths

| Call | Pattern | Failure behaviour |
|---|---|---|
| Cart → Inventory `GET /inventory/availability` (cart-lld.md §6, Option C) | Synchronous read-only pre-check | On timeout/5xx: Cart proceeds to checkout anyway (the pre-check is optimistic — Saga C, `sequence-diagrams.md` SD-08, is the correctness backstop). Circuit breaker (Resilience4j) opens after N consecutive failures to avoid cascading latency into checkout's NFR-PERF-004 (p99 < 500ms) budget |
| Order → Payment authorisation | Synchronous, in the critical path of `OrderPlaced` saga step | On timeout/5xx: Order Service does **not** retry synchronously (would blow the p99 < 500ms budget) — order is created in `PENDING_PAYMENT` and the saga's existing `PaymentExpired` TTL (15 min, ADR pending per event-storming H-OR-1) handles the failure asymptotically |

---

## 7. Phase 2 Delta

| Phase 1 | Phase 2 equivalent | Key difference |
|---|---|---|
| Spring Cloud Gateway, path-prefix routing (§3) | AWS API Gateway (HTTP API), route per Lambda integration | Versioning (§2) maps to API Gateway stages (`v1`, `v2`) instead of path-prefix rules on a single deployment |
| Redis token-bucket rate limiting (§4) | API Gateway usage plans + throttling (per-API-key or per-Cognito-claim) | Managed; no Redis dependency for this concern |
| CORS at Spring Cloud Gateway (§5) | CORS configuration on API Gateway resource | Same policy values, different config surface |
| Kubernetes DNS for internal calls (§6) | Lambda-to-Lambda via direct invoke or Step Functions (order/payment orchestration) | "Internal call" concept mostly disappears — Step Functions orchestrates what was a synchronous HTTP call in Phase 1 (consistent with the Phase 2 saga shift to orchestration, container-diagram.md §11) |

---

## 8. Open Questions

| ID | Item | Owner | Status |
|---|---|---|---|
| OQ-AGW-01 | Guest cart identification at the gateway: cart-service-api.yaml uses a guest session identifier (cart-lld.md `cart:guest:{sessionId}`) that is not a JWT — confirm whether this is a custom header (`X-Guest-Session-Id`, assumed here) or a signed cookie, and how the gateway distinguishes "no auth required" for `/api/v1/cart/**` guest requests vs requiring a session id | Architect | Open |
| OQ-AGW-02 | The seven `*-service-api.yaml` specs do not tag operations as internal-only vs externally-routable (§3) — needed to auto-generate gateway routing config and exclude `/api/v1/payments/**` and internal `/api/v1/inventory/**` operations from public routing | Architect | Open |
| OQ-AGW-03 | Numeric mismatch: `container-diagram.md` §8 says 1,000 req/min for authenticated users; `non-functional-requirements.md` NFR-SEC-006 says 500 req/min. This document adopts 1,000 (§4) — NFR doc needs amending to match | Architect | Open |
| OQ-AGW-04 | Service mesh (Istio/Linkerd) deferred for Phase 1 (§6) — revisit if internal synchronous call paths grow beyond Cart→Inventory and Order→Payment, or as a Phase 7 observability stretch goal | Architect | Open — low priority |

---

## 9. Next Artefacts

| Artefact | Description |
|---|---|
| **`docs/hld/deployment-architecture.md`** (SA-023) | Kubernetes deployment architecture — namespace strategy, HPA per service, resource quotas, Ingress controller (which fronts the Spring Cloud Gateway described here), ConfigMap/Secret management, cluster topology. The Ingress controller setup in that document is the entry point in front of the API Gateway designed here |
| **Phase 1 implementation scaffolding** | Per `WORKFLOW.md` and the seven LLDs' "Next Artefacts" sections — multi-module Maven project, per-service Dockerfiles/k8s manifests, Flyway migrations. The routing table (§3) and versioning convention (§2) in this document are direct inputs to the Spring Cloud Gateway module's route configuration |
