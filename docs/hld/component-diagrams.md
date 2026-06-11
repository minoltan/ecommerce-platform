# Component Diagrams — High-Level Design

**Artefact type:** C4 Level 3 — Component Diagrams (all 7 services)  
**Phase:** ARCH  
**Status:** Draft  
**Version:** 0.1  
**Date:** 2026-06-08  
**Author:** System Architect  
**Inputs:** `docs/hld/container-diagram.md`, `docs/requirements/event-storming.md` v0.3

---

## 1. Scope

This document zooms into each microservice container from the Level 2 diagram and shows the internal Spring Boot component structure: controllers, services, repositories, domain aggregates, and infrastructure adapters (Kafka, Redis, HTTP clients).

**What is shown per service:**
- REST layer (Controllers)
- Business logic layer (Services, Domain Aggregates)
- Data access layer (Repositories — JPA and non-JPA)
- Infrastructure adapters (Kafka publisher/consumer, Redis, external HTTP clients)
- Key dependency arrows between components

**What is NOT shown:** Class fields, method signatures, or implementation detail — those belong in the LLD documents (`docs/lld/`).

---

## 2. Component Structure Convention

Every service follows the same layered package structure:

```
com.ecommerce.[context]/
├── api/              ← Controllers (REST handlers)
├── application/      ← Services (use cases, aggregate operations)
├── domain/           ← Aggregates, Entities, Value Objects, Domain Events
├── infrastructure/
│   ├── persistence/  ← JPA Repositories, Redis adapters
│   ├── messaging/    ← Kafka producers, Kafka consumers
│   └── client/       ← External HTTP clients (gateway, email, SMS)
└── config/           ← Spring configuration, beans
```

Arrows in diagrams flow: `Controller → Service → Domain / Repository / Adapter`

---

## 3. User/Auth Service

**Bounded context:** User/Auth  
**Aggregates:** `User`, `RefreshToken`  
**DB:** `user_db` (MySQL)  
**Cache:** Redis (refresh tokens, token blacklist, rate-limit counters)

```mermaid
graph TB
    subgraph external ["External"]
        GW["API Gateway"]
        Kafka["Kafka"]
        Redis["Redis"]
        DB[("user_db\nMySQL")]
        Email["Email Provider\n(SES/SendGrid)"]
    end

    subgraph ua ["User/Auth Service"]
        subgraph api ["api/"]
            AC["AuthController\nPOST /auth/register\nPOST /auth/login\nPOST /auth/refresh\nDELETE /auth/logout"]
            UC["UserController\nGET /users/me\nPUT /users/me\nPUT /users/password\nPOST /users/addresses"]
        end

        subgraph application ["application/"]
            AS["AuthService\nregister · login\nrefreshToken · logout\nrevokeAllSessions"]
            US["UserService\ngetProfile · updateProfile\nchangePassword · manageAddresses"]
        end

        subgraph domain ["domain/"]
            UA["User (Aggregate Root)\nid · email · passwordHash\nroles · status · emailVerified"]
            RT["RefreshToken\ntokenId · userId · expiresAt · rotatedAt"]
        end

        subgraph infra ["infrastructure/"]
            UR["UserRepository\nJPA — user_db"]
            RR["RefreshTokenRepository\nRedis adapter\nkey: refresh:{userId}:{tokenId}"]
            BL["TokenBlacklistRepository\nRedis adapter\nkey: blacklist:{jti}"]
            RL["RateLimitRepository\nRedis adapter\nkey: rate:{userId}:{endpoint}"]
            KP["KafkaEventPublisher\nUserRegistered\nUserLoggedIn\nUserDeactivated\nPasswordResetRequested"]
        end
    end

    GW -->|"REST"| AC & UC
    AC --> AS
    UC --> US
    AS --> UA & RT
    US --> UA
    AS --> UR & RR & BL & RL & KP
    US --> UR
    UR --> DB
    RR & BL & RL --> Redis
    KP -->|"user-auth.*"| Kafka
```

---

## 4. Product Catalog Service

**Bounded context:** Product Catalog  
**Aggregates:** `Product`, `Category`  
**DB:** `catalog_db` (MySQL)  
**External:** S3 (image storage). Search is MySQL full-text + Redis cache for Phase 1
(ADR-0013) — Elasticsearch is deferred to Phase 2 (see Migration Path in ADR-0013).

```mermaid
graph TB
    subgraph external ["External"]
        GW["API Gateway"]
        Kafka["Kafka"]
        DB[("catalog_db\nMySQL\n+ FULLTEXT(title, description)")]
        Redis[("Redis\nsearch:* cache, TTL 5min")]
        S3["S3 + CloudFront"]
    end

    subgraph pc ["Product Catalog Service"]
        subgraph api ["api/"]
            ProdC["ProductController\nGET/POST/PUT/DELETE /products\nPOST /products/{id}/publish\nPOST /products/{id}/unpublish"]
            CatC["CategoryController\nGET/POST/PUT/DELETE /categories"]
            SearchC["SearchController\nGET /products?q="]
        end

        subgraph application ["application/"]
            ProdS["ProductService\ncreate · update · publish\nunpublish · archive · updatePrice\naddVariant · removeVariant"]
            CatS["CategoryService\ncreate · update · delete · move"]
            SearchS["SearchService\nsearch · suggest"]
        end

        subgraph domain ["domain/"]
            Prod["Product (Aggregate Root)\nid · sku · title · slug\nprice(BIGINT paise) · status\nunpublishReason · variants\nimages · categoryId"]
            Cat["Category (Aggregate Root)\nid · name · parentId · slug"]
        end

        subgraph infra ["infrastructure/"]
            PR["ProductRepository\nJPA — catalog_db\nMATCH...AGAINST full-text search"]
            CR["CategoryRepository\nJPA — catalog_db"]
            SCA["SearchCacheAdapter\nRedis — search:{hash}\nTTL 5min, DEL search:* on invalidate"]
            S3A["S3ImageAdapter\nAWS S3 SDK\nupload · delete · getUrl"]
            KP["KafkaEventPublisher\nProductPublished\nProductPriceUpdated\nProductVariantAdded\nProductVariantRemoved\nProductUnpublished"]
            KC["KafkaEventConsumer\nProductOutOfStock ← Inventory\nStockReplenished ← Inventory"]
        end
    end

    GW -->|"REST"| ProdC & CatC & SearchC
    ProdC --> ProdS
    CatC --> CatS
    SearchC --> SearchS
    ProdS --> Prod & PR & SCA & S3A & KP
    CatS --> Cat & CR
    SearchS --> PR & SCA
    KC -->|"inventory.*"| ProdS
    PR --> DB
    CR --> DB
    SCA --> Redis
    S3A --> S3
    KP -->|"catalog.*"| Kafka
    KC --> Kafka
```

---

## 5. Cart Service

**Bounded context:** Cart  
**Aggregates:** `Cart`, `LineItem`  
**Storage:** Redis only (no MySQL — Cart is entirely cache-resident)

```mermaid
graph TB
    subgraph external ["External"]
        GW["API Gateway"]
        Kafka["Kafka"]
        Redis["Redis"]
    end

    subgraph cs ["Cart Service"]
        subgraph api ["api/"]
            CC["CartController\nGET /cart\nPOST /cart/items\nPUT /cart/items/{sku}\nDELETE /cart/items/{sku}\nPOST /cart/coupon\nDELETE /cart/coupon\nPOST /cart/checkout"]
        end

        subgraph application ["application/"]
            CartS["CartService\ngetCart · addItem · updateQty\nremoveItem · applyCoupon\nremoveCoupon · checkout\nmergeGuestCart · reactivate\nrefreshPrices"]
        end

        subgraph domain ["domain/"]
            Cart["Cart (Aggregate Root)\ncartId · userId/sessionId · items\ncouponCode · discountAmount\ntotal(BIGINT paise) · expiresAt"]
            LI["LineItem (Value Object)\nsku · productId · name\nsnapshotPrice(BIGINT) · qty"]
        end

        subgraph infra ["infrastructure/"]
            CR["CartRepository\nRedis Hash adapter\nkey: cart:user:{userId}\nkey: cart:guest:{sessionId}"]
            KP["KafkaEventPublisher\nCartCheckedOut\nCartAbandoned"]
            KC["KafkaEventConsumer\nUserLoggedIn ← User/Auth\nProductPriceUpdated ← Catalog\nOrderFailed ← Order\nPaymentFailed ← Payment"]
        end
    end

    GW -->|"REST"| CC
    CC --> CartS
    CartS --> Cart & LI
    CartS --> CR & KP
    KC -->|"user-auth.*\ncatalog.*\norder.*\npayment.*"| CartS
    CR --> Redis
    KP -->|"cart.*"| Kafka
    KC --> Kafka
```

---

## 6. Order Service

**Bounded context:** Order  
**Aggregates:** `Order`, `OrderLineItem`, `Return`  
**DB:** `order_db` (MySQL) including `order_outbox` table  
**Pattern:** Transactional outbox for Kafka event publishing

```mermaid
graph TB
    subgraph external ["External"]
        GW["API Gateway"]
        Kafka["Kafka"]
        DB[("order_db\nMySQL\n+ order_outbox")]
    end

    subgraph os ["Order Service"]
        subgraph api ["api/"]
            OC["OrderController\nPOST /orders\nGET /orders/{id}\nGET /orders (history)\nDELETE /orders/{id} (cancel)\nPOST /orders/{id}/returns"]
            AC["AdminOrderController\nPUT /orders/{id}/status\nPUT /orders/{id}/tracking\nPOST /orders/{id}/notes"]
        end

        subgraph application ["application/"]
            OS["OrderService\nplaceOrder · confirmOrder\nfailOrder · startProcessing\nshipOrder · markDelivered\ncancelOrder · addNote"]
            RS["ReturnService\nrequestReturn · approveReturn\nrejectReturn · expireWindow"]
            OR["OutboxRelay\n500ms poll → Kafka publish\nat-least-once guarantee"]
        end

        subgraph domain ["domain/"]
            Ord["Order (Aggregate Root)\nid · customerId · status\nlines · totalAmount(BIGINT)\ncreatedAt · version(optimistic lock)"]
            OL["OrderLineItem (Entity)\nsku · name · unitPrice(BIGINT)\nqty · subtotal(BIGINT)"]
            Ret["Return (Entity)\nreturnId · orderId · status\nrequestedAt · windowExpiresAt"]
        end

        subgraph infra ["infrastructure/"]
            ORepo["OrderRepository\nJPA — order_db"]
            OutR["OutboxRepository\nJPA — order_outbox"]
            KC["KafkaEventConsumer\nCartCheckedOut ← Cart\nPaymentAuthorised ← Payment\nPaymentFailed ← Payment\nPaymentExpired ← Payment\nStockReservationFailed ← Inventory"]
        end
    end

    GW -->|"REST"| OC & AC
    OC & AC --> OS & RS
    OS --> Ord & OL
    RS --> Ret
    OS & RS --> ORepo & OutR
    OR -->|"reads outbox\npublishes events"| OutR
    OR -->|"order.*"| Kafka
    KC -->|"cart.*\npayment.*\ninventory.*"| OS
    KC --> Kafka
    ORepo & OutR --> DB
```

---

## 7. Payment Service

**Bounded context:** Payment  
**Aggregates:** `Payment`, `Refund`  
**DB:** `payment_db` (MySQL) including `payment_outbox` table  
**Cache:** Redis (idempotency keys)  
**Pattern:** Transactional outbox + HMAC webhook verification

```mermaid
graph TB
    subgraph external ["External"]
        GW["API Gateway"]
        Kafka["Kafka"]
        DB[("payment_db\nMySQL\n+ payment_outbox")]
        Redis["Redis"]
        PayGW["Payment Gateway\n(Stripe/Razorpay)"]
    end

    subgraph ps ["Payment Service"]
        subgraph api ["api/"]
            WC["WebhookController\nPOST /webhooks/payment\nHMAC signature verified\nbefore processing"]
            PC["PaymentController\nGET /payments/{orderId}\nGET /payments/{id}/status\n(internal — not via public GW)"]
        end

        subgraph application ["application/"]
            PayS["PaymentService\ninitiatePayment · capturePayment\nhandleWebhook · initiateRefund\ncheckIdempotency · expirePayment"]
            OR["OutboxRelay\n500ms poll → Kafka publish\nat-least-once guarantee"]
        end

        subgraph domain ["domain/"]
            Pay["Payment (Aggregate Root)\nid · orderId · status\namount(BIGINT paise)\ngatewayRef · idempotencyKey"]
            Ref["Refund (Entity)\nid · paymentId · amount(BIGINT)\nstatus · gatewayRefundRef"]
        end

        subgraph infra ["infrastructure/"]
            PRepo["PaymentRepository\nJPA — payment_db"]
            OutR["OutboxRepository\nJPA — payment_outbox"]
            IR["IdempotencyRepository\nRedis adapter\nkey: idem:{idempotencyKey}\nTTL: 24h"]
            GWC["PaymentGatewayClient\nHTTP REST — Stripe/Razorpay\ncreateIntent · capture · refund"]
            KC["KafkaEventConsumer\nOrderPlaced ← Order\nOrderCancelled ← Order\nReturnApproved ← Order"]
        end
    end

    GW -->|"Webhook POST\n(HMAC signed)"| WC
    GW -->|"REST (internal)"| PC
    WC & PC --> PayS
    PayS --> Pay & Ref
    PayS --> PRepo & OutR & IR & GWC
    OR -->|"reads outbox\npublishes events"| OutR
    OR -->|"payment.*"| Kafka
    KC -->|"order.*"| PayS
    KC --> Kafka
    GWC --> PayGW
    PRepo & OutR --> DB
    IR --> Redis
```

---

## 8. Inventory Service

**Bounded context:** Inventory  
**Aggregates:** `InventoryItem`, `StockReservation`  
**DB:** `inventory_db` (MySQL) including `inventory_outbox` table (ADR-0014, scoped to saga-critical events)  
**Pattern:** Pessimistic locking (`FOR UPDATE`) for saga-triggered stock mutations, optimistic locking (`version`) for admin operations; scheduled job for reservation TTL expiry; transactional outbox for `StockReserved`/`StockReservationFailed`/`StockReleased`/`StockRestored`

```mermaid
graph TB
    subgraph external ["External"]
        GW["API Gateway"]
        Kafka["Kafka"]
        DB[("inventory_db\nMySQL\n+ inventory_outbox")]
    end

    subgraph is ["Inventory Service"]
        subgraph api ["api/"]
            IC["InventoryController\nGET /inventory/{sku}\nPUT /inventory/{sku}/replenish\nPOST /inventory/{sku}/adjust\nPUT /inventory/{sku}/reorder-level\n(admin + internal)"]
        end

        subgraph application ["application/"]
            InvS["InventoryService\ncreateItem · reserveStock\ncommitStock · releaseReservation\nrestoreStock · replenishStock · adjustInventory\nsetReorderLevel · markOutOfStock\ntriggerLowStockAlert"]
            ExpJ["ReservationExpiryJob\n@Scheduled every 30s\nexpires reservations past TTL\npublishes StockReleased"]
            OR["OutboxRelay\n500ms poll → Kafka publish\nat-least-once guarantee\n(StockReserved, StockReservationFailed,\nStockReleased, StockRestored only)"]
        end

        subgraph domain ["domain/"]
            II["InventoryItem (Aggregate Root)\nsku · onHandQty · reservedQty\navailableQty (computed)\nreorderLevel · version(opt. lock)"]
            SR["StockReservation (Entity)\nreservationId · orderId · sku\nqty · expiresAt · status"]
        end

        subgraph infra ["infrastructure/"]
            IRepo["InventoryRepository\nJPA — inventory_db\npessimistic FOR UPDATE for saga ops,\noptimistic lock on version for admin ops"]
            SRRepo["ReservationRepository\nJPA — inventory_db"]
            OutR["OutboxRepository\nJPA — inventory_outbox"]
            KP["KafkaEventPublisher\nProductOutOfStock\nLowStockAlertTriggered\n(direct publish — non-saga events)"]
            KC["KafkaEventConsumer\nOrderPlaced ← Order\nOrderConfirmed ← Order\nOrderCancelled ← Order\nReturnApproved ← Order\nProductVariantAdded ← Catalog\nProductVariantRemoved ← Catalog"]
        end
    end

    GW -->|"REST"| IC
    IC --> InvS
    ExpJ --> InvS
    InvS --> II & SR
    InvS --> IRepo & SRRepo & OutR & KP
    OR -->|"reads outbox\npublishes events"| OutR
    OR -->|"inventory.*\n(StockReserved, StockReservationFailed,\nStockReleased, StockRestored)"| Kafka
    KC -->|"order.*\ncatalog.*"| InvS
    KC --> Kafka
    KP -->|"inventory.*"| Kafka
    IRepo & SRRepo & OutR --> DB
```

---

## 9. Notification Service

**Bounded context:** Notification  
**Aggregates:** `Notification`, `NotificationPreference`  
**DB:** `notification_db` (MySQL)  
**External:** Email Provider (SES/SendGrid), SMS Provider (Twilio), Push Provider (FCM/APNs)  
**Pattern:** Consumer-only — no domain events published; retry with exponential back-off; DLQ after 3 failures

```mermaid
graph TB
    subgraph external ["External"]
        GW["API Gateway"]
        Kafka["Kafka"]
        DB[("notification_db\nMySQL")]
        Email["Email Provider\n(SES/SendGrid)"]
        SMS["SMS Provider\n(Twilio)"]
        Push["Push Provider\n(FCM/APNs)"]
    end

    subgraph ns ["Notification Service"]
        subgraph api ["api/"]
            NC["NotificationController\nGET /notifications/history\nPUT /notifications/preferences\nPOST /notifications/send (admin)"]
        end

        subgraph application ["application/"]
            NS["NotificationService\ndispatch · retry · routeToDLQ\ncheckPreference · logDelivery"]
            RS["RetryScheduler\nBack-off schedule (ADR-0012):\nimmediate → +30s → +5min → +30min\nMax 4 attempts → DLQ"]
        end

        subgraph domain ["domain/"]
            Notif["Notification (Aggregate Root)\nid · userId · type(TRANSACTIONAL|MARKETING)\nchannel(EMAIL|SMS|PUSH)\nstatus · retryCount\nsourceEventId · correlationId"]
            NP["NotificationPreference (Entity)\nuserId · emailOptIn\nsmsOptIn · pushOptIn"]
        end

        subgraph infra ["infrastructure/"]
            NRepo["NotificationRepository\nJPA — notification_db"]
            NPRepo["PreferenceRepository\nJPA — notification_db"]
            EA["EmailAdapter\nSES / SendGrid HTTP client"]
            SA["SmsAdapter\nTwilio HTTP client"]
            PA["PushAdapter\nFCM / APNs HTTP client"]
            KC["KafkaEventConsumer\nUserRegistered ← User/Auth\nCartAbandoned ← Cart\nOrderPlaced/Confirmed/\nFailed/Cancelled/Shipped/\nDelivered ← Order\nPaymentFailed/RefundProcessed ← Payment\nLowStockAlertTriggered ← Inventory"]
        end
    end

    GW -->|"REST"| NC
    NC --> NS
    RS --> NS
    NS --> Notif & NP
    NS --> NRepo & NPRepo & EA & SA & PA
    KC -->|"user-auth.*\ncart.*\norder.*\npayment.*\ninventory.*"| NS
    KC --> Kafka
    NRepo & NPRepo --> DB
    EA --> Email
    SA --> SMS
    PA --> Push
```

---

## 10. Cross-Cutting Component Patterns

The following components appear in every service and are not repeated in each diagram above.

### 10.1 Exception Handling

```
GlobalExceptionHandler (@RestControllerAdvice)
  ├── DomainException → HTTP 422 + correlationId
  ├── ResourceNotFoundException → HTTP 404
  ├── ValidationException → HTTP 400 + field errors
  ├── OptimisticLockException → HTTP 409 + retry hint
  └── Uncaught → HTTP 500 + correlationId (no stack trace exposed)
```

All error responses include `correlationId` (UUID, propagated from `X-Correlation-ID` request header or generated if absent).

### 10.2 Observability

```
MicrometerMetricsFilter (per request)
  └── records: http.server.requests (method, path, status, duration)

KafkaConsumerMetricsListener
  └── records: kafka.consumer.lag, kafka.consumer.records-consumed

@Timed annotations on Service methods
  └── records: business operation duration histograms
```

All spans carry `correlationId` and `traceId` (Micrometer Tracing → Zipkin/Tempo).

### 10.3 Security Filter Chain

```
SecurityFilterChain (Spring Security)
  ├── CorsFilter
  ├── JwtAuthenticationFilter
  │     └── validates RS256 signature + exp claim
  │     └── populates SecurityContext with userId + roles
  ├── RateLimitFilter (Redis token bucket)
  └── AuthorizationFilter (role-based per endpoint)
```

`/webhooks/payment` bypasses JWT filter — HMAC verification is done inside `WebhookController`.

---

## 11. Open Questions

| # | Question | Severity | Owner |
|---|---|---|---|
| OQ-C3-01 | Should `CartService` call `InventoryService` synchronously at checkout to validate stock, or rely on eventual consistency (reserve on `OrderPlaced`)? A sync call reduces oversell but adds coupling. | High | Architect — resolve before Cart LLD |
| OQ-C3-02 | `ReservationExpiryJob` polling every 30s — acceptable lag for reservation TTL? Flash-sale scenarios may need sub-10s expiry. Consider Redis TTL + keyspace notification as an alternative. | Medium | Architect — note in Inventory LLD |
| OQ-C3-03 | Notification `RetryScheduler` — in-process scheduler vs dedicated Kafka retry topic (standard pattern). Kafka retry topics are more resilient on service restart. | Medium | Architect — resolve in ADR-008 |

---

## 12. Next Artefacts

| Artefact | Task |
|---|---|
| Sequence diagrams (Order saga, Payment flow, Inventory reservation) | SA-004 |
| ER diagrams — all 5 MySQL services | SA-005 |
| Order state machine | SA-006 |
| ADR-001: Monetary precision | SA-007 |
