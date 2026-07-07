# Load Testing user-service (against the AWS deployment)

Run this against the EKS deployment from `phase1/DEPLOYING_AWS.md` — not local. Local
Docker/Kubernetes on a laptop can't produce a trustworthy load-test number (shared
CPU with the load generator itself, no real network hop, no realistic node sizing),
which is why this is a separate guide.

**Tool: k6.** Chosen over Gatling for this test — `WORKFLOW.md` lists both as
Phase 4 (QA) options; k6's JS scripting and CLI-first workflow needs less setup than
Gatling's Scala/Maven toolchain for a single-service test session. Revisit for
Phase 4 proper once more services (and JVM-based contract-test reuse) are in play.

---

## What this script actually tests

`phase1/user-service/load-test/auth-load-test.js` is built around the rate limits
**actually implemented** in `RateLimitRepository`/`AuthService` today — not the
gateway-level limit mentioned in that class's Javadoc, which isn't enforced anywhere
yet because the API Gateway doesn't exist:

| Endpoint | Real limit today | Load-test approach |
|---|---|---|
| `POST /v1/auth/register` | 10/hour per source IP | Only exercised in a small `register_throttle_check` scenario (12 requests) that proves the 429 boundary — not a volume scenario |
| `POST /v1/auth/login` | None on success; 5 *failed* attempts/15min locks the account | Main load scenario — a small pool of users (created once in `setup()`) get logged into repeatedly at increasing concurrency |
| `POST /v1/auth/refresh` | None currently wired up | Secondary load scenario — useful for raw capacity numbers, but there's no 429 to validate |
| `GET /v1/auth/.well-known/jwks.json` | None | Baseline scenario — cheap, mostly-static response, establishes the latency floor everything else is compared against |

Because registration is IP-throttled at 10/hour, you cannot load-test registration
volume from a single k6 host without either running from multiple source IPs or
accepting you're testing the throttle, not the endpoint's raw capacity. This script
doesn't try to — that's a deliberate scope decision, not an oversight.

The `thresholds` in the script (p95/p99 targets) are a **proposed baseline for this
test session**, not an official requirement — `docs/requirements/non-functional-requirements.md`
has no `NFR-PERF` entry for User/Auth login/refresh yet. If these numbers turn out to
matter, promote them to a real `NFR-PERF-0XX` entry (see `WORKFLOW.md`'s ID Reference)
rather than leaving them only in a test script.

---

## Prerequisites

```bash
# macOS
brew install k6

# Linux (Debian/Ubuntu) — see https://k6.io for other package managers
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update && sudo apt-get install k6

# Or just download the binary — no install needed
curl -sfL https://github.com/grafana/k6/releases/download/v0.54.0/k6-v0.54.0-linux-amd64.tar.gz \
  | tar xz --strip-components=1 -C /usr/local/bin k6-v0.54.0-linux-amd64/k6
```

You also need `$LB_HOST` from `phase1/DEPLOYING_AWS.md` step 6 (the user-service
NLB's external hostname).

---

## Run it

```bash
export BASE_URL=http://$LB_HOST   # from DEPLOYING_AWS.md step 6

k6 run phase1/user-service/load-test/auth-load-test.js
```

Total runtime: ~6 minutes (`register_throttle_check` finishes in seconds;
`login_load`/`refresh_load`/`jwks_load` run in parallel for ~5.5 minutes after a
20s stagger).

To save results for later comparison (e.g., against a future replica-count or JVM
tuning change):

```bash
k6 run --summary-export=results-$(date +%Y%m%d-%H%M).json \
  phase1/user-service/load-test/auth-load-test.js
```

---

## Reading the output

k6 prints a summary per metric at the end. The ones that matter here:

| Metric | What it tells you |
|---|---|
| `login_duration` (p95/p99) | Bounded mostly by bcrypt's cost factor + DB round trip. If this threshold fails, the next question is whether it's CPU-bound (check `kubectl top pods -n ecommerce`) or DB-bound (check MySQL connection pool saturation — NFR-SCALE-007's 50-connection HikariCP cap) |
| `refresh_duration` (p95) | Redis-bound (refresh token lookup + rotation) — should be noticeably faster than login since there's no bcrypt hashing on this path |
| `jwks_duration` (p95) | The latency floor — if login/refresh times are only marginally above this, the service has headroom; if they're an order of magnitude above it, that's the real signal, not the absolute number |
| `login_errors` / `refresh_errors` rate | Anything above ~1% during the steady-state stages (not the ramp) means the service is shedding load — check HPA (`kubectl get hpa -n ecommerce`) actually scaled toward its max 4 replicas before concluding it's under-provisioned |
| `register_throttle_check` checks | Should show ~8/8 passes on `register: within limit succeeds` and ~2/2 on `register: over limit returns 429` — if the 429 checks fail, the rate limiter isn't working across pods (Redis-backed counter should be shared; a per-pod in-memory limiter would fail this) |

### Watching the cluster while the test runs

Open a second terminal:

```bash
watch kubectl -n ecommerce get pods,hpa
```

Confirm the HPA actually reacts (scale event visible in `kubectl describe hpa
user-service-hpa -n ecommerce`) during the 50-VU stage — if it never scales past 2
replicas even under load, either the load isn't CPU-intensive enough to cross the
60%-for-90s threshold (NFR-SCALE-006), or something's capping CPU usage lower than
expected (worth a closer look with `kubectl top pods`, not necessarily a bug).

---

## After the test

Load testing is the reason the AWS environment exists for this session — once
you have results, go straight to `phase1/DEPLOYING_AWS.md`'s **Teardown** section.
Don't leave the cluster up "just in case you want to re-run it" — re-running later
is just `eksctl create cluster` again, ~$0.20/hr while it exists either way.
