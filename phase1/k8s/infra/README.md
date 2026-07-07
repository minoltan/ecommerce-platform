# Shared infra — `ecommerce-infra` namespace

Single-pod Deployments (not StatefulSets) for MySQL, Redis, and Kafka — a
deliberately minimal stand-in for the full HA design in
`docs/hld/deployment-architecture.md` §4 (3-broker Kafka RF=3, per-service MySQL
StatefulSets with replica + failover, Redis cluster mode).

**Why simplified:** these manifests exist to support a single-service (user-service)
deployment + load-test session on a real cluster (EKS), not the full 7-service
production rollout. No replication, no PVC snapshotting, no multi-AZ spread. Treat
data here as disposable — this is not the OQ-DPL-01 resolution, just enough to run
one service end-to-end outside `docker-compose.infra.yml`.

When more bounded contexts land, this directory is where the real
`base/infra/{kafka,redis,mysql}-statefulset.yaml` design from
`deployment-architecture.md` §7's Kustomize layout should replace these.

## Contents

| File | Provides |
|---|---|
| `namespace.yaml` | `ecommerce` and `ecommerce-infra` namespaces |
| `mysql.yaml` | `user-mysql` Deployment + Service + PVC (single instance, `user_db` schema) |
| `redis.yaml` | `redis` Deployment + Service + PVC |
| `kafka.yaml` | `kafka` Deployment + Service (KRaft, single broker — same image/config as `docker-compose.infra.yml`) |
| `kustomization.yaml` | Ties the above together, generates the MySQL init ConfigMap from `infra/mysql/init/` |

## Apply

```bash
# From repo root
kubectl apply -k phase1/k8s/infra
kubectl -n ecommerce-infra rollout status deployment/user-mysql
kubectl -n ecommerce-infra rollout status deployment/redis
kubectl -n ecommerce-infra rollout status deployment/kafka
```
