# user-service — Kubernetes manifests

Kustomize layout per `docs/hld/deployment-architecture.md` §3/§6/§7 (see
`docs/lld/user-auth-lld.md` OQ-LLD-UA-07 for the per-service-vs-centralised layout
discrepancy this scaffold resolves pragmatically).

```
k8s/
├── base/             # Deployment, Service, ConfigMap, Secret template, HPA, PDB
└── overlays/
    └── local/        # 1 replica, no HPA/PDB, points at docker-compose.infra.yml on the host
```

## Prerequisites

- The `ecommerce` namespace must already exist in the cluster (created once at the
  cluster level — `deployment-architecture.md` §2).
- `k8s/base/secret.yaml` is a **placeholder**. Before applying, replace `DB_USERNAME` /
  `DB_PASSWORD` with the real `user_db` credentials (`infra/mysql/init/01-user-db.sql`)
  and `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY` with a base64-encoded RSA key pair, e.g.:

  ```bash
  openssl genrsa 2048 | base64 -w0           # JWT_PRIVATE_KEY (PKCS#1 — convert to PKCS#8 if needed)
  openssl rsa -in private.pem -pubout | base64 -w0   # JWT_PUBLIC_KEY
  ```

  **This is required for any deployment with more than one replica**: `application.yml`
  generates an ephemeral per-process RSA key pair when these are blank, so each pod
  would sign/verify JWTs with a different key and token validation would fail
  depending on which pod handles the request.

## Usage

```bash
# Local (single-node cluster, e.g. kind/minikube/k3d)
kubectl apply -k phase1/user-service/k8s/overlays/local

# Build the image referenced by base/deployment.yaml (image: user-service:latest)
docker build -f phase1/user-service/Dockerfile -t user-service:latest phase1/
```
