// k6 load test for user-service, targeting the AWS/EKS deployment from
// phase1/DEPLOYING_AWS.md. See phase1/LOAD_TESTING.md for how to run this and read
// the results.
//
// Rate limits actually enforced by the service today (RateLimitRepository.java) —
// this script is designed around them, not against them:
//   - POST /auth/register: 10 per hour per source IP (AuthService.REGISTER_MAX_ATTEMPTS)
//   - POST /auth/login: only *failed* attempts are throttled (5 per 15 min per user,
//     account lockout) — successful logins are NOT rate-limited, so this is the right
//     endpoint to drive real load through
//   - RateLimitRepository.endpointKey() (500 req/min per user) is scaffolded but not
//     wired into any controller yet — /auth/refresh and /auth/jwks currently have no
//     server-side throttle, which is worth knowing when you interpret their numbers
//
// Because registration is capped at 10/hour/IP, this script creates a small pool of
// users ONCE in setup() (not per-iteration) and load-tests login/refresh/jwks against
// that pool — the only approach compatible with the real limiter.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL; // e.g. http://<LB_HOST> from DEPLOYING_AWS.md step 6
const USER_POOL_SIZE = 8; // stays under the 10/hour/IP register cap with headroom
const TEST_PASSWORD = 'LoadTest-Passw0rd!23';

const loginErrors = new Rate('login_errors');
const loginDuration = new Trend('login_duration', true);
const refreshErrors = new Rate('refresh_errors');
const refreshDuration = new Trend('refresh_duration', true);
const jwksDuration = new Trend('jwks_duration', true);

export const options = {
  scenarios: {
    // Runs first and alone: confirms the register throttle actually fires at 10/hr.
    // Not a load scenario — a correctness check, kept to a handful of requests.
    register_throttle_check: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: USER_POOL_SIZE + 2, // 2 extra requests should come back 429
      exec: 'registerThrottleCheck',
      startTime: '0s',
    },
    // Main load: repeated logins against the pre-seeded pool.
    login_load: {
      executor: 'ramping-vus',
      exec: 'loginLoad',
      startTime: '20s', // after register_throttle_check has finished
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 },
        { duration: '2m', target: 20 },
        { duration: '30s', target: 50 },
        { duration: '2m', target: 50 },
        { duration: '30s', target: 0 },
      ],
    },
    // Refresh-token rotation load, same window as login_load.
    refresh_load: {
      executor: 'ramping-vus',
      exec: 'refreshLoad',
      startTime: '20s',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },
        { duration: '4m', target: 10 },
        { duration: '30s', target: 0 },
      ],
    },
    // JWKS baseline — cheap/cacheable, establishes the latency floor for comparison.
    jwks_load: {
      executor: 'constant-vus',
      exec: 'jwksLoad',
      startTime: '20s',
      vus: 5,
      duration: '5m',
    },
  },
  thresholds: {
    // Proposed baseline for THIS test, not an official NFR — non-functional-requirements.md
    // has no NFR-PERF entry for User/Auth login/refresh yet. Promote to a real
    // NFR-PERF-0XX if these numbers turn out to matter.
    login_duration: ['p(95)<300', 'p(99)<600'],
    login_errors: ['rate<0.01'],
    refresh_duration: ['p(95)<200'],
    refresh_errors: ['rate<0.01'],
    jwks_duration: ['p(95)<50'],
  },
};

export function setup() {
  const users = [];
  for (let i = 0; i < USER_POOL_SIZE; i++) {
    const email = `loadtest-user-${i}-${Date.now()}@example.com`;
    const res = http.post(
      `${BASE_URL}/v1/auth/register`,
      JSON.stringify({ email, password: TEST_PASSWORD }),
      { headers: { 'Content-Type': 'application/json' } }
    );
    check(res, { 'setup: register succeeded': (r) => r.status === 201 || r.status === 200 });
    users.push({ email, password: TEST_PASSWORD });
    sleep(0.2); // stay well clear of the 10/hour/IP window boundary
  }
  return { users };
}

// Runs once, before login_load/refresh_load/jwks_load, to prove the 429 boundary.
// Uses its own throwaway emails — separate from setup()'s pool so it doesn't consume
// slots the load scenarios need.
export function registerThrottleCheck() {
  const email = `throttle-check-${__ITER}-${Date.now()}@example.com`;
  const res = http.post(
    `${BASE_URL}/v1/auth/register`,
    JSON.stringify({ email, password: TEST_PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  if (__ITER < USER_POOL_SIZE) {
    check(res, { 'register: within limit succeeds': (r) => r.status === 201 || r.status === 200 });
  } else {
    check(res, { 'register: over limit returns 429': (r) => r.status === 429 });
  }
}

export function loginLoad(data) {
  const user = data.users[Math.floor(Math.random() * data.users.length)];
  const res = http.post(
    `${BASE_URL}/v1/auth/login`,
    JSON.stringify({ email: user.email, password: user.password }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  loginDuration.add(res.timings.duration);
  const ok = check(res, {
    'login: status 200': (r) => r.status === 200,
    'login: has accessToken': (r) => !!r.json('accessToken'),
  });
  loginErrors.add(!ok);
  sleep(1);
}

export function refreshLoad(data) {
  const user = data.users[Math.floor(Math.random() * data.users.length)];
  const loginRes = http.post(
    `${BASE_URL}/v1/auth/login`,
    JSON.stringify({ email: user.email, password: user.password }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  const refreshToken = loginRes.json('refreshToken');
  if (!refreshToken) {
    refreshErrors.add(true);
    return;
  }
  const res = http.post(
    `${BASE_URL}/v1/auth/refresh`,
    JSON.stringify({ refreshToken }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  refreshDuration.add(res.timings.duration);
  const ok = check(res, { 'refresh: status 200': (r) => r.status === 200 });
  refreshErrors.add(!ok);
  sleep(1);
}

export function jwksLoad() {
  const res = http.get(`${BASE_URL}/v1/auth/.well-known/jwks.json`);
  jwksDuration.add(res.timings.duration);
  check(res, { 'jwks: status 200': (r) => r.status === 200 });
  sleep(0.5);
}
