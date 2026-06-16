# OpenAPI & Swagger — Interview Questions & Answers

---

**1. What is OpenAPI 3.x?**

OpenAPI (formerly Swagger) is a standard specification for describing REST APIs in a machine-readable format (YAML/JSON). An OpenAPI document describes:
- Available endpoints and HTTP methods.
- Request/response schemas.
- Authentication requirements.
- Error responses.

It enables: auto-generated client SDKs, interactive documentation (Swagger UI), API contract testing, and mocking. This project maintains `docs/api-specs/user-service-api.yaml` as the source-of-truth API spec.

---

**2. What is Swagger UI?**

Swagger UI is a browser-based interactive API explorer generated from an OpenAPI document. At `http://localhost:8081/swagger-ui.html` you can:
- Browse all endpoints with their request/response schemas.
- Click **Authorize** to enter a Bearer token.
- Execute live API calls directly from the browser.
- Inspect responses including headers and status codes.

`springdoc-openapi-starter-webmvc-ui` auto-generates the OpenAPI document from Spring MVC annotations (`@RestController`, `@RequestMapping`, etc.) and serves it at `/v3/api-docs`.

---

**3. What does the `@SecurityScheme` annotation in `OpenApiConfig` do?**

```java
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
```

This registers a security scheme named `bearerAuth` in the generated OpenAPI document. It instructs Swagger UI to show a text input for `Bearer <token>` when the user clicks **Authorize**. The entered token is sent as `Authorization: Bearer <token>` on subsequent API calls made from Swagger UI.

---

**4. What is `@SecurityRequirement(name = "bearerAuth")` on `AdminUserController`?**

It marks all endpoints in `AdminUserController` as requiring the `bearerAuth` security scheme. In Swagger UI, these endpoints show a lock icon and require the token to be entered via **Authorize** before they can be called. Endpoints without `@SecurityRequirement` (like `AuthController`'s public endpoints) show no lock icon and don't require a token in Swagger UI.

---

**5. What is the JWKS endpoint and how would a downstream service use it?**

`GET /v1/auth/.well-known/jwks.json` returns the RSA public key in JWKS format:
```json
{
  "keys": [{
    "kty": "RSA",
    "n": "<modulus>",
    "e": "AQAB",
    "use": "sig",
    "alg": "RS256"
  }]
}
```

A downstream service (e.g., product-catalog) configures:
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: http://user-service:8081/v1/auth/.well-known/jwks.json
```

Spring Security fetches the public key from this URL at startup (and caches it), then uses it to verify every incoming JWT signature without any shared secret.

---

**6. What is the difference between `springdoc-openapi-starter-webmvc-ui` and the older `springfox`?**

| | springfox | springdoc-openapi |
|---|---|---|
| OpenAPI version | Swagger 2.x | OpenAPI 3.x |
| Spring Boot 3 support | No (abandoned) | Yes (actively maintained) |
| Auto-config | Manual config needed | Auto-configures with Spring Boot |
| Actuator integration | Limited | Built-in |

springfox is effectively abandoned (last release 2020) and incompatible with Spring Boot 3.x. springdoc is the current standard. This project uses `springdoc-openapi-starter-webmvc-ui` v2.6.0.

---

**7. Why are `/swagger-ui/**`, `/swagger-ui.html`, and `/v3/api-docs/**` added to `SecurityConfig.permitAll()`?**

Spring Security intercepts all requests by default. Without permitting these paths, requests to Swagger UI resources would be blocked with 401 (unauthenticated). These paths must be public because:
- Swagger UI loads static assets from `/swagger-ui/` (JS, CSS, HTML).
- The OpenAPI JSON is served at `/v3/api-docs`.
- These are documentation endpoints, not API endpoints — no authentication needed to view them.
- Actual API calls made from Swagger UI still require authentication (enforced per endpoint by the security rules).
