# Testing — Interview Questions & Answers

---

### Basic

**1. What is the difference between a unit test and an integration test?**

- **Unit test** — tests a single class in isolation. Dependencies are mocked. Fast (milliseconds). No external systems. Example: `UserTest`, `AuthServiceTest`, `JwtServiceTest`.
- **Integration test** — tests multiple components working together, often with real infrastructure (DB, Redis, Kafka). Slower (seconds). Catches issues that mocking hides. Example: `AuthControllerIntegrationTest`, `OutboxRelayIntegrationTest`.

This project has both: unit tests for domain logic and services, integration tests (Testcontainers) for persistence, Redis, and Kafka interactions.

---

**2. What is Mockito? How is it used in `AuthServiceTest`?**

Mockito is a mocking framework for Java. It creates fake implementations of dependencies and allows you to define their behaviour. In `AuthServiceTest`:
```java
@Mock UserRepository userRepository;
@Mock RateLimitRepository rateLimitRepository;
// ...
when(userRepository.existsByEmail(email)).thenReturn(false);
when(rateLimitRepository.tryConsume(...)).thenReturn(true);
```
The `AuthService` is instantiated with mocked dependencies, allowing testing of business logic without a real database or Redis.

---

**3. What is `@SpringBootTest`?**

`@SpringBootTest` loads the full Spring `ApplicationContext` for integration tests — auto-configuration, beans, security config, and all. It starts the embedded web server (optionally) and creates a test application context nearly identical to production. It's used in `UserServiceApplicationIntegrationTest` and `AuthControllerIntegrationTest` to test the full request-to-response cycle.

---

### Intermediate

**4. What is Testcontainers? Why is it used instead of an H2 in-memory database?**

Testcontainers is a Java library that starts real Docker containers during tests. This project uses:
- `mysql:8.0` container for DB integration tests.
- `confluentinc/cp-kafka` for Kafka integration tests.
- `redis:7` for Redis repository tests.

Why not H2:
- H2 has different SQL dialect (no `DATETIME(3)`, limited `CHECK` constraint support, different UUID handling).
- Tests would pass on H2 but fail on real MySQL — false confidence.
- Testcontainers tests against the exact same MySQL 8.0.39 version used in production.

---

**5. What does `AbstractIntegrationTest` provide?**

`AbstractIntegrationTest` is the base class for all integration tests. It provides:
- `@Testcontainers` — manages container lifecycle.
- Static `@Container` declarations for MySQL, Redis, Kafka — containers start once per test suite and are shared (using `@TestcontainersExtension` reuse).
- `@DynamicPropertySource` — injects container ports into Spring properties at test time:
  ```java
  registry.add("spring.datasource.url", mysql::getJdbcUrl);
  registry.add("spring.data.redis.host", redis::getHost);
  ```
- This means no hardcoded `localhost:3306` in tests — Testcontainers assigns random ports.

---

**6. What is `@Transactional` on a test class? Does it behave the same as in production?**

`@Transactional` on a test class wraps each test method in a transaction that is **rolled back after the test** (not committed). This means:
- Tests leave no state in the DB — no cleanup code needed.
- Each test starts with a clean slate.
- Behaviour difference: in production, `@Transactional` commits; in tests, it always rolls back.

Exception: tests that explicitly test rollback behaviour or test asynchronous code (like `OutboxRelayIntegrationTest`) must use `@Commit` or manage transactions manually.

---

**7. What does `FlywayMigrationIntegrationTest` verify?**

It verifies that `V1__init.sql` applies cleanly to a real MySQL container and that Hibernate's schema validation (`ddl-auto: validate`) passes against the migrated schema. This catches:
- SQL syntax errors in the migration file.
- Type mismatches between the migration and entity mappings (exactly the `correlation_id VARCHAR vs CHAR` bug that was fixed).
- Missing or incorrect constraints.

It is the first test to run in CI and acts as a migration smoke test.

---

**8. Why are `OutboxRelayIntegrationTest` tests more valuable than mocking `KafkaTemplate`?**

Mocking `KafkaTemplate` only proves the relay calls the mock — it doesn't test:
- That the Kafka producer config (`acks: all`, `key-serializer`) is correct.
- That the Kafka message actually lands in the correct topic with the correct key.
- That the `published = true` DB update commits correctly after successful publish.
- That error handling works when Kafka is temporarily unavailable.

The integration test starts a real Kafka container, publishes events, and verifies the consumer receives them — testing the full pipeline end-to-end.

---

**9. What is `spring-security-test`? How would you test an admin endpoint?**

`spring-security-test` provides test utilities for Spring Security:
- `@WithMockUser(roles = "ADMIN")` — populates the `SecurityContext` with a mock authenticated admin user.
- `SecurityMockMvcRequestPostProcessors.jwt()` — creates a mock JWT for the request.

For `AdminUserController`:
```java
mockMvc.perform(get("/v1/admin/users")
    .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
    .andExpect(status().isOk());
```

Without `spring-security-test`, all protected endpoints return 401/403 in tests, making them untestable with `MockMvc`.

---

**10. What is the difference between `@Mock` and `@MockBean`?**

- `@Mock` (Mockito) — creates a plain Mockito mock. Used with `@ExtendWith(MockitoExtension.class)`. Does not interact with the Spring context. Fast. Used in unit tests (`AuthServiceTest`).
- `@MockBean` (Spring Boot Test) — creates a Mockito mock **and registers it as a Spring bean**, replacing the real bean in the `ApplicationContext`. Used in integration tests where you want a mostly-real context but need to stub one dependency (e.g., stub the email sender in `AuthControllerIntegrationTest`). Slower because it reloads the context.
