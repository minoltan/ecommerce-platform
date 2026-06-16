# Interview Questions — User/Auth Service

Based on the actual implementation in `phase1/user-service`. Covers everything built: Spring Boot, DDD, JWT, Redis, Kafka, MySQL, Docker, Kubernetes, and microservices patterns.

---

## 1. Spring Boot & Core Java

### Basic
1. What is Spring Boot auto-configuration? How does it differ from Spring Framework?
2. What is the difference between `@Component`, `@Service`, `@Repository`, and `@RestController`?
3. What does `@SpringBootApplication` do internally?
4. What is dependency injection? What types does Spring support?
5. What is the difference between `@Bean` and `@Component`?
6. What is `application.yml`? How do you inject values from it into a Spring Bean?
7. What does `${REDIS_HOST:localhost}` mean in `application.yml`?
8. What is Spring Actuator? What endpoints does it expose?
9. What is `@ControllerAdvice` / `@RestControllerAdvice`? How is it used in this project?
10. What is the role of `@Valid` on a `@RequestBody` parameter?

### Intermediate
11. How does Spring Boot's embedded Tomcat work?
12. What is the difference between `@RequestMapping`, `@GetMapping`, and `@PostMapping`?
13. What is a `Filter` in Spring? How is `CorrelationIdFilter` applied in this project without registering it as a `@Bean`?
14. Explain `@Scheduled(fixedDelayString = ...)`. What is the difference between `fixedDelay` and `fixedRate`?
15. What is `@Transactional`? What happens if an exception is thrown inside a `@Transactional` method?
16. What is the difference between checked and unchecked exceptions in the context of `@Transactional` rollback?
17. How does `@Version` (optimistic locking) work in JPA? Where is it used in this project and why?
18. What is a `record` in Java? How are DTOs like `LoginRequest` implemented using records?
19. What is the difference between `Instant`, `LocalDateTime`, and `ZonedDateTime`? Why does this project use `Instant`?
20. What does `List.copyOf(addresses)` do in the `User` aggregate, and why is it used?

### Advanced
21. How does Spring Boot resolve property placeholders at startup? What is the `Environment` abstraction?
22. What is a `BeanDefinitionRegistryPostProcessor`? How does Spring Boot's auto-configuration use `@Conditional` annotations?
23. Explain the Spring Security filter chain. Where does `JwtBlacklistFilter` sit relative to `BearerTokenAuthenticationFilter`, and why does order matter?
24. What is `SessionCreationPolicy.STATELESS`? What are the implications for CSRF protection?
25. How does `@EnableWebSecurity` affect the default security configuration?

---

## 2. Domain-Driven Design (DDD)

### Basic
1. What is Domain-Driven Design (DDD)?
2. What is a bounded context? Name the seven bounded contexts in this project.
3. What is an aggregate? What is an aggregate root?
4. What is the difference between an entity and a value object?
5. What is a domain event?

### Intermediate
6. In this project, `User` is an aggregate root. What rules does it enforce that a plain data class would not?
7. `Email` and `PasswordHash` are value objects. What makes them value objects rather than entities?
8. Why is `User`'s constructor `private`, with a static factory method `User.register(...)`? What does this pattern enforce?
9. What is an `AttributeConverter` (`EmailConverter`, `PasswordHashConverter`)? Why is this pattern used instead of storing raw strings in the entity?
10. Why does `User.verifyEmail()` check the current status before transitioning? What pattern is this?
11. `User.deactivate()` is described as a "terminal transition" — what does that mean, and how is it enforced in the code?
12. Why does `AuthService.register(...)` save the `EmailVerification` row and the outbox event instead of `User` doing it directly?
13. What is the difference between a domain service and an application service? Where do `AuthService` and `AdminUserService` fit?
14. What is the Ubiquitous Language principle? Give examples of where it is applied in the naming of classes in this project.
15. What does `@Version` on `User.version` prevent in a concurrent system?

### Advanced
16. Why does cross-context communication happen via domain events (Kafka) rather than direct DB queries or REST calls?
17. What is an anti-corruption layer (ACL)? When would you add one between bounded contexts in this project?
18. What is the difference between choreography-based and orchestration-based sagas? Which approach does this project use and why?
19. If you needed to add a `PasswordResetService`, where would it live and what aggregate would it operate on?
20. What consistency guarantees does the User aggregate give you, and where does it break down in a distributed system?

---

## 3. JWT & Security

### Basic
1. What is a JWT? What are its three parts?
2. What is the difference between authentication and authorisation?
3. What is the difference between symmetric (HS256) and asymmetric (RS256) JWT signing?
4. Why does this project use RS256 instead of HS256?
5. What is an access token vs a refresh token?
6. What is the `jti` claim in a JWT? How is it used here?
7. What does `bearerFormat: JWT` mean in OpenAPI?

### Intermediate
8. Why does the user-service expose a `/v1/auth/.well-known/jwks.json` endpoint? Who consumes it?
9. How does `NimbusJwtDecoder` validate a JWT? What does it check?
10. What is token blacklisting? Why is it needed when JWTs are stateless?
11. How does `JwtBlacklistFilter` prevent use of a logged-out access token?
12. What is refresh token rotation? What security property does it provide?
13. What is the `role` claim in the JWT used for in `SecurityConfig`? How is `hasRole("ADMIN")` evaluated?
14. Why is the JWT private key generated ephemerally in dev but must be stable in Kubernetes multi-replica deployments?
15. What is PKCS#8 format for a private key? Why does the application need it in that specific format?
16. What is the difference between `permitAll()` and `anonymous()` in Spring Security?
17. What is rate limiting? How is it implemented using Redis in `RateLimitRepository`?

### Advanced
18. If two replicas of the user-service each generated their own RSA key pair, what would happen when a token signed by replica A is sent to replica B?
19. How would you implement key rotation (replacing the RSA key pair) without causing downtime or invalidating all existing tokens?
20. What is the OAuth2 Resource Server pattern? How is the user-service acting as one in this project?
21. What is the security implication of storing the refresh token as a plain UUID vs a hashed value in Redis?

---

## 4. MySQL, JPA & Flyway

### Basic
1. What is Flyway? What problem does it solve?
2. What is the naming convention for Flyway migration files? What does `V1__init.sql` mean?
3. What is `ddl-auto: validate` in JPA? How is it different from `update` or `create`?
4. What is the difference between `CHAR` and `VARCHAR` in MySQL?
5. What is a foreign key constraint? Give an example from `V1__init.sql`.
6. What is an index? Why does `user_auth_outbox` have an index on `(published, created_at)`?

### Intermediate
7. Why does this project store UUIDs as `CHAR(36)` instead of `BINARY(16)`?
8. What is `preferred_uuid_jdbc_type: CHAR` in Hibernate configuration? Why was it needed?
9. What is the HikariCP connection pool? What is `HikariPool-1`?
10. What is optimistic locking vs pessimistic locking? How is the `version` column in `users` used?
11. What is `open-in-view: false`? Why is it recommended to disable it?
12. What does `insertable = false, updatable = false` mean on the `created_at` column mapping?
13. What is a soft delete? How is `deleted_at` used in the `users` table?
14. What does `DATETIME(3)` mean in MySQL? Why use fractional seconds?
15. What is `utf8mb4` charset in MySQL? Why is it preferred over `utf8`?
16. What is `ADR-0008 database-per-service`? What are the trade-offs?

### Advanced
17. What is the N+1 query problem? How can it occur with `@OneToMany` on `User.addresses`?
18. If you needed to query users across multiple bounded contexts, how would you handle it without cross-schema joins?
19. What happens if a Flyway migration fails halfway through? How would you recover?
20. What is the checksum validation Flyway performs? What happens if you modify `V1__init.sql` after it has been applied?

---

## 5. Redis

### Basic
1. What is Redis? What data structures does it support?
2. What does TTL (Time-To-Live) mean in Redis?
3. What is the difference between Redis as a cache and Redis as a primary store?

### Intermediate
4. How is Redis used in this project? Name all three use cases.
5. How does the `TokenBlacklistRepository` use Redis to blacklist a JWT `jti`?
6. How does `RefreshTokenRepository` store and rotate refresh tokens?
7. How does `RateLimitRepository` implement login rate limiting using Redis?
8. What would happen if Redis goes down? Which parts of the user-service would fail?
9. What is the difference between `SET key value EX seconds` and `SET key value PX milliseconds`?
10. What is Redis eviction policy? Which policy is appropriate for a token blacklist use case?

### Advanced
11. What is the difference between Redis standalone, Sentinel, and Cluster modes? Which would you use in production for this project?
12. What is a Redis race condition? How could it affect the rate limiter if not handled carefully?
13. What is the difference between `INCR` + `EXPIRE` and a Lua script for atomic rate limiting in Redis?

---

## 6. Apache Kafka & Outbox Pattern

### Basic
1. What is Apache Kafka? What problem does it solve?
2. What is a topic, partition, and consumer group in Kafka?
3. What is the difference between at-least-once, at-most-once, and exactly-once delivery?
4. What is a domain event? Give an example from this project.

### Intermediate
5. What is the Transactional Outbox pattern? Why is it used in this project?
6. Walk through the lifecycle of a `UserRegistered` event from the moment `AuthService.register()` is called to when it lands in Kafka.
7. Why is the `OutboxRelay` annotated with `@Scheduled` rather than publishing directly in `AuthService`?
8. What is the `aggregateId` used as the Kafka message key, and why? (Reference ADR-0002)
9. What does `kafkaTemplate.send(...).get(5, TimeUnit.SECONDS)` do? What are the implications of a blocking `.get()`?
10. What happens if the Kafka send fails in `OutboxRelay`? Will the event be lost?
11. What is `acks: all` in the Kafka producer config? What durability guarantee does it provide?
12. What is KRaft mode in Kafka? How does it differ from ZooKeeper mode?
13. What is `auto.create.topics.enable`? Why is it set to `true` in the local dev compose but might be `false` in production?

### Advanced
14. What is the dual-write problem that the outbox pattern solves?
15. If `OutboxRelay` polls every 500ms and publishes 100 events per poll, what is the maximum throughput? What would you change to increase it?
16. What is consumer idempotency? Why must downstream consumers of `UserRegistered` be idempotent?
17. What is log compaction in Kafka? Would it be appropriate for the `user-auth.user-registered` topic?
18. How would you implement exactly-once semantics end-to-end (producer → Kafka → consumer)?
19. What is ADR-0002's decision on partition key for `user-auth.*` topics and why?

---

## 7. Docker & Kubernetes

### Basic
1. What is Docker? What is a container vs a virtual machine?
2. What is a `Dockerfile`? What do `FROM`, `COPY`, `RUN`, and `ENTRYPOINT` do?
3. What is a multi-stage Docker build? What is the benefit in this project's `Dockerfile`?
4. What is Docker Compose? What does `docker-compose.infra.yml` start?
5. What is a Docker volume? Why does `docker-compose.infra.yml` use `mysql-data`?
6. What is the difference between `docker compose down` and `docker compose down -v`?

### Intermediate
7. What is Kubernetes? What problems does it solve over plain Docker?
8. What is a `Deployment` in Kubernetes? What does `replicas: 2` mean?
9. What is a `Service` in Kubernetes? What is `ClusterIP`?
10. What is a `ConfigMap` vs a `Secret`? How are they used in the user-service manifests?
11. What is Kustomize? How does `overlays/local` differ from `base`?
12. What is a liveness probe vs a readiness probe? What endpoints are used in this project?
13. What is `host.docker.internal`? Why is it used in the `overlays/local` ConfigMap?
14. What is `podAntiAffinity`? Why is it configured in the base deployment?
15. What is a HorizontalPodAutoscaler (HPA)? Why is it deleted in the local overlay?
16. What is a PodDisruptionBudget (PDB)?

### Advanced
17. Why must the JWT RSA key pair be set as a Kubernetes Secret rather than auto-generated per pod?
18. What is the difference between `envFrom` + `configMapRef` and mounting a ConfigMap as a volume?
19. What would happen if you applied `base/secret.yaml` with blank JWT keys to a 2-replica deployment?
20. How would you handle secret rotation (new DB password) in a running Kubernetes deployment with zero downtime?
21. What is `imagePullPolicy: Always` vs `IfNotPresent`? Which matters when using `user-service:latest`?

---

## 8. Microservices Architecture

### Basic
1. What is a microservice? How does it differ from a monolith?
2. What is the single responsibility principle at the service level?
3. What is synchronous vs asynchronous communication between services?

### Intermediate
4. What is ADR-0006 (microservices vs monolith)? What trade-offs did it document?
5. Why does each service have its own database schema (`ADR-0008`)? What problem does it prevent?
6. What is a saga pattern? What two types exist and which does this project use?
7. What is the CAP theorem? How does it apply to the user-service?
8. What is eventual consistency? How does the outbox pattern achieve it?
9. What is a correlation ID? How does `CorrelationIdFilter` propagate it?
10. What is an API Gateway? What role does it play in this architecture?
11. What is service discovery? How would downstream services find the user-service in Kubernetes?
12. What is circuit breaking (Resilience4j)? Why is it in the parent POM even though user-service doesn't use it?

### Advanced
13. How would you handle a distributed transaction spanning user-service (ACTIVE user) and order-service (OrderPlaced)?
14. What is the strangler fig pattern? How would it apply if migrating from a monolith to this microservices architecture?
15. What is event sourcing? How does it differ from the outbox pattern used here?
16. What is the two-phase commit problem and why is it avoided in this architecture?
17. How would you implement distributed tracing across services using the `correlationId` header?
18. What is backward compatibility in API design? How would you version the user-service API if you needed a breaking change?

---

## 9. Testing

### Basic
1. What is the difference between a unit test and an integration test?
2. What is Mockito? How is it used in `AuthServiceTest`?
3. What is `@SpringBootTest`?

### Intermediate
4. What is Testcontainers? Why is it used instead of an H2 in-memory database?
5. What does `AbstractIntegrationTest` provide for all integration tests in this project?
6. What is `@Transactional` on a test class? Does it behave the same as in production code?
7. What does `FlywayMigrationIntegrationTest` verify?
8. Why are `OutboxRelayIntegrationTest` tests more valuable than mocking the Kafka template?
9. What is `spring-security-test`? How would you test an endpoint that requires `ROLE_ADMIN`?
10. What is the difference between `@Mock` and `@MockBean`?

---

## 10. OpenAPI & Swagger

### Basic
1. What is OpenAPI 3.x?
2. What is Swagger UI?
3. What does the `@SecurityScheme` annotation in `OpenApiConfig` do?

### Intermediate
4. What is `@SecurityRequirement(name = "bearerAuth")` on `AdminUserController`? What does it render in Swagger UI?
5. What is the JWKS endpoint (`/v1/auth/.well-known/jwks.json`) and how would a downstream service use it to configure its JWT decoder?
6. What is the difference between `springdoc-openapi-starter-webmvc-ui` and the older `springfox`?
7. Why are `/swagger-ui/**` and `/v3/api-docs/**` added to the security `permitAll()` list?

---

## Quick Reference — Key Decisions in This Project

| Decision | Choice | ADR |
|---|---|---|
| Microservices vs monolith | Microservices | ADR-0006 |
| Message broker | Kafka over RabbitMQ | ADR-0007 |
| Database strategy | Database-per-service | ADR-0008 |
| JWT algorithm | RS256 over HS256 | ADR-0011 |
| Saga type | Choreography (Kafka) | ADR-0014 |
| Outbox polling interval | 500 ms | LLD §11 |
| UUID storage | CHAR(36) | application.yml |
| Token blacklist | Redis with TTL = remaining access token TTL | SecurityConfig |
