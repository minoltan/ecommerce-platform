# Kubernetes Deployment Architecture — High-Level Design

**Artefact type:** C4 Level 2 supplement — Deployment / Infrastructure design
**Phase:** ARCH
**Status:** Draft
**Version:** 0.1
**Date:** 2026-06-11
**Author:** System Architect
**Inputs:** `docs/hld/container-diagram.md` §3/§4, `docs/hld/api-gateway-design.md`, `docs/requirements/non-functional-requirements.md` (NFR-AVAIL-*, NFR-SCALE-*, NFR-SEC-010, NFR-MAINT-006), `CLAUDE.md` (`kubectl apply -k phase1/k8s/overlays/local`)

---

## 1. Scope

`container-diagram.md` §3/§4 inventories the 7 microservices, API Gateway, Kafka,
Redis, and 6 MySQL schemas, and `api-gateway-design.md` designs the gateway that sits
in front of them. This document defines the **Kubernetes deployment shape** that hosts
all of it for Phase 1:

1. **Namespace strategy**
2. **Per-service deployment sizing** — replica counts, resource requests/limits, HPA
   config (NFR-SCALE-006)
3. **Stateful infrastructure placement** — Kafka, MySQL, Redis: in-cluster
   StatefulSets vs external managed services
4. **Ingress controller setup** — fronting the Spring Cloud Gateway from
   `api-gateway-design.md`
5. **ConfigMap / Secret management** (NFR-SEC-010, NFR-MAINT-006)
6. **Cluster topology** — node pools, availability zones
7. **Kustomize layout** matching the `kubectl apply -k phase1/k8s/overlays/local`
   command already referenced in `CLAUDE.md`

It does **not** redesign the services themselves (per-service LLDs, `docs/lld/`) or
the CI/CD pipeline that builds the images deployed here (Phase 5, OPS-* tasks).

---

## 2. Namespace Strategy

| Namespace | Contents | Rationale |
|---|---|---|
| `ecommerce` | All 7 microservices + API Gateway (Spring Cloud Gateway) | Single application namespace for Phase 1 — the bounded contexts are not separately operated teams, so per-context namespaces (`order`, `payment`, ...) would add RBAC/NetworkPolicy overhead without an organisational boundary to justify it. Revisit only if Phase 1 grows into a multi-team exercise |
| `ecommerce-infra` | Kafka (StatefulSet), Redis (StatefulSet), MySQL instances (StatefulSets, one per bounded context's schema per ADR-0007) | Separates long-lived stateful infrastructure from the frequently-redeployed application services — different `kubectl rollout` cadence, different PodDisruptionBudget needs, and lets infra be provisioned/torn down independently of app code during local dev |
| `monitoring` | Prometheus, Grafana, (Phase 7: Jaeger, ELK) | Standard convention — observability stack outlives individual app deployments and is shared across namespaces via ServiceMonitor/scrape configs |
| `ingress-nginx` | Ingress controller (§5) | Cluster-wide ingress controllers conventionally run in their own namespace (matches the upstream `ingress-nginx` Helm chart's default) |

**NetworkPolicy:** `ecommerce` namespace pods may reach `ecommerce-infra` (Kafka,
Redis, MySQL — both DB hostnames are namespace-qualified, e.g.
`order-mysql.ecommerce-infra.svc.cluster.local`, consistent with
`api-gateway-design.md` §6's Kubernetes-DNS convention) and `monitoring` (metrics
scrape). Cross-service calls within `ecommerce` are restricted to the two paths
identified in `api-gateway-design.md` §6 (Cart→Inventory, Order→Payment) — a default-deny
NetworkPolicy with explicit allow rules for those two paths plus gateway→service
ingress.

---

## 3. Per-Service Deployment Sizing

Replica counts derive from `non-functional-requirements.md`'s per-service availability
tiers (NFR-AVAIL-001/002/003) — a Deployment needs **at least 2 replicas** to survive a
single pod eviction without downtime, and revenue-critical services (Order, Payment)
get a third for headroom during rolling updates.

| Service | Min replicas | Max replicas (HPA) | Requests (CPU/mem) | Limits (CPU/mem) | Availability tier |
|---|---|---|---|---|---|
| API Gateway | 2 | 6 | 250m / 512Mi | 500m / 1Gi | 99.9% (NFR-AVAIL-001) — single point of ingress, so floor of 2 is non-negotiable |
| User/Auth Service | 2 | 4 | 250m / 512Mi | 500m / 1Gi | 99.9% |
| Product Catalog Service | 2 | 6 | 250m / 512Mi | 500m / 1Gi | 99.9% — highest read throughput (NFR-SCALE-003, 5,000 req/s), widest HPA ceiling |
| Cart Service | 2 | 6 | 250m / 512Mi | 500m / 1Gi | 99.9% — highest write throughput (NFR-SCALE-004, 2,000 req/s) |
| Order Service | 3 | 8 | 500m / 1Gi | 1 / 2Gi | **99.95%** (NFR-AVAIL-002) — saga coordinator; higher mem limit for `order_saga_state` tracking (ADR-0014) |
| Payment Service | 3 | 6 | 500m / 1Gi | 1 / 2Gi | **99.95%** (NFR-AVAIL-002) |
| Inventory Service | 2 | 6 | 250m / 512Mi | 500m / 1Gi | 99.9% — also receives Cart's synchronous availability pre-check (`api-gateway-design.md` §6) |
| Notification Service | 2 | 4 | 250m / 512Mi | 500m / 1Gi | 99.5% (NFR-AVAIL-003) — lowest tier, smallest HPA ceiling; async consumer-only workload |

### HPA configuration (NFR-SCALE-006)

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: <service>-hpa
  namespace: ecommerce
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: <service>
  minReplicas: <per table above>
  maxReplicas: <per table above>
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 60     # NFR-SCALE-006: CPU > 60% for 90s
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 90  # matches NFR-SCALE-006's 90s sustained threshold
    scaleDown:
      stabilizationWindowSeconds: 300 # avoid flapping after a burst (e.g., flash sale, NFR-SCALE-002)
```

**Order/Payment-specific note:** the 12-partition `orderId`-keyed Kafka topics
(ADR-0002 §2) cap useful consumer parallelism at 12 — `maxReplicas: 8` for Order and
`6` for Payment stay under that ceiling so HPA never creates idle consumer instances
that cannot be assigned a partition.

### Pod Disruption Budgets

Every Deployment gets a `PodDisruptionBudget` with `minAvailable: 1` (or
`minAvailable: 2` for Order/Payment given their 3-replica floor) — ensures voluntary
disruptions (node drains during cluster upgrades) never take a service below its
availability floor.

---

## 4. Stateful Infrastructure Placement

| Component | Phase 1 placement | Rationale |
|---|---|---|
| **Kafka** (3 brokers, RF=3, Kraft mode) | In-cluster StatefulSet, `ecommerce-infra` namespace, `volumeClaimTemplates` on a `ReadWriteOnce` SSD-backed StorageClass | ADR-0002/container-diagram.md §5 already assume a self-managed 3-broker cluster; running it in-cluster (vs. a managed Kafka service) is consistent with this project's "operate it yourself to learn the operational trade-offs" goal (CLAUDE.md project purpose) |
| **MySQL** (6 instances — one per schema-isolated service per ADR-0007: `user_db`, `catalog_db`, `order_db`, `payment_db`, `inventory_db`, `notification_db`; Cart has no MySQL DB per ADR-0010) | In-cluster StatefulSet per service (`order-mysql`, `payment-mysql`, etc.), each with its own PVC and `mysql.cnf` ConfigMap | One StatefulSet per schema (not one shared MySQL instance with multiple schemas) — physically enforces ADR-0007's "no cross-context DB access" by making cross-context queries require crossing a Service boundary, not just a schema boundary. NFR-AVAIL-005 (99.95%, primary + replica + automatic failover) is **not fully met by a single-pod StatefulSet** — flagged as **OQ-DPL-01** |
| **Redis** (cluster mode) | In-cluster StatefulSet, `ecommerce-infra` namespace, RDB persistence enabled (per ADR-0010's "RDB snapshots every 60s" requirement) | Single Redis cluster shared across Cart (cart state), User/Auth (refresh tokens, rate limiting), and Payment (idempotency keys) — per `container-diagram.md` OQ-CD-02, accepted as a single cluster for Phase 1 cost/simplicity, with logical isolation by key-prefix ACLs |
| **Object Storage (S3) / CDN (CloudFront)** | External (AWS), not in-cluster | `container-diagram.md` §3 already designates these as external managed services even in Phase 1 — no Kubernetes equivalent makes sense for object storage |

### OQ-DPL-01 — MySQL HA gap

NFR-AVAIL-005 specifies "Primary + 1 replica, automatic failover" for MySQL, but a
single-pod-per-service StatefulSet (as scoped above) provides neither replication nor
failover — it is a **single point of failure per bounded context**, each only as
durable as its PVC. Closing this gap requires either:

- **Option A — MySQL InnoDB Cluster / Group Replication** (3-pod StatefulSet per
  service with a Router sidecar) — true HA, but **6 services × 3 pods = 18 MySQL
  pods**, a significant resource and operational cost increase
- **Option B — Single-pod StatefulSet + automated PVC snapshot/restore** (meets
  NFR-AVAIL-011's RPO < 5min via frequent snapshots, but RTO during a pod-loss event
  is "redeploy + restore from snapshot", likely exceeding NFR-AVAIL-010's RTO < 15min
  for a busy database)
- **Option C — Defer to a managed database** (Phase 1 uses a managed MySQL — e.g.,
  cloud-provider RDS-equivalent — for `order_db`/`payment_db` only, the two
  **99.95%** services, while less-critical schemas stay as single-pod StatefulSets)

**Recommendation: Option C**, scoped to Order and Payment only (the two services with
the 99.95% NFR-AVAIL-002 target) — but this is a cost/ops trade-off the project owner
should weigh against the "operate it yourself" learning goal (CLAUDE.md). Tracked as
**OQ-DPL-01**, candidate for an ADR if Option C is chosen (would also need a
`docs/adr/` entry documenting the Phase 1→Phase 2 cost comparison, since Phase 2 uses
DynamoDB which has no analogous single-point-of-failure).

---

## 5. Ingress Controller Setup

| Setting | Value |
|---|---|
| Controller | `ingress-nginx` (community Nginx Ingress Controller) — chosen per `container-diagram.md` OQ-CD-01's "Nginx Ingress" alternative being viable if the team has Nginx expertise; combined with Spring Cloud Gateway (`api-gateway-design.md`), Nginx Ingress handles **TLS termination and L7 load balancing only**, while Spring Cloud Gateway (running as a Deployment behind the Ingress) handles JWT validation, rate limiting, path routing (`api-gateway-design.md` §3), and CORS (§5) |
| TLS | `cert-manager` + Let's Encrypt (or self-signed CA for local dev) issuing a certificate for the Ingress resource — terminates TLS 1.2+ per NFR-SEC-002 |
| Routing | Single Ingress resource routes `/**` to the `api-gateway` Service (ClusterIP) in the `ecommerce` namespace — **all** path-prefix routing logic (`api-gateway-design.md` §3) lives in Spring Cloud Gateway, not in Ingress annotations, to keep routing rules in version-controlled application config rather than Kubernetes-specific YAML |
| `/webhooks/payment` | Same Ingress, same backend (`api-gateway`) — Spring Cloud Gateway forwards unauthenticated per `api-gateway-design.md` §3; no separate Ingress path needed |

**Why a two-layer gateway (Ingress + Spring Cloud Gateway) instead of Ingress-only
routing?** An Ingress controller can do path-based routing directly to each
microservice's Service, removing Spring Cloud Gateway entirely. This was implicitly
left open by `container-diagram.md` OQ-CD-01. This document resolves it: **keep Spring
Cloud Gateway** because JWT validation, the Redis-backed rate-limiting (§4 of
`api-gateway-design.md`), and CORS policy are **application-level concerns** best
expressed in Spring code (testable, debuggable with the same tools as the services
themselves) rather than Nginx Lua/annotation scripting. Ingress is reduced to its
infrastructural strength: TLS termination and L7 load balancing into the cluster.

---

## 6. ConfigMap / Secret Management

Per NFR-MAINT-006 (12-Factor config externalisation) and NFR-SEC-010 (no plaintext
credentials in code or env vars):

| Config type | Mechanism | Example |
|---|---|---|
| Non-sensitive config (feature flags, cache TTLs, Kafka topic names, log levels) | `ConfigMap`, mounted as `application.yml` overlay (Spring profile `kubernetes`) | `cart-service-config` ConfigMap → `cart-ttl-seconds: 604800` (cart-lld.md §7.2's 7-day TTL, ADR-0010 amendment) |
| Sensitive config (DB passwords, JWT signing keys, payment gateway API keys, Kafka SASL credentials) | `Secret` (Kubernetes-native for Phase 1), mounted as files (not env vars, per NFR-SEC-010's "no... environment variables in plain text" — env vars are visible via `kubectl describe pod` and process inspection; file mounts via `tmpfs` are not) | `payment-service-secrets` Secret → mounted at `/etc/secrets/payment-gateway-api-key` |
| Secret encryption at rest | Kubernetes `EncryptionConfiguration` with a KMS provider (or `etcd` encryption at rest at minimum for local/dev clusters) | Cluster-level config, not per-service |

**Phase 1 → Phase 2 secrets migration note:** Kubernetes `Secret` objects are the
Phase 1 mechanism; `container-diagram.md` §11 already designates AWS Secrets
Manager as the Phase 2 equivalent (referenced for Cognito/Lambda credentials). No
Phase 1 code should read secrets via direct Kubernetes API calls — always via
mounted file/env injected by the Deployment spec — so the Phase 2 migration only
changes *how* the file gets there (CSI Secrets Store driver vs. native Secret), not
the application code's read path.

---

## 7. Cluster Topology

| Aspect | Phase 1 design |
|---|---|
| **Node pools** | Single general-purpose node pool for `ecommerce` (app services — stateless, HPA-scaled) + a separate node pool with local SSD storage for `ecommerce-infra` (Kafka/MySQL/Redis StatefulSets) — separates noisy-neighbour risk between bursty app pods and I/O-sensitive stateful workloads |
| **Availability zones** | Minimum 3 AZs for the `ecommerce-infra` node pool — Kafka's RF=3 (ADR-0002) and MySQL's planned replica (OQ-DPL-01, if Option A/C chosen) need AZ-level spread to be meaningful. `ecommerce` app node pool: 2+ AZs, sufficient given stateless pods can be rescheduled freely |
| **Pod anti-affinity** | `podAntiAffinity` (preferred, not required, to avoid scheduling deadlock on small clusters) spreads each service's replicas across nodes/AZs — combined with the PDBs (§3), ensures a single node or AZ failure doesn't take a service below its `minAvailable` |
| **Local dev** | `kubectl apply -k phase1/k8s/overlays/local` (per `CLAUDE.md`) targets a single-node cluster (kind/minikube/k3d) — `local` overlay reduces all `minReplicas` to 1, drops PDBs and anti-affinity (irrelevant on one node), and uses `emptyDir` instead of PVCs for stateful components where persistence isn't needed for local testing |

### Kustomize layout

```
phase1/k8s/
├── base/
│   ├── api-gateway/
│   │   ├── deployment.yaml
│   │   ├── service.yaml
│   │   ├── hpa.yaml
│   │   ├── pdb.yaml
│   │   └── configmap.yaml
│   ├── user-auth-service/        (same structure)
│   ├── catalog-service/
│   ├── cart-service/
│   ├── order-service/
│   ├── payment-service/
│   ├── inventory-service/
│   ├── notification-service/
│   ├── infra/
│   │   ├── kafka-statefulset.yaml
│   │   ├── redis-statefulset.yaml
│   │   └── mysql-statefulset.yaml   (templated per service via Kustomize components)
│   ├── ingress.yaml
│   ├── namespaces.yaml
│   └── kustomization.yaml
└── overlays/
    ├── local/
    │   ├── kustomization.yaml      # patches: minReplicas=1, no PDB, emptyDir volumes
    │   └── resource-patches.yaml
    └── prod/
        ├── kustomization.yaml      # patches: full replica counts (§3), PVCs, AZ anti-affinity
        └── resource-patches.yaml
```

This directly satisfies the `kubectl apply -k phase1/k8s/overlays/local` command
already documented in `CLAUDE.md`'s Build & Run section — that command currently has
no corresponding directory; this is the structure Phase 1 implementation scaffolding
(next artefact, §9) should create.

---

## 8. Phase 2 Delta

| Phase 1 | Phase 2 equivalent | Key difference |
|---|---|---|
| Kubernetes Deployments + HPA (§3) | Lambda + concurrency auto-scaling | No replica/resource sizing — Lambda scales per-invocation; `container-diagram.md` §11 already notes this |
| In-cluster Kafka StatefulSet (§4) | EventBridge + SQS | No cluster to size or operate |
| In-cluster MySQL StatefulSets (§4, OQ-DPL-01) | DynamoDB (managed, multi-AZ by default) | OQ-DPL-01's HA gap doesn't exist in Phase 2 — DynamoDB's availability model replaces it entirely, a key Phase 1 vs Phase 2 learning comparison per CLAUDE.md's "Phase comparison" guidance |
| In-cluster Redis StatefulSet (§4) | ElastiCache Serverless / DynamoDB TTL items | Per `container-diagram.md` §11 |
| Ingress + Spring Cloud Gateway (§5) | AWS API Gateway (managed TLS + routing) | Two-layer gateway (§5) collapses to one managed layer |
| Kubernetes Secrets (§6) | AWS Secrets Manager + IAM | Already noted in §6 |
| Kustomize overlays (§7) | AWS CDK (Java) stacks | `phase1/k8s/` → `phase2/infrastructure/cdk/` per `WORKFLOW.md`'s repo structure |

---

## 9. Open Questions

| ID | Item | Owner | Status |
|---|---|---|---|
| OQ-DPL-01 | MySQL HA: NFR-AVAIL-005 (99.95%, primary+replica+failover) is not met by single-pod-per-service StatefulSets (§4). Recommendation: managed DB for `order_db`/`payment_db` only (Option C); needs an ADR if adopted | Architect | Open |
| OQ-DPL-02 | Resource sizing in §3 (CPU/mem requests/limits) is a first-pass estimate with no load-test data behind it — revisit once Phase 4 (QA, k6/Gatling load tests) produces real numbers | Architect / QA | Open — low priority, revisit in Phase 4 |
| OQ-DPL-03 | `ecommerce-infra`'s MySQL StatefulSets need per-service storage sizing (PVC capacity) — not yet estimated; depends on each service's `er-diagrams.md` table volume projections, which don't currently include growth estimates | Architect | Open |

---

## 10. Next Artefacts

| Artefact | Description |
|---|---|
| **Phase 1 implementation scaffolding** | Per `WORKFLOW.md` and all seven LLDs' "Next Artefacts" sections — multi-module Maven project (`phase1/<context>-service/`), per-service Dockerfiles, and the `phase1/k8s/` Kustomize layout (§7) this document defines. This is the last HLD-level artefact before implementation begins — Phase 2 (System Architecture) is now complete |
| **Flyway migration scaffolding** | Each service's `er-diagrams.md` schema (already finalised per-LLD) becomes `V1__init.sql` under `phase1/<context>-service/src/main/resources/db/migration/` |
