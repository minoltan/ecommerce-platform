# JWT & Security — Interview Questions & Answers

---

### Basic

**1. What is a JWT? What are its three parts?**

A JSON Web Token is a compact, URL-safe token for transmitting claims between parties. It has three Base64URL-encoded parts separated by dots:
1. **Header** — algorithm and token type: `{"alg":"RS256","typ":"JWT"}`.
2. **Payload** — claims: `{"sub":"<userId>","role":"CUSTOMER","jti":"<uuid>","iss":"ecommerce-platform","iat":...,"exp":...}`.
3. **Signature** — cryptographic signature over header + payload.

The signature allows any holder of the public key to verify the token was issued by the party holding the private key.

---

**2. What is the difference between authentication and authorisation?**

- **Authentication** — verifying identity: "Who are you?" Handled by `POST /v1/auth/login`, which validates email + password and issues a JWT.
- **Authorisation** — verifying permissions: "What are you allowed to do?" Handled by `SecurityConfig` — `hasRole("ADMIN")` on `/v1/admin/**` checks the `role` claim in the JWT.

---

**3. What is the difference between symmetric (HS256) and asymmetric (RS256) JWT signing?**

- **HS256** — HMAC with SHA-256. Uses a single shared secret to both sign and verify. Any party that can verify can also forge tokens.
- **RS256** — RSA with SHA-256. Uses a private key to sign and a public key to verify. Only the user-service holds the private key; downstream services only need the public key (from JWKS) to verify.

---

**4. Why does this project use RS256 instead of HS256?**

Per ADR-0011: in a microservices architecture, multiple services (product-catalog, order, cart) must verify JWTs. With HS256, every service would need the shared secret — any compromise of one service exposes the secret to all. With RS256, only the user-service holds the private key. Downstream services use the public key from `/v1/auth/.well-known/jwks.json` to verify signatures — they can verify but never forge tokens.

---

**5. What is an access token vs a refresh token?**

- **Access token** — short-lived JWT (15 minutes, `access-token-ttl-seconds: 900`). Sent in `Authorization: Bearer <token>` on every API request. Stateless — verified via signature; no server-side lookup needed.
- **Refresh token** — long-lived opaque token (7 days). Stored server-side in Redis. Used only at `POST /v1/auth/refresh` to get a new access token pair without re-authenticating with password. Rotated on each use.

---

**6. What is the `jti` claim in a JWT? How is it used here?**

`jti` (JWT ID) is a unique identifier for the token — a UUID generated per issued token. In this project, on logout, the `jti` of the access token is stored in Redis with a TTL equal to the token's remaining lifetime. `JwtBlacklistFilter` checks every incoming request's `jti` against the blacklist. If found, the request is rejected even though the token signature is still valid. This solves the stateless JWT logout problem.

---

**7. What does `bearerFormat: JWT` mean in OpenAPI?**

It is a documentation hint in the OpenAPI security scheme definition. `type: HTTP`, `scheme: bearer` tells Swagger UI to show a text box for the `Authorization: Bearer <token>` header. `bearerFormat: JWT` is purely informational — it tells API consumers the expected token format is JWT (not an opaque string). It has no functional effect on validation.

---

### Intermediate

**8. Why does the user-service expose `/v1/auth/.well-known/jwks.json`? Who consumes it?**

JWKS (JSON Web Key Set) is the standard format (RFC 7517) for publishing public keys. Downstream microservices (product-catalog, order, etc.) configure their Spring Security OAuth2 resource server with:
```yaml
spring.security.oauth2.resourceserver.jwt.jwk-set-uri: http://user-service:8081/v1/auth/.well-known/jwks.json
```
Spring Security fetches and caches the public key from this endpoint and uses it to verify every incoming JWT. This avoids distributing static public key files and supports key rotation.

---

**9. How does `NimbusJwtDecoder` validate a JWT?**

It performs these checks in order:
1. Fetches the public key from the configured `jwk-set-uri` (cached).
2. Verifies the signature using the RSA public key.
3. Checks the `exp` claim — rejects expired tokens.
4. Checks the `iss` claim if configured.
5. Returns the decoded `Jwt` object for further processing.

In `SecurityConfig`: `NimbusJwtDecoder.withPublicKey((RSAPublicKey) jwtKeyPair.getPublic()).build()` configures it directly with the in-memory public key rather than fetching from a URL.

---

**10. What is token blacklisting? Why is it needed when JWTs are stateless?**

JWTs are validated purely by signature — the server holds no session state. This means a stolen or logged-out token remains valid until it expires. Blacklisting adds a server-side revocation list: on logout, the token's `jti` is stored in Redis with TTL = remaining token lifetime. `JwtBlacklistFilter` rejects any request whose `jti` is in the blacklist. The TTL ensures the Redis key is auto-deleted when the token would have expired anyway — no manual cleanup needed.

---

**11. How does `JwtBlacklistFilter` prevent use of a logged-out access token?**

It is positioned in the filter chain **after** `BearerTokenAuthenticationFilter`. After the JWT is cryptographically validated and the `SecurityContext` is populated, `JwtBlacklistFilter` reads the `jti` claim from the authenticated token and calls `TokenBlacklistRepository.isBlacklisted(jti)`. If the Redis key `blacklist:{jti}` exists, it clears the `SecurityContext` and returns 401. Because it runs after authentication, the `jti` is always available and already validated.

---

**12. What is refresh token rotation? What security property does it provide?**

On every `POST /v1/auth/refresh`, the presented refresh token is **revoked** and a **new** refresh token is issued. This means each refresh token is single-use. If an attacker steals a refresh token and uses it, the legitimate user's next refresh attempt will fail (the token is already revoked), alerting them that a token was stolen. The `RefreshTokenRepository.rotate()` method atomically: (1) validates the presented token, (2) deletes the Redis key, (3) issues a new token.

---

**13. What is the `role` claim in the JWT used for in `SecurityConfig`?**

The `jwtAuthenticationConverter()` reads the `role` claim from the JWT payload and converts it to a Spring Security `GrantedAuthority` of the form `ROLE_<role>`. For example, `role: "ADMIN"` becomes `ROLE_ADMIN`. The `hasRole("ADMIN")` check in `SecurityConfig` then works against this authority. The custom converter is needed because Spring Security's default JWT converter looks for a `roles` or `authorities` array, not a single `role` string claim.

---

**14. Why must the JWT private key be stable in Kubernetes multi-replica deployments?**

`JwtKeyConfig` generates an ephemeral RSA key pair at startup if `jwt.private-key` is blank. Each pod generates a different key. A client whose token was signed by pod A will get a 401 when their request is routed to pod B (which has a different public key). In Kubernetes, the private and public keys must be provided via a `Secret` so all replicas share the same key pair.

---

**15. What is PKCS#8 format for a private key?**

PKCS#8 is a standard format for storing private keys. The `JwtKeyConfig` uses `PKCS8EncodedKeySpec` to load the private key from Base64-encoded DER bytes. This is the format produced by:
```bash
openssl genrsa 2048 | openssl pkcs8 -topk8 -nocrypt -outform DER | base64 -w0
```
The public key is loaded using `X509EncodedKeySpec` (the standard public key format). Java's `KeyFactory.getInstance("RSA")` handles both formats natively.

---

**16. What is the difference between `permitAll()` and `anonymous()` in Spring Security?**

- `permitAll()` — allows access to any user, whether authenticated or not (anonymous, authenticated, or any role).
- `anonymous()` — only allows anonymous (unauthenticated) users; authenticated users are rejected.

The Auth endpoints use `permitAll()` because a logged-in user should also be able to call `POST /v1/auth/register` (e.g., to register another account). Using `anonymous()` would block them.

---

**17. What is rate limiting? How is it implemented using Redis in `RateLimitRepository`?**

Rate limiting restricts how many times an action can be performed within a time window. `RateLimitRepository.tryConsume()` uses Redis `INCR` + `EXPIRE`:
- On first call: `INCR rate:{key}` returns 1; `EXPIRE rate:{key} <window>` sets the TTL.
- On subsequent calls within the window: `INCR` returns 2, 3, ...
- If the count exceeds the limit, `tryConsume` returns `false` and `AuthService` throws `RateLimitExceededException` (HTTP 429).

Login: 5 attempts per 15 minutes per `userId`. Registration: 10 attempts per hour per `clientIp`.

---

### Advanced

**18. If two replicas had different RSA key pairs, what would happen?**

Every JWT signed by replica A contains a signature made with A's private key. Replica B only has its own key pair, so `NimbusJwtDecoder` on B would fail signature verification and return 401. With N replicas each having different keys, roughly `(N-1)/N` of requests would fail depending on which replica handled the login vs which handles subsequent API calls. This is why a shared, stable key pair via Kubernetes Secret is mandatory.

---

**19. How would you implement key rotation without downtime?**

JWKS supports multiple keys simultaneously. During rotation:
1. Generate a new RSA key pair with a new `kid` (key ID).
2. Add the new public key to the JWKS endpoint (publish both old and new).
3. Update the application to sign new tokens with the new private key.
4. Wait until all tokens signed with the old key expire (max 15 minutes = access token TTL).
5. Remove the old key from JWKS.

Spring Security's JWKS consumer caches the key set and re-fetches when it encounters an unknown `kid`, so it handles multiple keys transparently.

---

**20. What is the OAuth2 Resource Server pattern?**

A Resource Server is a service that protects resources using OAuth2 access tokens. It does not issue tokens (that's the Authorization Server's job — in this project, the user-service acts as a simplified AS). The resource server validates the Bearer token on every request and grants or denies access. In Spring Security, `oauth2ResourceServer(oauth2 -> oauth2.jwt(...))` configures the service as a resource server that validates RS256 JWTs. Other microservices in this project will be pure resource servers pointing at the user-service's JWKS endpoint.

---

**21. What is the security implication of storing the refresh token hash vs plain text?**

`RefreshTokenRepository` stores `SHA-256(secret)` in Redis, not the raw secret. The token returned to the client is `userId.tokenId.secret`. If Redis is compromised, attackers get the hash but cannot reverse it to the original secret (SHA-256 is a one-way function). They also cannot construct a valid token string without the secret. Plain-text storage would mean a Redis breach immediately exposes all active refresh tokens, allowing attackers to impersonate all logged-in users.
