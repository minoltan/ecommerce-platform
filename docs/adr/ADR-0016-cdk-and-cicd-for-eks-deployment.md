# ADR-0016: AWS CDK (TypeScript) + Fully Automated CI/CD for Phase 1 EKS Deployment

**Status:** Accepted
**Date:** 2026-07-08
**Phase:** IMPL / OPS
**Bounded contexts affected:** Cross-cutting infrastructure (currently exercised via User/Auth, the first deployed service)

---

## Context

`phase1/DEPLOYING_AWS.md` documents a manual, imperative path to run `user-service` on
real AWS: `eksctl create cluster -f phase1/k8s/eks-cluster.yaml`, hand-run `docker
build`/`push` to ECR, and `kubectl apply -k` for the app and infra manifests. It is
explicitly framed as a short-lived **deploy → smoke test → load test → teardown**
session, not a standing environment — the guide's own cost table warns that "the #1 way
to overspend on this exercise is forgetting to delete [the cluster]."

`docs/hld/deployment-architecture.md` §8 (Phase 2 Delta) already names "AWS CDK (Java)"
as this project's intended IaC tool, but scopes it to Phase 2's serverless stacks only —
Phase 1 was left on `eksctl` + Kustomize. Two things changed that:

1. Repeating the manual `eksctl`/`kubectl`/`docker` sequence by hand every time is
   error-prone (the guide itself has a troubleshooting table full of steps skipped or
   done out of order) and leaves no reviewable diff of what infrastructure exists.
2. The project owner wants this formalised as a real CI/CD pipeline — full automated
   deploy, not just a better runbook — as practice for the DevOps/AWS-Architect-adjacent
   skills this portfolio project is building (`CLAUDE.md`'s "Working With This User").

This ADR covers Phase 1 only. It does not revisit Phase 2's CDK usage (Lambda/DynamoDB
stacks), which remains a separate, later concern.

## Decision

### 1. Replace `eksctl` with AWS CDK, written in TypeScript

A new CDK v2 app at `phase1/infra/cdk/` provisions the AWS-level resources currently
created by `eksctl create cluster -f phase1/k8s/eks-cluster.yaml` plus the `aws ecr
create-repository` step from `DEPLOYING_AWS.md` Step 1: a public-subnet-only VPC, the
EKS cluster and its managed node group (2× `t3.medium`, matching the existing sizing
math), the IRSA-backed `aws-ebs-csi-driver` addon, and the ECR repository. TypeScript
per the project owner's explicit choice — it is also the CDK ecosystem's primary
language, so L2 constructs and documentation are first-class rather than ported.

This is declarative, diffable (`cdk diff`), and destroyable as a unit (`cdk destroy`),
replacing an imperative CLI sequence with no drift detection.

### 2. CDK owns cloud infrastructure only — Kustomize keeps owning in-cluster app state

The CDK app stops at the cluster boundary: VPC, EKS control plane, node group, IRSA
addons, ECR. It does **not** deploy the Kustomize-rendered manifests (`user-service`
Deployment/Service/HPA/PDB, or the `ecommerce-infra` Kafka/MySQL/Redis StatefulSets) via
`eks.KubernetesManifest`/`addManifest`. Those stay exactly as `DEPLOYING_AWS.md` already
has them — `kubectl apply -k`.

This mirrors the separation `deployment-architecture.md` already draws between Phase 1
(Kustomize owns cluster-internal state) and Phase 2 (CDK owns everything, because there
is no cluster) — Phase 1 now has both tools, each scoped to what it's good at.

### 3. GitHub Actions orchestrates full automated deploy; GitHub OIDC for AWS auth

A `deploy.yml` workflow chains `cdk deploy` (infra) → `docker build/push` (image) →
`kubectl apply -k` (infra manifests, secrets, app manifests) → smoke test into one
pipeline run, so the deploy is end-to-end automated even though no single tool owns the
whole sequence — the workflow is the orchestration layer. AWS authentication uses GitHub
OIDC federation (`aws-actions/configure-aws-credentials` with `role-to-assume`), not
long-lived IAM access keys stored as repository secrets.

### 4. Deploy triggers on push to `main`; teardown is manual-only

`deploy.yml` runs on push to `main` and on manual `workflow_dispatch`. This repo's
GitFlow (`GITHUB_BEST_PRACTICES.md`) means `main` only advances via deliberate release
PRs, not every commit — so "automatic on push to `main`" does not mean "an EKS cluster
spins up on every feature-branch push."

`teardown.yml`, by contrast, is `workflow_dispatch`-only and requires a typed
confirmation input. Auto-creating billed infrastructure on a rare, deliberate trigger is
one risk profile; auto-destroying it on some misconfigured or accidental trigger is a
different and worse one — the existing guide's own repeated cost/teardown warnings are
the reason this step stays human-gated even though everything else is automated.

## Consequences

### Positive

- Infrastructure changes are reviewable (`cdk diff` in PRs touching `phase1/infra/cdk/`)
  instead of being whatever the last person typed into `eksctl`.
- One `git push` to `main` reproduces the entire environment; no more hand-run,
  order-sensitive command sequences.
- No long-lived AWS credentials stored in GitHub — OIDC tokens are short-lived and
  scoped to the specific repo/branch trust policy in `github-oidc-stack.ts`.
- CDK and Kustomize each keep the responsibility they already had; no new tool needs to
  learn Kustomize's job or vice versa.

### Negative

- Two IaC-adjacent tools now coexist in Phase 1 (CDK for cloud resources, Kustomize for
  cluster-internal resources) — a newcomer has to know the boundary. Mitigated by this
  ADR and by `phase1/CICD.md` stating the split explicitly.
- The GitHub OIDC deploy role is broad (CDK deploy permissions + EKS cluster-admin
  access entry + ECR push), matching `DEPLOYING_AWS.md`'s existing "AdministratorAccess
  is the path of least friction for a personal test account" framing — acceptable for a
  personal learning account, not a template for a shared/organisational one.
- `deploy.yml` triggering on every push to `main` means every release-branch merge
  provisions a real, billed EKS cluster (~$0.20–0.25/hr per `DEPLOYING_AWS.md`'s cost
  table) unless torn down afterward — automation makes it easier to *create* the cost
  trap, not harder to forget about it. `teardown.yml` staying manual is a partial
  mitigation, not a fix; the project owner is still responsible for running it.
- `cdk synth`/`deploy` need real AWS API calls for AMI/VPC context lookups the EKS L2
  construct performs — `cdk synth` cannot be fully offline/credential-free in CI the way
  a pure-unit-test suite could.

## Alternatives Rejected

### Keep `eksctl`, automate it via a shell-script-driven pipeline

Would achieve "automated" but not "declarative/diffable" — `eksctl`'s YAML config is a
cluster-creation spec, not a full infrastructure model, and offers no plan/diff step
before applying. Rejected because the whole point of formalising this was to get
reviewable infrastructure changes, not just a scripted version of the same manual steps.

### CDK in Java

Would match the rest of the platform's language and the `deployment-architecture.md` §8
note's original "AWS CDK (Java)" framing. Rejected per the project owner's explicit
choice of TypeScript, which also has the practical benefit of being CDK's best-supported
language for less-common constructs (e.g. EKS access entries).

### CDK also deploying the Kustomize-rendered K8s manifests (`addManifest`)

Would give a single deploy command instead of `cdk deploy` + `kubectl apply -k`.
Rejected: it would mean rendering Kustomize output at CDK synth time and feeding it into
`KubernetesManifest`, coupling a slow-changing infra stack's lifecycle (and its
CloudFormation rollback semantics) to fast-changing application config and to secret
material (JWT keys) that must never be committed or synthesised into a CDK template.
Keeping them separate means either can be redeployed/rolled back independently.

### Fully automatic teardown (e.g., scheduled cluster deletion, or teardown-on-PR-close)

Would close the "forgot to tear it down" cost gap more aggressively. Rejected: an
automatic destroy trigger firing at the wrong time (e.g. a scheduled job running while a
load test is mid-flight) is a worse failure mode than a human occasionally forgetting to
click a button — especially for a single-environment personal account with no staging
buffer. Teardown stays a deliberate, confirmed action.

### Long-lived AWS IAM access keys as GitHub repository secrets

Simpler to set up than OIDC (no `github-oidc-stack.ts` bootstrap step required).
Rejected as a static-credential-in-CI anti-pattern — keys don't expire, are one leaked
Actions log away from account compromise, and require manual rotation. OIDC's short-lived
tokens and repo/branch-scoped trust policy directly address the current CI/CD-security
best practice this ADR is meant to demonstrate understanding of.
