# Spring Boot & Core Java — Interview Questions & Answers

---

### Basic

**1. What is Spring Boot auto-configuration? How does it differ from Spring Framework?**

Spring Framework requires you to manually declare every bean and configuration. Spring Boot adds auto-configuration: it scans the classpath for known libraries and conditionally wires beans for you using `@Conditional` annotations. For example, because `spring-boot-starter-data-jpa` is on the classpath, Spring Boot auto-configures a `DataSource`, `EntityManagerFactory`, and transaction manager without any XML or `@Bean` declarations. You can always override any auto-configured bean by declaring your own.

---

**2. What is the difference between `@Component`, `@Service`, `@Repository`, and `@RestController`?**

All four are stereotypes — they all register the class as a Spring bean. The difference is semantic and functional:
- `@Component` — generic; no special behaviour.
- `@Service` — marks business logic; no extra behaviour but communicates intent.
- `@Repository` — marks a DAO; Spring adds persistence exception translation (wraps JDBC/JPA exceptions into `DataAccessException`).
- `@RestController` — combines `@Controller` + `@ResponseBody`; every method returns data serialised to JSON/XML, not a view name.

In this project: `AuthService` is `@Service`, `UserRepository` is a JPA interface (inherits `@Repository` behaviour), and `AuthController` is `@RestController`.

---

**3. What does `@SpringBootApplication` do internally?**

It is a meta-annotation combining three annotations:
- `@SpringBootConfiguration` — marks the class as a configuration class (equivalent to `@Configuration`).
- `@EnableAutoConfiguration` — triggers Spring Boot's auto-configuration mechanism.
- `@ComponentScan` — scans the package and sub-packages for beans.

In this project, `UserServiceApplication` carries `@SpringBootApplication` and is in `com.ecommerce.userauth`, so all classes under that package are picked up automatically.

---

**4. What is dependency injection? What types does Spring support?**

Dependency injection (DI) is the pattern where an object receives its dependencies from the outside rather than creating them itself. Spring supports:
- **Constructor injection** — recommended; used throughout this project (e.g., `AuthService` receives all 7 dependencies via constructor). Makes dependencies explicit and enables immutability.
- **Field injection** — `@Autowired` on a field; discouraged because it hides dependencies and makes testing harder.
- **Setter injection** — `@Autowired` on a setter; useful for optional dependencies.

---

**5. What is the difference between `@Bean` and `@Component`?**

- `@Component` (and its stereotypes) tells Spring to auto-detect and register the class itself as a bean via classpath scanning.
- `@Bean` is declared inside a `@Configuration` class on a method; the return value of that method becomes the bean. Use `@Bean` when you need to configure a third-party class you can't annotate (e.g., `KeyPair` in `JwtKeyConfig`, `SecurityFilterChain` in `SecurityConfig`).

---

**6. What is `application.yml`? How do you inject values from it into a Spring Bean?**

`application.yml` is the primary configuration file, loaded by Spring Boot at startup. Values are injected using:
- `@Value("${property.key}")` — injects a single value directly into a field or constructor parameter.
- `@ConfigurationProperties(prefix = "...")` — binds a whole section to a typed POJO.

In `JwtKeyConfig`: `@Value("${jwt.private-key:}")` injects the RSA private key string (the `:` after the key means default to empty string if not set).

---

**7. What does `${REDIS_HOST:localhost}` mean in `application.yml`?**

It is a Spring property placeholder with a default value. It resolves as:
1. First, look for an environment variable or system property named `REDIS_HOST`.
2. If not found, use `localhost`.

This pattern lets the same `application.yml` work locally (defaults to `localhost`) and in Docker/Kubernetes (where the env var is injected via `ConfigMap`).

---

**8. What is Spring Actuator? What endpoints does it expose?**

Spring Actuator adds production-ready endpoints to monitor and manage the application. This project exposes:
- `/actuator/health` — overall health (DB, Redis, Kafka).
- `/actuator/info` — application metadata.
- `/actuator/prometheus` — Micrometer metrics in Prometheus format.

Additionally, `management.endpoint.health.probes.enabled=true` enables:
- `/actuator/health/liveness` — used by Kubernetes liveness probe.
- `/actuator/health/readiness` — used by Kubernetes readiness probe.

---

**9. What is `@ControllerAdvice` / `@RestControllerAdvice`? How is it used in this project?**

`@RestControllerAdvice` is a global exception handler — it intercepts exceptions thrown from any `@RestController` and maps them to HTTP responses. In `GlobalExceptionHandler`, domain exceptions are caught and converted to structured `ErrorResponse` objects with appropriate HTTP status codes (e.g., `InvalidCredentialsException` → 401, `EmailAlreadyRegisteredException` → 409, `RateLimitExceededException` → 429).

---

**10. What is the role of `@Valid` on a `@RequestBody` parameter?**

`@Valid` triggers Bean Validation (JSR-380) on the request body. Annotations like `@NotBlank`, `@Email`, `@Size` on the DTO fields are enforced. If validation fails, Spring throws `MethodArgumentNotValidException`, which `GlobalExceptionHandler` catches and returns a `ValidationErrorResponse` with field-level error details.

---

### Intermediate

**11. How does Spring Boot's embedded Tomcat work?**

When `spring-boot-starter-web` is on the classpath, Spring Boot auto-configures an embedded Tomcat server. At startup, `TomcatWebServer` is created and started programmatically — no WAR deployment or external Tomcat is needed. The app runs as a self-contained JAR (`java -jar app.jar`). Port is configured via `server.port: 8081` in `application.yml`.

---

**12. What is the difference between `@RequestMapping`, `@GetMapping`, and `@PostMapping`?**

- `@RequestMapping` is the generic annotation; it accepts a `method` parameter to specify HTTP verbs. It can be placed at class level to set a base path.
- `@GetMapping` / `@PostMapping` are shortcuts for `@RequestMapping(method = GET/POST)`.

In this project, `@RequestMapping("/v1/auth")` is on `AuthController` (class-level base path), and individual methods use `@PostMapping("/register")`, `@PostMapping("/login")`, etc.

---

**13. How is `CorrelationIdFilter` applied without registering it manually?**

`CorrelationIdFilter` extends `OncePerRequestFilter` and is annotated `@Component`. Spring Boot auto-detects it and registers it in the servlet filter chain automatically. `OncePerRequestFilter` guarantees it executes exactly once per request even in forward/include scenarios. It puts the correlation ID in SLF4J's `MDC` so it appears in every log line for that request.

---

**14. Explain `@Scheduled(fixedDelayString = ...)`. What is the difference between `fixedDelay` and `fixedRate`?**

`@Scheduled` marks a method to run on a schedule. `fixedDelayString` reads the delay from a property (`${outbox.relay.poll-interval-ms:500}`).
- `fixedDelay` — waits N ms **after the previous execution completes** before running again. Used in `OutboxRelay` — if publishing takes 300ms, the next poll starts 500ms after that.
- `fixedRate` — runs every N ms **from the start of the previous execution**, regardless of how long it takes. Can cause overlap if the task is slow.

`fixedDelay` is correct for `OutboxRelay` because overlapping polls would cause duplicate Kafka publishes.

---

**15. What is `@Transactional`? What happens if an exception is thrown inside a `@Transactional` method?**

`@Transactional` wraps the method in a database transaction. If the method completes normally, the transaction commits. If an **unchecked exception** (`RuntimeException` or `Error`) is thrown, the transaction rolls back. Checked exceptions do NOT trigger rollback by default (configurable via `rollbackFor`).

In `AuthService.register()`, the `User` save, `EmailVerification` save, and `OutboxEvent` save all happen in one transaction — either all commit or all roll back.

---

**16. What is the difference between checked and unchecked exceptions in the context of `@Transactional` rollback?**

By default, `@Transactional` only rolls back on unchecked exceptions (`RuntimeException` subclasses). All domain exceptions in this project (`InvalidCredentialsException`, `EmailAlreadyRegisteredException`, etc.) extend `RuntimeException`, so they trigger automatic rollback. Checked exceptions (e.g., `IOException`) would NOT roll back by default and would need `@Transactional(rollbackFor = IOException.class)`.

---

**17. What is `@Version` (optimistic locking) on `User.version`? How does it work?**

`@Version` tells Hibernate to use optimistic locking. When updating a `User` row, Hibernate adds `WHERE version = <current>` to the UPDATE statement and increments the version. If two concurrent transactions both read version 5 and both try to save, the second one finds `0 rows updated` (because the first already bumped it to 6) and throws `OptimisticLockException`. This prevents lost updates without holding a DB lock.

---

**18. What is a `record` in Java? How are DTOs like `LoginRequest` implemented using records?**

A Java `record` (Java 16+) is a concise, immutable data carrier. The compiler generates: constructor, getters (`email()`, `password()`), `equals()`, `hashCode()`, and `toString()`. In `LoginRequest`:
```java
public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
```
This replaces a full class with private fields, getters, and boilerplate. Records are ideal for DTOs that are never mutated after creation.

---

**19. What is the difference between `Instant`, `LocalDateTime`, and `ZonedDateTime`? Why does this project use `Instant`?**

- `Instant` — a point on the UTC timeline; no timezone concept. Always UTC.
- `LocalDateTime` — a date and time without timezone; ambiguous across timezones.
- `ZonedDateTime` — a date/time with a timezone attached.

This project uses `Instant` for all timestamps (`created_at`, `deactivated_at`, etc.) because microservices may run in different timezones/regions, and UTC timestamps avoid ambiguity. MySQL stores them as `DATETIME(3)` and Hibernate converts automatically.

---

**20. What does `List.copyOf(addresses)` do in the `User` aggregate, and why is it used?**

`List.copyOf()` returns an unmodifiable copy of the list. The `User.getAddresses()` method returns this copy so callers cannot directly mutate the internal `addresses` list. All mutations must go through `User.addAddress()`, `User.removeAddress()`, and `User.setDefaultAddress()`, which enforce domain invariants. This is an aggregate boundary enforcement pattern.

---

### Advanced

**21. How does Spring Boot resolve property placeholders at startup?**

Spring Boot builds an `Environment` from multiple `PropertySource`s in priority order: OS environment variables → JVM system properties → `application.yml` → `application.properties` → defaults. `${REDIS_HOST:localhost}` first checks all higher-priority sources before falling back to `localhost`. Kubernetes injects env vars via `ConfigMap`/`Secret`, which override the `application.yml` defaults.

---

**22. What is a `BeanDefinitionRegistryPostProcessor`? How does Spring Boot's auto-configuration use `@Conditional` annotations?**

`BeanDefinitionRegistryPostProcessor` runs before the `ApplicationContext` is refreshed and can modify the bean registry. Spring Boot's `AutoConfigurationImportSelector` reads `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` and conditionally imports configuration classes using annotations like:
- `@ConditionalOnClass` — bean only created if a class is on the classpath.
- `@ConditionalOnMissingBean` — bean only created if no user-defined bean of that type exists.
- `@ConditionalOnProperty` — bean only created if a property is set.

This is why adding `springdoc-openapi-starter-webmvc-ui` to the classpath is enough to get Swagger UI — its auto-configuration fires conditionally.

---

**23. Explain the Spring Security filter chain. Where does `JwtBlacklistFilter` sit relative to `BearerTokenAuthenticationFilter`, and why does order matter?**

The Spring Security filter chain is an ordered list of `Filter`s applied to every request. `BearerTokenAuthenticationFilter` extracts the JWT from the `Authorization` header and authenticates the request. `JwtBlacklistFilter` is added **after** it using `.addFilterAfter(new JwtBlacklistFilter(blacklist), BearerTokenAuthenticationFilter.class)`. This means: (1) the JWT is first validated cryptographically by `BearerTokenAuthenticationFilter`, (2) then `JwtBlacklistFilter` checks if the `jti` has been blacklisted (logged out). If order were reversed, blacklist checks would run on unauthenticated requests, and the `jti` wouldn't be available yet.

---

**24. What is `SessionCreationPolicy.STATELESS`? What are the implications for CSRF protection?**

`STATELESS` tells Spring Security never to create or use an `HttpSession`. Every request must be authenticated independently via the `Authorization` header. Because there is no session cookie, CSRF attacks (which exploit session cookies) are impossible — so `csrf.disable()` is safe for a pure JWT REST API. If sessions were used, CSRF protection would be mandatory.

---

**25. How does `@EnableWebSecurity` affect the default security configuration?**

`@EnableWebSecurity` activates Spring Security's web security support and disables Spring Boot's auto-configured `SecurityFilterChain`. This gives full control over the security configuration. Without it (in a non-web context), Spring Security would still be active but without web-specific features. In this project it is combined with `@Configuration` on `SecurityConfig` to define the custom filter chain, JWT decoder, and authority converter.
