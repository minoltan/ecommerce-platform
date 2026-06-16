# Redis — Interview Questions & Answers

---

### Basic

**1. What is Redis? What data structures does it support?**

Redis (Remote Dictionary Server) is an in-memory data store used as a cache, message broker, and session store. It supports:
- **String** — key → value (used in this project for all three use cases).
- **Hash** — key → field/value map.
- **List** — ordered list of strings.
- **Set** — unordered unique strings.
- **Sorted Set** — set with a score (used for leaderboards, rate limiting alternatives).
- **Stream** — append-only log (Kafka-like within Redis).

---

**2. What does TTL (Time-To-Live) mean in Redis?**

TTL is an expiry time set on a Redis key. After TTL seconds, the key is automatically deleted by Redis. This is critical in this project:
- Refresh tokens expire in 7 days (`TTL = Duration.ofDays(7)`).
- Blacklisted JTIs expire when the access token would have expired (remaining TTL = 15min max).
- Rate limit counters expire at the end of their window (15min for login, 1hr for registration).

TTL means no manual cleanup — Redis handles expiry automatically.

---

**3. What is the difference between Redis as a cache and Redis as a primary store?**

- **Cache** — stores copies of data from a primary DB (MySQL). On cache miss, fetch from DB and populate. Data can be lost without consequence — just a performance cost. Used in Cart service (Phase 1).
- **Primary store** — Redis is the source of truth. Data loss = data loss. In this project, refresh tokens and token blacklist are stored **only** in Redis (no MySQL backup), making Redis a primary store for session data.

This distinction matters for Redis persistence config: as a primary store, AOF (Append Only File) persistence should be enabled in production.

---

### Intermediate

**4. How is Redis used in this project? Name all three use cases.**

| Use Case | Key Pattern | TTL | Implementation |
|---|---|---|---|
| Refresh token storage | `refresh:{userId}:{tokenId}` | 7 days | `RefreshTokenRepository` |
| JWT access token blacklist | `blacklist:{jti}` | remaining access token TTL | `TokenBlacklistRepository` |
| Login/registration rate limiting | `rate:{userId}:login`, `rate:{ip}:register` | 15min / 1hr | `RateLimitRepository` |

---

**5. How does `TokenBlacklistRepository` use Redis to blacklist a JWT `jti`?**

On logout, `AuthService.logout()` calls `tokenBlacklistRepository.blacklist(jti, remainingTtl)`. This sets a Redis key `blacklist:{jti}` with a value (e.g., `"1"`) and TTL equal to the token's remaining lifetime. `JwtBlacklistFilter` calls `isBlacklisted(jti)` which checks if the key exists using `redis.hasKey("blacklist:" + jti)`. When the access token's `exp` passes, the Redis key auto-expires — no manual cleanup.

---

**6. How does `RefreshTokenRepository` store and rotate refresh tokens?**

**Storage:** The token returned to the client is `"{userId}.{tokenId}.{secret}"`. In Redis, only `SHA-256(secret)` is stored at key `refresh:{userId}:{tokenId}` with 7-day TTL.

**Rotation on refresh:**
1. Parse the presented token into `userId`, `tokenId`, `secret`.
2. Fetch `refresh:{userId}:{tokenId}` from Redis.
3. Verify `SHA-256(presentedSecret) == storedHash`.
4. Delete the key (revoke old token).
5. Issue a new token (new `tokenId` + `secret`, store new hash).
6. Return new token to client.

If validation fails (missing key, wrong hash), return empty — possible token replay attack.

---

**7. How does `RateLimitRepository` implement login rate limiting using Redis?**

```
tryConsume(key, maxAttempts, window):
  count = INCR "rate:{key}"
  if count == 1:
    EXPIRE "rate:{key}" <window_seconds>
  return count <= maxAttempts
```

On the first call within the window, `INCR` returns 1 and TTL is set. Subsequent calls within the window increment the counter. If `count > maxAttempts`, `tryConsume` returns `false` and the service throws `RateLimitExceededException` (HTTP 429). After the window expires, the key is auto-deleted and the counter resets.

Login: 5 attempts per 15 minutes per `userId`. On successful login, `rateLimitRepository.reset(loginRateLimitKey)` deletes the key.

---

**8. What would happen if Redis goes down?**

The user-service would fail at:
- **Login** — `RateLimitRepository` tries to call Redis; Spring's `RedisConnectionFailureException` propagates → 500.
- **Logout** — blacklisting fails → 500.
- **Refresh** — token validation fails → 500.
- **Register** — rate limit check fails → 500.

Flyway migrations and DB operations would still work (MySQL is independent). The service would be effectively non-functional for all auth operations. In production, Redis Sentinel or Cluster should be used for high availability. Alternatively, circuit breakers (Resilience4j) could be added to fall back gracefully.

---

**9. What is the difference between `SET key value EX seconds` and `SET key value PX milliseconds`?**

- `EX seconds` — TTL in whole seconds.
- `PX milliseconds` — TTL in milliseconds (more precise).

Spring's `StringRedisTemplate.opsForValue().set(key, value, duration)` uses `PX` internally when `duration` has sub-second precision. For the refresh token (7 days), `EX` is sufficient. For rate limiting windows (15 minutes), either works — `PX` allows finer-grained windows if needed.

---

**10. What is Redis eviction policy? Which policy is appropriate for a token blacklist?**

Redis eviction policy controls what happens when memory is full and new keys must be added:
- `noeviction` — reject writes when full.
- `allkeys-lru` — evict least recently used keys.
- `volatile-lru` — evict LRU keys with TTL set.
- `volatile-ttl` — evict keys with the shortest TTL first.

For the token blacklist, `volatile-lru` or `volatile-ttl` is appropriate since all blacklist keys have TTLs. `allkeys-lru` risks evicting valid refresh tokens. `noeviction` is safest for security-critical data — it prevents silent loss of blacklist entries.

---

### Advanced

**11. What is the difference between Redis standalone, Sentinel, and Cluster modes?**

- **Standalone** — single instance. Used in this project locally via Docker (`ecommerce-redis`). Single point of failure.
- **Sentinel** — master + replicas with automatic failover. Sentinel processes monitor the master; on failure, a replica is promoted. No data sharding. Suitable for this project in production (moderate data volume).
- **Cluster** — shards data across multiple masters (16384 hash slots). Automatic failover + horizontal scaling. Needed at very high scale (millions of sessions).

For this project's scale, **Sentinel** is the right production choice — provides HA without the operational complexity of Cluster.

---

**12. What is a Redis race condition? How could it affect the rate limiter?**

The `INCR` + `EXPIRE` pattern has a race condition: if two concurrent requests both call `INCR` and get `1` (first call), both set `EXPIRE`, which is harmless. But if `INCR` succeeds and the server crashes before `EXPIRE`, the key lives forever and the rate limiter never resets. Solution: use `SET key 0 EX <window> NX` (set only if not exists) to initialise with TTL atomically, then `INCR`. Or use a Lua script for atomicity.

---

**13. What is the difference between `INCR` + `EXPIRE` and a Lua script for atomic rate limiting?**

`INCR` and `EXPIRE` are two separate commands — between them, a crash or another Redis operation can occur (race condition as above). A Lua script executes atomically on the Redis server:

```lua
local count = redis.call('INCR', KEYS[1])
if count == 1 then
  redis.call('EXPIRE', KEYS[1], ARGV[1])
end
return count
```

Redis executes Lua scripts as a single atomic operation — no other commands can interleave. This eliminates the race condition. For production rate limiting, a Lua script or Redis `SET NX + INCR` pattern is preferred over separate commands.
