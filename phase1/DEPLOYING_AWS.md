# Deploying user-service to AWS (EKS)

> **This guide's manual steps are now automated.** [`phase1/CICD.md`](CICD.md) +
> [`phase1/infra/cdk/`](infra/cdk/README.md) replace `eksctl` with an AWS CDK app and
> wire the whole deploy → smoke-test sequence into a GitHub Actions pipeline
> (`git push` to `main`, or a manual workflow run) — see
> [`ADR-0016`](../docs/adr/ADR-0016-cdk-and-cicd-for-eks-deployment.md) for why. **Start
> there** for the recommended path. The rest of this document is kept as the manual
> walkthrough — useful for understanding exactly what the pipeline is doing under the
> hood, debugging a failed automated run step by step, or a one-off deploy without
> touching CI at all.

Step-by-step guide to run user-service on a real AWS cluster for a **deploy → smoke
test → load test → tear down** session — not a permanent environment. Read the cost
and teardown sections before you start; leaving this running is the expensive
mistake, not running it.

Builds on `phase1/RUNNING.md` (local dev) and reuses the same Kustomize base
(`phase1/user-service/k8s/base/`) that already encodes
`docs/hld/deployment-architecture.md` §3's sizing (2 replicas, HPA min 2/max 4, PDB,
pod anti-affinity). This guide only adds what's genuinely different in AWS: real
infra instead of `docker-compose.infra.yml`, an EKS cluster instead of kind/minikube,
and a container registry instead of a local image.

**Why EKS, not ECS/Fargate:** `docs/adr/ADR-0007-kafka-vs-rabbitmq.md` and
`ADR-0010-cart-storage.md` deliberately keep Phase 1 on cloud-agnostic Kubernetes —
self-hosted Kafka/MySQL/Redis, no managed AWS service lock-in yet (that's Phase 2's
serverless rewrite). EKS is the only AWS target consistent with that decision.

---

## Cost overview

| Resource | Rate | Notes |
|---|---|---|
| EKS control plane | $0.10/hr (~$73/mo if left running) | Bills from cluster creation until `eksctl delete cluster` completes — **the #1 way to overspend on this exercise is forgetting to delete it** |
| 2x `t3.medium` (on-demand) | ~$0.0416/hr each ≈ $0.083/hr | Sized for user-service (2 replicas) + MySQL + Redis + Kafka, see resource math below |
| NLB (`user-service-external`) | ~$0.0225/hr + per-GB processed | Created automatically by the `LoadBalancer` Service |
| EBS volumes (3x, gp3) | ~$0.08/GB-month, prorated | 5Gi (MySQL) + 1Gi (Redis) + 5Gi (Kafka) — a few cents for a few hours |
| NAT Gateway | **Disabled** in `eks-cluster.yaml` | Public-only subnets to avoid the ~$0.045/hr + data-processing charge — acceptable for a short test with no real user data, not for a real deployment |

**Rough total: ~$0.20–0.25/hr while the cluster exists.** A 3–4 hour deploy + smoke
test + load test + teardown session costs roughly $1. The risk isn't the hourly
rate — it's an EKS cluster or orphaned NLB left running for days because teardown
was skipped.

Resource math for the 2-node sizing: user-service 2×(250m/512Mi) + MySQL
250m/512Mi + Redis 100m/128Mi + Kafka 250m/1Gi ≈ 1.1 vCPU / 2.65Gi requested,
comfortably inside 2×`t3.medium` (4 vCPU / 8Gi total, minus per-node system
reservation).

---

## Prerequisites

| Tool | Purpose | Install check |
|---|---|---|
| AWS CLI v2, configured (`aws configure`) | Auth to AWS | `aws sts get-caller-identity` |
| `eksctl` | Cluster lifecycle | `eksctl version` |
| `kubectl` | Cluster interaction | `kubectl version --client` |
| `kustomize` (or `kubectl apply -k`, same thing) | Render manifests | `kubectl kustomize --help` |
| Docker | Build the image | `docker version` |

IAM permissions: the AWS principal needs to create/manage EKS clusters, EC2
instances, VPCs, IAM roles (for `iam.withOIDC` + IRSA), and ECR repositories.
`AdministratorAccess` is the path of least friction for a personal test account;
scope it down if this is a shared/organisational account.

---

## Step 1 — Build and push the image to ECR

```bash
export AWS_REGION=ap-south-1                      # match eks-cluster.yaml's region
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
export ECR_REPO=user-service

# Create the repo (one-time)
aws ecr create-repository --repository-name $ECR_REPO --region $AWS_REGION

# Auth Docker to ECR
aws ecr get-login-password --region $AWS_REGION | \
  docker login --username AWS --password-stdin $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

# Build (same Dockerfile/context as local — phase1/RUNNING.md Option 2)
docker build -f phase1/user-service/Dockerfile \
  -t $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPO:latest \
  phase1/

# Push
docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/$ECR_REPO:latest
```

---

## Step 2 — Create the EKS cluster

```bash
eksctl create cluster -f phase1/k8s/eks-cluster.yaml
```

Takes 15–20 minutes (EKS control plane provisioning is the slow part). This also
configures your local `kubectl` context — verify:

```bash
kubectl get nodes
# should show 2 Ready t3.medium nodes
```

If `region` in `eks-cluster.yaml` doesn't match your ECR push region, fix one or the
other before continuing.

---

## Step 3 — Deploy shared infra (MySQL, Redis, Kafka)

```bash
kubectl apply -k phase1/k8s/infra

kubectl -n ecommerce-infra rollout status deployment/user-mysql
kubectl -n ecommerce-infra rollout status deployment/redis
kubectl -n ecommerce-infra rollout status deployment/kafka
```

If a pod stays `Pending`, check the PVC first — this is almost always the EBS CSI
driver addon not being ready yet:

```bash
kubectl get pvc -n ecommerce-infra
kubectl describe pvc <name> -n ecommerce-infra   # look for provisioning errors
```

---

## Step 4 — Generate and load secrets

Same requirement as `phase1/RUNNING.md`'s Kubernetes option: every replica must share
one RSA key pair, or cross-pod JWT verification breaks.

```bash
openssl genrsa -out jwt-private.pem 2048
openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem

kubectl -n ecommerce create secret generic user-service-secrets \
  --from-literal=DB_USERNAME=user_service \
  --from-literal=DB_PASSWORD=changeme \
  --from-file=JWT_PRIVATE_KEY=jwt-private.pem \
  --from-file=JWT_PUBLIC_KEY=jwt-public.pem \
  --dry-run=client -o yaml | kubectl apply -f -

rm jwt-private.pem jwt-public.pem   # keep key material out of your shell history/disk
```

`--dry-run=client -o yaml | kubectl apply -f -` (instead of plain `create secret`) so
this is safely re-runnable if you need to rotate the keys mid-session.

---

## Step 5 — Deploy user-service

Edit `phase1/user-service/k8s/overlays/aws/kustomization.yaml` — replace
`<ACCOUNT_ID>` and `<REGION>` in the `images:` block with the values from Step 1 (or
just re-run with `envsubst`):

```bash
sed -i "s/<ACCOUNT_ID>/$AWS_ACCOUNT_ID/; s/<REGION>/$AWS_REGION/" \
  phase1/user-service/k8s/overlays/aws/kustomization.yaml

kubectl apply -k phase1/user-service/k8s/overlays/aws

kubectl -n ecommerce rollout status deployment/user-service
```

---

## Step 6 — Verify

```bash
kubectl -n ecommerce get pods,svc,hpa,pdb

# Wait for the NLB to get an external hostname (takes 1-3 min)
kubectl -n ecommerce get svc user-service-external -w
```

Once `EXTERNAL-IP` shows a hostname:

```bash
export LB_HOST=$(kubectl -n ecommerce get svc user-service-external \
  -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')

curl http://$LB_HOST/actuator/health

curl -X POST http://$LB_HOST/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"smoke-test@example.com","password":"Passw0rd!23"}'
```

If this works, you're ready for `phase1/LOAD_TESTING.md`, which targets `$LB_HOST`.

---

## Teardown (do this right after load testing)

Order matters — delete the LoadBalancer Service **before** the cluster, or the NLB
and its security group are orphaned in your AWS account and keep billing with
nothing left to manage them from `kubectl`.

```bash
# 1. Release the NLB
kubectl delete -k phase1/user-service/k8s/overlays/aws

# 2. Confirm the NLB is actually gone (AWS console or CLI) before proceeding
aws elbv2 describe-load-balancers --region $AWS_REGION \
  --query "LoadBalancers[?contains(LoadBalancerName, 'k8s-ecommerce')]"
# should return an empty list within ~2 minutes of step 1

# 3. Remove infra + app namespaces
kubectl delete -k phase1/k8s/infra

# 4. Delete the cluster (control plane + node group + VPC)
eksctl delete cluster -f phase1/k8s/eks-cluster.yaml

# 5. Revert the account-specific image edit from Step 5 so it doesn't get committed
git checkout -- phase1/user-service/k8s/overlays/aws/kustomization.yaml
```

**Post-teardown checklist** (the actual cost trap is anything below surviving the
cluster deletion):

- [ ] `aws ec2 describe-volumes --region $AWS_REGION --filters Name=status,Values=available` — no leftover EBS volumes (PVCs should auto-delete with the cluster, but verify)
- [ ] `aws elbv2 describe-load-balancers --region $AWS_REGION` — no leftover load balancers
- [ ] `aws ec2 describe-addresses --region $AWS_REGION` — no unattached Elastic IPs
- [ ] `aws ecr list-images --repository-name user-service --region $AWS_REGION` — delete the repo too if you don't need the image (`aws ecr delete-repository --repository-name user-service --region $AWS_REGION --force`)

---

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| PVC stuck `Pending` | `aws-ebs-csi-driver` addon not ready yet, or `iam.withOIDC` was missing from the cluster config before creation (IRSA role wouldn't exist) |
| Pod `ImagePullBackOff` | Node's IAM role lacks ECR pull permission (should be automatic via the managed node group's default policy), or the image URI in `overlays/aws/kustomization.yaml` doesn't match what you pushed |
| `user-service` pods `CrashLoopBackOff` on startup | Check `kubectl -n ecommerce logs deploy/user-service` — usually DB not reachable yet (infra rollout from Step 3 not actually finished) or the JWT secret has blank keys (Step 4 skipped/failed) |
| Login works on one pod's requests but fails intermittently | Confirms the "shared RSA key pair" requirement from Step 4 wasn't met — each replica generated its own ephemeral key |
