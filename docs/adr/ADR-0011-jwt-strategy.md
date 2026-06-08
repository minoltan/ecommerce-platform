# ADR-0011: JWT Authentication Strategy — RS256, Short-Lived Access + Rotating Refresh Tokens

**Status:** Accepted  
**Date:** 2026-06-08  
**Phase:** ARCH  
**Bounded contexts affected:** User/Auth (issuer), all other services (verifiers)  

---

## Context

The platform requires stateless authentication across seven microservices. Any service
that receives a client request must be able to verify the caller's identity without
making a synchronous call to the User/Auth service on every request.

Key requirements:
- Services must verify tokens independently (no per-request auth service call)
- Compromised tokens must be revocable within a bounded time window
- Token refresh must not require the user to re-enter credentials
- Refresh tokens must be rotated on use to limit replay attack surface

---

## Decision

**JWT with RS256 signing.** User/Auth service holds the RSA private key; all other
services verify using the public key (distributed at startup or fetched from a JWKS
endpoint).

| Token type | Algorithm | TTL | Storage |
|---|---|---|---|
| Access token | RS256 | 15 minutes | Client memory / `Authorization: Bearer` header |
| Refresh token | Opaque (UUID) | 7 days | `HttpOnly; Secure` cookie |

### Access token claims

```json
{
  "sub": "{userId}",
  "email": "{email}",
  "role": "CUSTOMER | ADMIN",
  "jti": "{uuid}",
  "iat": {issued-at},
  "exp": {issued-at + 900}
}
```

`jti` (JWT ID) is used for blacklisting (see below).

### Refresh token rotation

1. Client sends refresh token (cookie) to `POST /auth/refresh`
2. Auth service validates token (DB lookup, not expired, not revoked)
3. Auth service **deletes** the old refresh token row from DB
4. Auth service issues new access token + new refresh token
5. New refresh token is stored in DB and returned as `HttpOnly` cookie

Rotation means a stolen refresh token can be used at most once — the first legitimate
refresh invalidates it, and any subsequent attempt (by the attacker) fails.

### Token blacklist (Redis)

On logout or forced invalidation:
- Access token `jti` is added to Redis: `blacklist:{jti}` with TTL = remaining access token lifetime
- All services check this key on each request (Redis GET < 1 ms)
- After TTL expires, the access token is naturally expired anyway — the blacklist entry is self-cleaning

Refresh tokens are revoked by deleting the DB row (no Redis needed; they are opaque, not JWTs).

### JWKS endpoint

`GET /auth/.well-known/jwks.json` returns the RSA public key in JWK format. Services
fetch this at startup and cache it (refreshed every 24 hours or on `401` from an
unexpected key ID).

---

## Consequences

### Positive

- **Stateless verification.** Any service can verify an access token using the public key
  without a network call to Auth service. Eliminates Auth as a synchronous dependency
  on every request.
- **Short TTL limits blast radius.** A stolen access token expires in 15 minutes without
  revocation infrastructure.
- **Refresh rotation prevents replay.** A stolen refresh token can be used at most once;
  the legitimate user's next refresh invalidates it and the system detects a potential
  breach (double-use of a rotated token).
- **HttpOnly cookie for refresh token.** Refresh token is inaccessible to JavaScript,
  preventing XSS-based token theft.
- **RS256 over HS256.** Asymmetric signing means services only need the public key —
  they cannot forge tokens even if compromised. HS256 (shared secret) would mean any
  service could issue tokens.

### Negative

- **15-minute revocation lag.** Forced logout (e.g., password change, account suspension)
  revokes the refresh token immediately but the current access token is valid until
  expiry. The Redis blacklist mitigates this — a forced logout also blacklists the
  access token's `jti`.
- **JWKS distribution.** Services must fetch the JWKS at startup. If Auth service is
  down at a service's startup, the service cannot verify tokens. Mitigated by caching
  the public key in a ConfigMap / environment variable as a fallback.
- **Redis dependency for blacklist.** If Redis is unavailable, the blacklist check fails.
  Policy: fail-open (skip blacklist check) or fail-closed (reject all requests). Decision:
  **fail-open for access tokens** (15-min TTL is the safety net); **fail-closed for
  admin operations** (admins must wait for Redis recovery before privileged actions).
- **Refresh token DB row.** Each active session has a DB row in `refresh_tokens`. At
  1M active users × 3 devices = 3M rows. With 7-day TTL and a nightly sweep, table
  size is bounded. Index on `token` (UNIQUE) and `user_id`.

---

## Alternatives Rejected

### HS256 (HMAC-SHA256, shared secret)

Single secret key used for both signing and verification. Simpler key management.

Rejected because:
- Any service that can verify tokens can also issue them. A compromised microservice
  (e.g., Notification service) could forge access tokens for any user.
- RS256 asymmetric signing means only User/Auth (private key holder) can issue tokens.

### Opaque tokens with per-request validation

Issue short random tokens; validate against a Redis or DB lookup on every request
(like OAuth2 introspection endpoint).

Pros: Instant revocation; no TTL-based lag.

Rejected because:
- Creates a synchronous dependency on Auth service for every authenticated request.
  If Auth is slow or down, all services degrade.
- Adds ~5–20 ms per request for the Auth round-trip.
- Negates the stateless verification benefit of JWTs.

### Session cookies (server-side sessions)

Store session state in Redis; client holds only a session ID cookie. The API Gateway
looks up the session on every request.

Rejected because:
- Requires API Gateway to have a Redis dependency and session store logic.
- Does not compose cleanly with mobile clients (cookie management on native apps).
- Stateful — contradicts the microservices principle of stateless services.

### Phase 2 (Cognito)

AWS Cognito handles JWT issuance, JWKS, refresh rotation, and MFA natively. The Phase 2
decision is to migrate to Cognito User Pools, replacing the custom User/Auth JWT
implementation. The token format (JWT RS256) and the verification pattern (JWKS) remain
identical — services need only update the JWKS endpoint URL. This migration path is
explicitly designed into Phase 1 by using standard JWT formats.
