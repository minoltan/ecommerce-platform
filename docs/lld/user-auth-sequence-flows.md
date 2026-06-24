# User/Auth Service — AuthService Sequence Flows

Companion to `user-auth-lld.md` §6 (Authentication & Session Strategy) and §8
(Sequence Diagrams). Walks the five core `AuthService` flows — register, login,
request authentication, refresh, logout — as implemented today, each verified
line-by-line against the current code. Corrections from the original hand-drawn
drafts are called out explicitly so the gap between intended and actual behaviour
is visible, rather than silently fixed.

---

## 1. Register

```mermaid
sequenceDiagram
    participant C as Client
    participant AC as AuthController
    participant AS as AuthService
    participant Redis
    participant DB as MySQL (users, email_verifications)
    participant OB as user_auth_outbox

    C->>AC: POST /v1/auth/register
    AC->>AS: register(email, password, displayName, clientIp)
    AS->>Redis: tryConsume rate:{ip}:register (10/hr per IP)
    AS->>DB: existsByEmail(email) — fast-path check
    alt email already taken (fast path)
        AS-->>AC: EmailAlreadyRegisteredException
        AC-->>C: 409 EMAIL_ALREADY_REGISTERED
    else fast path clear
        AS->>DB: User.register() — bcrypt hash (cost 12), status UNVERIFIED
        AS->>DB: saveAndFlush(user) — INSERT runs now, not at commit
        alt uq_users_email violated (race loser)
            AS-->>AC: EmailAlreadyRegisteredException
            AC-->>C: 409 EMAIL_ALREADY_REGISTERED
        else insert succeeds
            AS->>DB: EmailVerification.issue() — 24h TTL token
            AS->>OB: write UserRegistered outbox row
            AC-->>C: 201 {userId}
        end
    end
    Note over OB: Kafka relay polls the outbox separately
```

**Verified against:** `AuthController.java:44-50` (`POST /v1/auth/register`, `201` +
`RegisterResponse{userId}`), `AuthService.java:77-101` (`register`),
`PasswordHash.java:19` (`BCrypt.hashpw(..., gensalt(12))`), `V1__init.sql:14`
(`uq_users_email`).

**Corrections from the original draft:** the draft showed a single
`User.register()` step straight to `201`. Two things were missing:

1. The `existsByEmail` fast-path check (gives the common duplicate-email case an
   immediate, cheap `409` without paying for a bcrypt hash + DB round trip).
2. The **race-safety net** — `save` → `saveAndFlush` plus a catch on
   `DataIntegrityViolationException`, added in **ADR-0015**. Two concurrent
   registrations for the same email can both pass the fast-path check (it's a
   plain read, not a lock); `uq_users_email` is the real guard, and
   `saveAndFlush` forces the INSERT to run synchronously so the constraint
   violation is caught and translated to the same `409` instead of leaking out
   as a raw `500`.

---

## 2. Login

```mermaid
sequenceDiagram
    participant C as Client
    participant AS as AuthService
    participant DB as MySQL (users)
    participant Redis
    participant JWT as JwtService
    participant RT as RefreshTokenRepository
    participant OB as user_auth_outbox

    C->>AS: login(email, password)
    AS->>DB: findByEmail(email)
    alt not found
        AS-->>C: InvalidCredentialsException (401)
    end
    AS->>Redis: tryConsume rate:{userId}:login (5/15min)
    alt rate-limited
        AS-->>C: RateLimitExceededException (429)
    end
    AS->>AS: user.login(password) — BCrypt.checkpw, status guard
    AS->>Redis: reset rate:{userId}:login
    AS->>DB: save(user) — no field changes, no-op UPDATE
    AS->>OB: write UserLoggedIn outbox row
    AS->>JWT: issueAccessToken(user)
    JWT-->>AS: RS256 JWT {sub, email, role, jti, iat, exp=+15m, iss}
    AS->>RT: issue(userId) — opaque refresh token
    RT->>Redis: SET refresh:{userId}:{tokenId} = SHA256(secret), TTL 7d
    AS-->>C: TokenResponse {accessToken, refreshToken, expiresIn, tokenType: "Bearer"}
```

**Verified against:** `AuthService.java:122-140` (`login`), `JwtService.java:41-59`
(claim set + TTL from `application.yml:64`, `900`s), `RefreshTokenRepository.java:38-43`
(`issue`), `TokenResponse.java:6-15`.

**Corrections from the original draft:**

- Missing the `findByEmail` lookup — required *before* the rate-limit check,
  since the limiter key is `rate:{userId}:login`, not `rate:{email}:login`; you
  need the user row to even form the key.
- Missing the `rateLimitRepository.reset()` call on success (otherwise a user's
  failed-attempt counter never clears between lockout windows).
- Missing the `UserLoggedIn` outbox write — `login` is one of the two domain
  events for this context (`UserRegistered`, `UserLoggedIn` per `CLAUDE.md`'s
  bounded-context table); omitting it understates what the flow actually does.
- The `save(user)` call is real but currently a no-op in practice: `User.login()`
  only validates and throws — it mutates no fields — so Hibernate's dirty-check
  skips the UPDATE. Included above for fidelity to the code, not because it has
  an observable effect today.
- JWT claims and `TokenResponse` fields were abbreviated in the draft
  (`exp=15m` only, `{accessToken, refreshToken}` only) — `iat`/`iss` and
  `expiresIn`/`tokenType` are also present.

---

## 3. Request Authentication (filter chain)

```mermaid
sequenceDiagram
    participant C as Client
    participant SF as Spring Security filter chain
    participant BT as BearerTokenAuthenticationFilter
    participant BL as JwtBlacklistFilter
    participant Redis
    participant Ctrl as Controller

    C->>SF: GET /v1/something (Authorization: Bearer {jwt})
    SF->>BT: NimbusJwtDecoder.withPublicKey(...) verifies RS256 signature + exp
    BT->>BT: JwtAuthenticationConverter maps claim "role" → ROLE_{role}
    BT->>BL: addFilterAfter(..., BearerTokenAuthenticationFilter.class)
    BL->>Redis: isBlacklisted(jti) — GET blacklist:{jti}
    alt blacklisted
        BL-->>C: 401 Token revoked
    else not blacklisted, or Redis lookup throws → fail-open
        BL->>Ctrl: forward request, SecurityContext populated
        Ctrl-->>C: 200
    end
```

**Verified against:** `SecurityConfig.java:48-50` (`NimbusJwtDecoder`),
`SecurityConfig.java:54-66` (`JwtAuthenticationConverter` / role mapping),
`SecurityConfig.java:43` (`addFilterAfter`), `JwtBlacklistFilter.java:47-49`
(catch-and-allow on Redis failure), `TokenBlacklistRepository.java:30-36`
(`blacklist:{jti}`).

**Corrections from the original draft:** none — this one matched the
implementation as drawn. Only change above: made `Redis` an explicit
participant for consistency with the other diagrams, instead of folding the
lookup into a `BL->>BL` self-call.

---

## 4. Refresh

```mermaid
sequenceDiagram
    participant C as Client
    participant AS as AuthService
    participant RT as RefreshTokenRepository
    participant Redis
    participant DB as MySQL (users)
    participant JWT as JwtService

    C->>AS: refresh(oldRefreshToken)
    AS->>RT: rotate(oldRefreshToken)
    RT->>Redis: GETDEL refresh:{userId}:{oldTokenId} — atomic read + revoke
    Redis-->>RT: storedHash | null
    alt null, or hash mismatch
        RT-->>AS: Optional.empty()
        AS-->>C: InvalidRefreshTokenException (401)
    else hash matches
        RT->>RT: issue() new token
        RT->>Redis: SET refresh:{userId}:{newTokenId} = SHA256(newSecret), TTL 7d
        RT-->>AS: {userId, newRefreshToken}
        AS->>DB: findById(userId)
        AS->>JWT: issueAccessToken(user) — new 15m JWT
        AS-->>C: {newAccessToken, newRefreshToken}
    end
    Note over RT: GETDEL makes read+revoke one atomic step — two concurrent<br/>rotations of the same token can no longer both succeed (ADR-0015)
```

**Verified against:** `AuthService.java:146-154` (`refresh`),
`RefreshTokenRepository.java:61-71` (`rotate`, post-ADR-0015).

**Corrections from the original draft:** this is the flow that just changed.
The draft described the *pre-fix* behaviour — separate "validate hash, DELETE
old key" then "issue new token, SET new key" steps. That's a real
check-then-act race: two requests presenting the same token could both read the
stored hash before either deleted it, both pass validation, and both issue a
new session for one rotation. The fix (ADR-0015) replaced the GET-then-DELETE
with a single atomic Redis `GETDEL` — whichever request's `GETDEL` runs first
gets the stored hash; the other gets `null` and correctly fails. The diagram
above reflects the current, fixed code.

Also missing from the draft: the `userRepository.findById(rotated.userId())`
lookup between getting the rotated token back and issuing the new access
token — needed because `issueAccessToken` signs claims (`email`, `role`) off
the full `User`, not just the `userId`.

The draft's closing note — *"old token now unusable — reuse = signal of
theft"* — describes the **design intent** from `user-auth-lld.md` §6.2
("...the caller should treat this as a possible token-replay and may choose to
revoke all sessions for the user"), not automated behaviour. `AuthService.refresh`
does not itself detect reuse and revoke all sessions on a failed rotation —
it just throws `InvalidRefreshTokenException`. Reacting to repeated rotation
failures as a compromise signal is a caller-side decision that isn't
implemented today.

---

## 5. Logout

```mermaid
sequenceDiagram
    participant C as Client
    participant AC as AuthController
    participant JWT as JwtService
    participant AS as AuthService
    participant RT as RefreshTokenRepository
    participant TBR as TokenBlacklistRepository
    participant Redis

    C->>AC: POST /v1/auth/logout {refreshToken}<br/>Authorization: Bearer {accessToken}
    alt Authorization header missing or not "Bearer "
        AC-->>C: 401 INVALID_ACCESS_TOKEN
    end
    AC->>JWT: parse(accessToken) → Claims
    alt token malformed/expired/unparseable
        AC-->>C: 401 INVALID_ACCESS_TOKEN
    end
    AC->>AC: remainingTtl = claims.exp - now
    AC->>AS: logout(refreshToken, claims.jti, remainingTtl)
    AS->>RT: revoke(refreshToken)
    RT->>Redis: DEL refresh:{userId}:{tokenId}
    AS->>TBR: blacklist(jti, remainingTtl)
    TBR->>Redis: SET blacklist:{jti} = "1" EX remainingTtl
    AC-->>C: 204 No Content
```

**Verified against:** `AuthController.java:67-84` (`logout`),
`AuthService.java:160-166` (`logout`), `RefreshTokenRepository.java:74-76`
(`revoke`), `TokenBlacklistRepository.java:23-28` (`blacklist`).

**Notes (this flow wasn't in the original four drafts):**

- The access token is mandatory here, not optional: `AuthController.logout`
  throws `InvalidAccessTokenException` (`401`) if the `Authorization` header is
  missing, malformed, or fails to parse — there's no logout path that skips
  blacklisting. `AuthService.logout`'s `accessTokenJti` parameter is nullable
  at the method-signature level, but the controller never actually calls it
  with `null`.
- `revoke()` is a silent no-op if `refreshToken` is malformed or already gone
  (`RefreshTokenRepository.parse` returns empty, `.ifPresent` does nothing) —
  logout never fails because of a bad refresh token, only because of a bad
  access token.
- `blacklist()` itself no-ops if `remainingTtl` is zero/negative (token already
  expired) — nothing to blacklist, it would fail open on its own anyway.
- This is single-device logout. Logout-all-devices and the `deactivate()` path
  use `RefreshTokenRepository.revokeAll` (`SCAN refresh:{userId}:*` + `DEL`)
  instead of a single `revoke` — see `user-auth-lld.md` §8.1 for the
  deactivation flow.
