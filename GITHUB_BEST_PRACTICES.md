# GitHub Best Practices — Ecommerce Platform

> This document defines the Git and GitHub workflow standards for the
> ecommerce platform project. Every contributor must follow these practices.
> Last updated: June 2026

---

## Table of Contents

1. [Branch Strategy](#1-branch-strategy)
2. [Branch Protection Rules](#2-branch-protection-rules)
3. [Commit Standards](#3-commit-standards)
4. [Pull Request Standards](#4-pull-request-standards)
5. [Release Process](#5-release-process)
6. [Versioning](#6-versioning)
7. [Daily Workflow](#7-daily-workflow)
8. [Common Scenarios](#8-common-scenarios)
9. [What Never To Do](#9-what-never-to-do)
10. [Quick Reference](#10-quick-reference)

---

## 1. Branch Strategy

### Branch Model — GitFlow

```
main
│   Production-ready code only
│   Every commit = a tagged release
│   Never commit directly — PR from release/* or hotfix/* only
│
develop
│   Integration branch — always deployable
│   All feature branches merge here first
│   Never commit directly — PR from feature/* only
│
├── feature/*
│       All new work — requirements, design, code, tests
│       Branched from develop
│       Merged back to develop via PR
│
├── release/*
│       Created when a phase/milestone is complete
│       Branched from develop
│       Merged to main via PR
│       Then merged back to develop
│
└── hotfix/*
        Emergency fixes to production
        Branched from main
        Merged to both main AND develop
```

### Branch Naming Convention

```
feature/[ROLE]-[task-description]
release/[phase-name]
hotfix/[issue-description]
```

#### Examples by role

```bash
# Requirements Engineer
feature/RE-001-event-storming
feature/RE-002-user-stories-auth
feature/RE-003-openapi-stubs

# System Architect
feature/SA-001-hld-c4-diagrams
feature/SA-002-lld-order-service
feature/SA-003-adr-kafka-decision

# Backend Developer
feature/DEV-001-auth-service
feature/DEV-002-catalog-service
feature/DEV-003-order-service

# QA Engineer
feature/QA-001-test-strategy
feature/QA-002-integration-tests-auth
feature/QA-003-load-test-scripts

# DevOps Engineer
feature/OPS-001-dockerfiles
feature/OPS-002-k8s-manifests
feature/OPS-003-github-actions-ci

# AWS Architect
feature/AWS-001-cdk-setup
feature/AWS-002-lambda-order-service
feature/AWS-003-dynamodb-design

# Project Manager
feature/PM-001-sprint-1-plan
feature/PM-002-risk-register

# Releases
release/phase-1-requirements
release/phase-2-system-architecture
release/phase-3-implementation
release/phase-4-testing
release/phase-5-cicd
release/phase-6-aws-serverless

# Hotfixes
hotfix/fix-order-api-schema
hotfix/fix-auth-token-expiry
```

---

## 2. Branch Protection Rules

### main branch rules

| Rule | Setting |
|------|---------|
| Require pull request before merging | ✅ Enabled |
| Required approvals | 0 (solo) / 1 (team) |
| Require status checks to pass | ✅ Enabled |
| Require conversation resolution | ✅ Enabled |
| Require linear history | ✅ Enabled |
| Do not allow bypassing | ✅ Enabled |
| Allow force pushes | ❌ Never |
| Allow deletions | ❌ Never |

### develop branch rules

| Rule | Setting |
|------|---------|
| Require pull request before merging | ✅ Enabled |
| Required approvals | 0 (solo) / 1 (team) |
| Require status checks to pass | ✅ Enabled |
| Require conversation resolution | ✅ Enabled |
| Require linear history | ❌ Not required |
| Allow force pushes | ❌ Never |
| Allow deletions | ❌ Never |

### Default branch

Set `develop` as the default branch so all new PRs automatically
target `develop` instead of `main`.

```
GitHub → Settings → General → Default branch → Switch to develop
```

---

## 3. Commit Standards

### Commit Message Format

```
[ROLE] scope: short description

Optional longer description explaining WHY, not WHAT.
What is already visible in the diff.

Refs: #issue-number (if applicable)
```

### Role Prefixes

| Prefix | Role | When to use |
|--------|------|-------------|
| `[INIT]` | Initialization | Project setup, folder structure |
| `[SKILL]` | Skill files | Adding or updating role/technique skills |
| `[RE]` | Requirements Engineer | RE artifacts, user stories, OpenAPI stubs |
| `[SA]` | System Architect | HLD, LLD, ADRs, diagrams |
| `[PM]` | Project Manager | Sprint plans, risk register, status docs |
| `[DEV]` | Backend Developer | Service code, tests, configs |
| `[QA]` | QA Engineer | Test code, test strategy, load scripts |
| `[OPS]` | DevOps Engineer | Dockerfiles, Helm, K8s, CI/CD pipelines |
| `[AWS]` | AWS Architect | CDK stacks, Lambda, DynamoDB design |
| `[FIX]` | Bug fix | Any bug fix regardless of role |
| `[RELEASE]` | Release | Release commits and tags |

### Commit Message Examples

```bash
# Good — clear role, scope, and description
[RE] auth: add user registration user stories with acceptance criteria
[SA] hld: add C4 level 1 system context diagram
[DEV] order-service: implement order placement with saga pattern
[QA] auth: add Testcontainers integration tests for login flow
[OPS] ci: add GitHub Actions pipeline with SonarQube quality gate
[AWS] dynamodb: add single-table design for order service
[FIX] payment: fix idempotency key not persisted on retry

# Bad — too vague
update files
fix bug
add stuff
WIP
```

### Commit Rules

- One logical change per commit — do not batch unrelated changes
- Present tense in description ("add" not "added")
- Description under 72 characters
- Never commit directly to `main` or `develop`
- Never commit secrets, credentials, or API keys
- Run lint and tests before committing

---

## 4. Pull Request Standards

### PR Lifecycle

```
1. Create feature branch from develop
2. Do the work, commit with proper messages
3. Push branch to GitHub
4. Open PR targeting develop (or main for releases)
5. Fill in PR template completely
6. Review (self-review for solo, peer review for team)
7. Resolve all conversations
8. Merge using correct merge strategy
9. Delete feature branch after merge
```

### PR Title Format

```
[ROLE] scope: description of what this PR delivers
```

Examples:
```
[RE] auth: complete user authentication requirements and OpenAPI stub
[SA] hld: add C4 container diagram for all 7 microservices
[DEV] order-service: implement order placement endpoint with saga
[RELEASE] Phase 2 — System Architecture complete
```

### PR Description Template

Save this as `.github/pull_request_template.md` in your repo:

```markdown
## Summary
<!-- One paragraph describing what this PR delivers and why -->

## Type
- [ ] Feature / new artifact
- [ ] Bug fix
- [ ] Documentation
- [ ] Refactoring
- [ ] Release

## Artifacts Changed
<!-- List files added or modified -->
- `path/to/file` — what changed and why

## Bounded Context
<!-- Which bounded context does this affect? -->
- [ ] User & Auth
- [ ] Product Catalog
- [ ] Cart & Session
- [ ] Order Management
- [ ] Payment Processing
- [ ] Inventory Management
- [ ] Notification Service
- [ ] Cross-cutting / Infrastructure

## Testing
<!-- How was this verified? -->
- [ ] Unit tests pass
- [ ] Integration tests pass
- [ ] Manual verification done
- [ ] Not applicable (documentation only)

## Definition of Done
- [ ] Code/artifact follows project standards
- [ ] Commit messages follow [ROLE] convention
- [ ] No secrets or credentials committed
- [ ] PR description complete
- [ ] All conversations resolved

## Related Issues / ADRs
<!-- Link any related issues, ADRs, or user stories -->
Refs: #
```

### Merge Strategy by Branch

| From | To | Strategy | Reason |
|------|----|----------|--------|
| `feature/*` | `develop` | Squash and merge | Clean develop history |
| `release/*` | `main` | Merge commit | Preserve full history |
| `hotfix/*` | `main` | Merge commit | Clear audit trail |
| `hotfix/*` | `develop` | Merge commit | Sync fix back |
| `main` | `develop` | Merge commit | Sync after release |

### After Merge — Always Delete the Feature Branch

```bash
# GitHub does this automatically if you enable it:
# Settings → General → ✅ Automatically delete head branches

# Or manually:
git branch -d feature/RE-001-event-storming
git push origin --delete feature/RE-001-event-storming
```

---

## 5. Release Process

### When to Release to main

Only when a full phase or milestone is complete and stable:

```
Phase 0 — Project setup          → v0.0.1
Phase 1 — Requirements complete  → v0.1.0
Phase 2 — Architecture complete  → v0.2.0
Phase 3 — Implementation done    → v0.3.0
Phase 4 — Testing done           → v0.4.0
Phase 5 — CI/CD done             → v0.5.0
Phase 6 — AWS Serverless done    → v0.6.0
Phase 7 — Production hardening   → v0.9.0
Phase 8 — Portfolio complete     → v1.0.0
```

### Release Steps

```bash
# Step 1 — Make sure develop is stable
git checkout develop
git pull origin develop

# Step 2 — Create release branch
git checkout -b release/phase-1-requirements
git push origin release/phase-1-requirements

# Step 3 — Open PR on GitHub
# release/phase-1-requirements → main
# Use "Merge commit" strategy
# Title: [RELEASE] Phase 1 — Requirement Engineering complete

# Step 4 — After PR is merged, tag the release
git checkout main
git pull origin main
git tag -a v0.1.0 -m "Phase 1 complete: Requirement Engineering

Artifacts delivered:
- Event storming (all 7 bounded contexts)
- Functional requirements (FR-* numbered)
- Non-functional requirements (NFR-* numbered)
- User stories with acceptance criteria
- OpenAPI 3.0 stubs (all 7 services)"

git push origin v0.1.0

# Step 5 — Sync develop with main
git checkout develop
git merge main
git push origin develop

# Step 6 — Delete release branch
git branch -d release/phase-1-requirements
git push origin --delete release/phase-1-requirements
```

---

## 6. Versioning

### Semantic Versioning — MAJOR.MINOR.PATCH

```
MAJOR — breaking change or complete system rebuild
MINOR — new phase or milestone complete
PATCH — bug fix or small improvement within a phase
```

### Version History Plan

```
v0.0.1  Project initialized
v0.1.0  Phase 1: Requirements Engineering complete
v0.2.0  Phase 2: System Architecture complete
v0.3.0  Phase 3: Java microservices implementation complete
v0.3.1  Patch: fix discovered during QA
v0.4.0  Phase 4: Testing strategy complete
v0.5.0  Phase 5: CI/CD pipeline complete
v0.6.0  Phase 6: AWS Serverless implementation complete
v0.9.0  Phase 7: Observability and production hardening
v1.0.0  Phase 8: Full system production ready
```

### Tagging Commands

```bash
# Annotated tag (always use this — includes message)
git tag -a v0.1.0 -m "Phase 1 complete: Requirement Engineering"
git push origin v0.1.0

# List all tags
git tag -l

# View tag details
git show v0.1.0

# Delete a tag (only if mistake — use carefully)
git tag -d v0.1.0
git push origin --delete v0.1.0
```

---

## 7. Daily Workflow

### Starting new work

```bash
# Always start from latest develop
git checkout develop
git pull origin develop

# Create your feature branch
git checkout -b feature/SA-001-hld-c4-diagrams

# Verify you are on the right branch
git branch
```

### During work

```bash
# Check what has changed
git status

# Stage specific files (never use git add . blindly)
git add docs/hld/c4-system-context.drawio
git add docs/hld/c4-container.drawio

# Commit with proper message
git commit -m "[SA] hld: add C4 level 1 and level 2 diagrams for ecommerce platform"

# Push regularly — do not accumulate unpushed commits
git push origin feature/SA-001-hld-c4-diagrams
```

### Keeping your branch up to date with develop

```bash
# If develop has moved forward while you were working
git checkout develop
git pull origin develop
git checkout feature/SA-001-hld-c4-diagrams
git rebase develop

# Resolve any conflicts, then
git push origin feature/SA-001-hld-c4-diagrams --force-with-lease
```

### Finishing work — open a PR

```bash
# Final push
git push origin feature/SA-001-hld-c4-diagrams

# On GitHub:
# New pull request → base: develop ← compare: feature/SA-001-hld-c4-diagrams
# Fill in PR template
# Merge using Squash and merge
# Delete branch after merge
```

### After PR is merged

```bash
# Sync local develop
git checkout develop
git pull origin develop

# Delete local feature branch
git branch -d feature/SA-001-hld-c4-diagrams
```

---

## 8. Common Scenarios

### Scenario 1 — Fix a mistake in your last commit (not pushed yet)

```bash
# Amend the last commit message
git commit --amend -m "[SA] hld: corrected diagram file name"

# Amend and add forgotten file
git add docs/hld/missing-file.drawio
git commit --amend --no-edit
```

### Scenario 2 — Undo last commit but keep the changes

```bash
git reset HEAD~1
# Files are back to staged/unstaged, commit is gone
```

### Scenario 3 — Develop has moved forward, rebase your branch

```bash
git checkout develop
git pull origin develop
git checkout feature/MY-branch
git rebase develop
# Resolve conflicts if any
git push origin feature/MY-branch --force-with-lease
```

### Scenario 4 — Emergency hotfix on production

```bash
# Branch from main — not develop
git checkout main
git pull origin main
git checkout -b hotfix/fix-payment-timeout

# Make the fix
git add .
git commit -m "[FIX] payment: increase timeout threshold to 5s"
git push origin hotfix/fix-payment-timeout

# PR to main → merge → tag patch version
git checkout main && git pull origin main
git tag -a v0.3.1 -m "Hotfix: payment timeout fix"
git push origin v0.3.1

# PR to develop too — keep them in sync
git checkout develop
git merge main
git push origin develop

# Delete hotfix branch
git branch -d hotfix/fix-payment-timeout
git push origin --delete hotfix/fix-payment-timeout
```

### Scenario 5 — Accidentally committed to develop directly

```bash
# Find the commit hash of the bad commit
git log --oneline -5

# Revert it (creates a new commit that undoes it — safe)
git revert [commit-hash]
git push origin develop

# Then do it properly on a feature branch
git checkout -b feature/RE-correct-branch
# move your work here
```

### Scenario 6 — Pull fails due to diverged branches

```bash
# Always use rebase, never merge when pulling
git pull origin develop --rebase

# Set this globally so it never asks again
git config --global pull.rebase true
```

---

## 9. What Never To Do

| Action | Why |
|--------|-----|
| `git push origin main` directly | Bypasses PR process and protection |
| `git push --force origin main` | Rewrites shared history, destroys others' work |
| `git add .` without checking `git status` | Risk committing secrets, build artifacts, IDE files |
| Commit secrets or API keys | Permanent in git history even if deleted later |
| Commit directly to `develop` | Bypasses PR review and status checks |
| Use `git reset --hard` on pushed commits | Rewrites shared history |
| Leave feature branches open for weeks | Causes merge conflicts and stale branches |
| Squash commits on `release → main` PRs | Loses individual commit history |
| Name branches `test`, `fix`, `update`, `wip` | Meaningless, untrackable |
| Skip the PR description | Future you won't know why this change was made |

---

## 10. Quick Reference

### Most used commands

```bash
# Start new work
git checkout develop && git pull origin develop
git checkout -b feature/[ROLE]-[task]

# Save work
git add [specific files]
git commit -m "[ROLE] scope: description"
git push origin feature/[ROLE]-[task]

# Keep branch fresh
git rebase develop

# Finish work
git push origin feature/[ROLE]-[task]
# Open PR on GitHub → develop

# After PR merged
git checkout develop && git pull origin develop
git branch -d feature/[ROLE]-[task]

# Release a phase
git checkout -b release/phase-[N]-[name]
git push origin release/phase-[N]-[name]
# Open PR on GitHub → main
git checkout main && git pull origin main
git tag -a v0.N.0 -m "Phase N complete: [name]"
git push origin v0.N.0
git checkout develop && git merge main && git push origin develop
```

### Branch at a glance

```
main        → production releases only, tagged
develop     → integration, always stable
feature/*   → all daily work
release/*   → phase completion, merges to main
hotfix/*    → emergency production fixes
```

### Commit prefix at a glance

```
[INIT]   [SKILL]   [RE]   [SA]   [PM]
[DEV]    [QA]      [OPS]  [AWS]  [FIX]   [RELEASE]
```

---

*Follow these practices on every branch, every commit, every PR.
The git history is your project's autobiography — make it readable.*
