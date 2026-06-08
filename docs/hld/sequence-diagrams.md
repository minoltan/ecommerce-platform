# Sequence Diagrams — High-Level Design

**Artefact type:** Cross-service sequence diagrams  
**Phase:** ARCH  
**Status:** Draft  
**Version:** 0.1  
**Date:** 2026-06-08  
**Author:** System Architect  
**Inputs:** `docs/hld/component-diagrams.md`, `docs/requirements/event-storming.md` v0.3

---

## 1. Scope

This document covers all key cross-service flows, expanding on the event storming saga diagrams with implementation-level detail:

- Solid arrows `→` = synchronous REST/HTTP call (caller waits for response)
- Dashed arrows `-->` = asynchronous Kafka event (fire and forget; consumer processes independently)
- All flows carry `X-Correlation-ID` header (HTTP) or `correlationId` field (Kafka event payload)
- Monetary amounts in all diagrams are in **paise (integer)** per ADR-001

---

## 2. Flow Index

| # | Flow | Type | Services involved |
|---|---|---|---|
| SD-01 | User Registration + Email Verification | Auth | User/Auth, Notification |
| SD-02 | User Login + Guest Cart Merge | Auth | User/Auth, Cart |
| SD-03 | Refresh Token Rotation | Auth | User/Auth |
| SD-04 | Product Search | Read | API Gateway, Product Catalog, Search Index |
| SD-05 | Add Item to Cart | Write | Cart |
| SD-06 | Checkout → Order Placement (Saga A — Happy Path) | Saga | Cart, Order, Inventory, Payment, Notification |
| SD-07 | Payment Failure Compensation (Saga B) | Saga | Payment, Order, Inventory, Cart, Notification |
| SD-08 | Stock Unavailable Compensation (Saga C) | Saga | Order, Inventory, Cart, Notification |
| SD-09 | Order Cancellation + Refund (Saga D) | Saga | Order, Inventory, Payment, Notification |
| SD-10 | Stockout → Catalog Unpublish (Saga E) | Saga | Inventory, Product Catalog, Notification |
| SD-11 | Notification Retry + DLQ Escalation | Infra | Notification, Email Provider |
| SD-12 | Admin: Manual Stock Replenishment | Write | Inventory, Product Catalog, Notification |

---

## 3. SD-01 — User Registration + Email Verification

```mermaid
sequenceDiagram
    actor Guest
    participant GW as API Gateway
    participant UA as User/Auth Service
    participant DB as user_db
    participant Kafka
    participant NS as Notification Service
    participant Email as Email Provider

    Guest->>GW: POST /auth/register {email, password, name}
    GW->>UA: POST /auth/register (JWT not required)

    UA->>UA: Validate email format, password strength
    UA->>DB: Check email uniqueness
    DB-->>UA: email not found ✓

    UA->>UA: bcrypt password (cost ≥ 12)
    UA->>UA: Generate emailVerificationToken (UUID)
    UA->>DB: INSERT user (status=UNVERIFIED) + INSERT outbox row (UserRegistered)
    DB-->>UA: committed ✓

    UA-->>GW: HTTP 201 {userId, message: "Check your email"}
    GW-->>Guest: HTTP 201

    Note over UA: Outbox relay fires async
    UA->>Kafka: PUBLISH user-auth.user.registered {userId, email, verificationToken, correlationId}

    Kafka-->>NS: CONSUME UserRegistered
    NS->>NS: Build verification email from template
    NS->>DB: INSERT notification (status=PENDING)
    NS->>Email: POST /send {to, subject, body, correlationId}
    Email-->>NS: HTTP 200 accepted ✓
    NS->>DB: UPDATE notification (status=SENT)

    Note over Guest: Guest clicks link in email
    Guest->>GW: GET /auth/verify?token={verificationToken}
    GW->>UA: GET /auth/verify
    UA->>DB: UPDATE user SET status=ACTIVE, emailVerifiedAt=now()
    UA-->>GW: HTTP 200 {message: "Email verified"}
    GW-->>Guest: HTTP 200
```

---

## 4. SD-02 — User Login + Guest Cart Merge

```mermaid
sequenceDiagram
    actor Customer
    participant GW as API Gateway
    participant UA as User/Auth Service
    participant CS as Cart Service
    participant Redis
    participant Kafka

    Customer->>GW: POST /auth/login {email, password} + sessionId cookie
    GW->>UA: POST /auth/login

    UA->>UA: Load user by email
    UA->>UA: bcrypt.verify(password, hash)
    UA->>UA: Generate accessToken (JWT RS256, 15min TTL)
    UA->>UA: Generate refreshToken (UUID)
    UA->>Redis: SET refresh:{userId}:{tokenId} = tokenHash (TTL 7 days)
    UA->>Kafka: PUBLISH user-auth.user.logged-in {userId, sessionId, correlationId}

    UA-->>GW: HTTP 200 {accessToken, expiresIn:900}
    Note over GW: Set HttpOnly cookie: refreshToken
    GW-->>Customer: HTTP 200 + Set-Cookie: refreshToken

    Kafka-->>CS: CONSUME UserLoggedIn {userId, sessionId}
    CS->>Redis: GET cart:guest:{sessionId}
    Redis-->>CS: guestCart (may be empty)

    alt Guest cart has items
        CS->>Redis: GET cart:user:{userId}
        Redis-->>CS: userCart (may be empty)
        CS->>CS: Merge: sum quantities for duplicate SKUs
        CS->>Redis: SET cart:user:{userId} = mergedCart (TTL 7 days)
        CS->>Redis: DEL cart:guest:{sessionId}
        Note over CS: CartsMerged — internal event, not published
    else No guest cart
        Note over CS: No action required
    end
```

---

## 5. SD-03 — Refresh Token Rotation

```mermaid
sequenceDiagram
    actor Customer
    participant GW as API Gateway
    participant UA as User/Auth Service
    participant Redis

    Note over Customer: accessToken expired (15 min)
    Customer->>GW: POST /auth/refresh (Cookie: refreshToken)
    GW->>UA: POST /auth/refresh {refreshToken}

    UA->>Redis: GET refresh:{userId}:{tokenId}
    Redis-->>UA: storedHash ✓

    UA->>UA: Verify storedHash matches incoming token
    UA->>UA: Check token not expired (7-day TTL)

    UA->>UA: Generate new accessToken (JWT RS256, 15min)
    UA->>UA: Generate new refreshToken (rotate)
    UA->>Redis: DEL refresh:{userId}:{oldTokenId}
    UA->>Redis: SET refresh:{userId}:{newTokenId} (TTL 7 days)

    UA-->>GW: HTTP 200 {accessToken, expiresIn:900}
    Note over GW: Set new HttpOnly cookie: refreshToken
    GW-->>Customer: HTTP 200 + Set-Cookie: refreshToken (rotated)

    Note over UA,Redis: If stolen token reuse detected (old tokenId still presented after rotation),<br/>revoke all sessions for userId — publish UserSessionsRevoked
```

---

## 6. SD-04 — Product Search

```mermaid
sequenceDiagram
    actor User as Guest or Customer
    participant GW as API Gateway
    participant PC as Product Catalog Service
    participant ES as Search Index (Elasticsearch)
    participant Cache as Redis

    User->>GW: GET /products/search?q=running+shoes&category=footwear&page=0
    GW->>GW: Rate limit check (100 req/min unauthenticated)
    GW->>PC: GET /products/search?q=...

    PC->>Cache: GET search:running+shoes:footwear:0
    Cache-->>PC: MISS

    PC->>ES: POST /products/_search {query, filters, from, size}
    ES-->>PC: {hits, total, took}

    PC->>PC: Map ES hits → ProductSummaryDTO
    PC->>Cache: SET search:running+shoes:footwear:0 (TTL 60s)

    PC-->>GW: HTTP 200 {items[], total, page, pageSize}
    GW-->>User: HTTP 200

    Note over ES,PC: Cache TTL 60s balances freshness<br/>vs search index load
```

---

## 7. SD-05 — Add Item to Cart

```mermaid
sequenceDiagram
    actor Customer
    participant GW as API Gateway
    participant CS as Cart Service
    participant PC as Product Catalog Service
    participant Redis

    Customer->>GW: POST /cart/items {sku, qty:2} + JWT
    GW->>GW: Validate JWT (RS256)
    GW->>CS: POST /cart/items {sku, qty, userId from JWT}

    CS->>PC: GET /products/{sku} (sync — fetch price snapshot)
    PC-->>CS: HTTP 200 {sku, name, price:49900, status:PUBLISHED}

    alt Product not PUBLISHED or price = 0
        CS-->>GW: HTTP 422 {code: PRODUCT_UNAVAILABLE}
        GW-->>Customer: HTTP 422
    end

    CS->>Redis: GET cart:user:{userId}
    Redis-->>CS: existing cart

    CS->>CS: Snapshot price (49900 paise) into LineItem
    CS->>CS: Add/update LineItem in Cart aggregate
    CS->>CS: Recalculate cart total

    CS->>Redis: SET cart:user:{userId} = updatedCart (TTL 7 days)
    Redis-->>CS: OK

    CS-->>GW: HTTP 200 {cart summary, itemCount, total}
    GW-->>Customer: HTTP 200

    Note over CS: Price is snapshotted at add-to-cart time.<br/>Cart flags stale prices on ProductPriceUpdated event.
```

---

## 8. SD-06 — Checkout → Order Placement (Saga A — Happy Path)

```mermaid
sequenceDiagram
    actor Customer
    participant GW as API Gateway
    participant CS as Cart Service
    participant Redis
    participant Kafka
    participant OS as Order Service
    participant ODB as order_db
    participant IS as Inventory Service
    participant IDB as inventory_db
    participant PS as Payment Service
    participant PDB as payment_db
    participant PayGW as Payment Gateway
    participant NS as Notification Service

    Customer->>GW: POST /cart/checkout + JWT
    GW->>CS: POST /cart/checkout {userId}

    CS->>Redis: GET cart:user:{userId}
    Redis-->>CS: cart {items, total}

    CS->>CS: Validate: cart not empty, no stale-price flags
    CS->>CS: Emit CartCheckedOut (write to cart state)
    CS->>Redis: DEL cart:user:{userId}
    CS->>Kafka: PUBLISH cart.cart.checked-out {orderId, userId, items[], total, correlationId}

    CS-->>GW: HTTP 202 {orderId, message: "Order being placed"}
    GW-->>Customer: HTTP 202 + orderId

    Kafka-->>OS: CONSUME CartCheckedOut
    OS->>ODB: BEGIN TX — INSERT order (status=PENDING) + INSERT outbox (OrderPlaced)
    ODB-->>OS: committed ✓
    Note over OS: Outbox relay fires
    OS->>Kafka: PUBLISH order.order.placed {orderId, items[], total, correlationId}

    par Stock reservation
        Kafka-->>IS: CONSUME OrderPlaced
        IS->>IDB: BEGIN TX — SELECT InventoryItem FOR UPDATE (optimistic lock)
        IS->>IS: availableQty >= requested qty?
        IDB-->>IS: yes — UPDATE reservedQty, INSERT StockReservation (TTL 15 min)
        IS->>IDB: COMMIT
        IS->>Kafka: PUBLISH inventory.stock.reserved {orderId, reservationId, correlationId}
    and Payment initiation
        Kafka-->>PS: CONSUME OrderPlaced
        PS->>PS: Check idempotency key in Redis
        PS->>PayGW: POST /payment-intents {amount, orderId, idempotencyKey}
        PayGW-->>PS: HTTP 200 {intentId, clientSecret, status:REQUIRES_ACTION}
        PS->>PDB: INSERT payment (status=INITIATED) + INSERT outbox (PaymentInitiated)
    end

    Note over Customer: Customer completes payment in frontend (Stripe/Razorpay widget)

    PayGW->>GW: POST /webhooks/payment {event:AUTHORISED, intentId, orderId}
    GW->>PS: POST /webhooks/payment (bypass JWT — HMAC verified inside)
    PS->>PS: Verify HMAC signature
    PS->>PDB: BEGIN TX — UPDATE payment (status=AUTHORISED) + INSERT outbox (PaymentAuthorised)
    PDB-->>PS: committed ✓
    PS->>Kafka: PUBLISH payment.payment.authorised {orderId, amount, correlationId}

    Kafka-->>OS: CONSUME PaymentAuthorised
    OS->>ODB: BEGIN TX — UPDATE order (status=CONFIRMED) + INSERT outbox (OrderConfirmed)
    ODB-->>OS: committed ✓
    OS->>Kafka: PUBLISH order.order.confirmed {orderId, correlationId}

    par Post-confirmation
        Kafka-->>IS: CONSUME OrderConfirmed
        IS->>IDB: UPDATE — reservation → StockCommitted (permanent deduction from onHandQty)
    and
        Kafka-->>NS: CONSUME OrderConfirmed
        NS->>NS: Build order confirmation email + SMS
        NS->>Customer: Order confirmation email + SMS
    end
```

---

## 9. SD-07 — Payment Failure Compensation (Saga B)

```mermaid
sequenceDiagram
    participant PayGW as Payment Gateway
    participant GW as API Gateway
    participant PS as Payment Service
    participant PDB as payment_db
    participant Kafka
    participant OS as Order Service
    participant ODB as order_db
    participant IS as Inventory Service
    participant IDB as inventory_db
    participant CS as Cart Service
    participant Redis
    participant NS as Notification Service

    PayGW->>GW: POST /webhooks/payment {event:FAILED, orderId}
    GW->>PS: POST /webhooks/payment
    PS->>PS: Verify HMAC signature
    PS->>PDB: BEGIN TX — UPDATE payment (status=FAILED) + INSERT outbox (PaymentFailed)
    PDB-->>PS: committed ✓
    PS->>Kafka: PUBLISH payment.payment.failed {orderId, reason, correlationId}

    par Compensation — parallel
        Kafka-->>OS: CONSUME PaymentFailed
        OS->>ODB: BEGIN TX — UPDATE order (status=FAILED) + INSERT outbox (OrderFailed)
        ODB-->>OS: committed ✓
        OS->>Kafka: PUBLISH order.order.failed {orderId, reason, correlationId}
    end

    par Downstream compensation — parallel
        Kafka-->>IS: CONSUME OrderFailed
        IS->>IDB: BEGIN TX — DELETE StockReservation, UPDATE reservedQty -= qty
        IDB-->>IS: StockReleased ✓
        IS->>Kafka: PUBLISH inventory.stock.released {orderId, correlationId}
    and
        Kafka-->>CS: CONSUME OrderFailed
        CS->>CS: Rebuild cart from OrderFailed event line items
        CS->>Redis: SET cart:user:{userId} = reactivatedCart (TTL 7 days)
    and
        Kafka-->>NS: CONSUME PaymentFailed
        NS->>NS: Build payment failure email
        NS->>Customer: Payment failure alert email
    end

    Note over CS: Cart is restored — customer can retry checkout<br/>with a different payment method
```

---

## 10. SD-08 — Stock Unavailable Compensation (Saga C)

```mermaid
sequenceDiagram
    participant Kafka
    participant OS as Order Service
    participant ODB as order_db
    participant IS as Inventory Service
    participant IDB as inventory_db
    participant CS as Cart Service
    participant Redis
    participant NS as Notification Service

    Note over IS: OrderPlaced consumed; availableQty < requested qty
    IS->>IDB: No reservation written — constraint violated
    IS->>Kafka: PUBLISH inventory.stock.reservation-failed {orderId, sku, requestedQty, availableQty, correlationId}

    Kafka-->>OS: CONSUME StockReservationFailed
    OS->>ODB: BEGIN TX — UPDATE order (status=FAILED) + INSERT outbox (OrderFailed)
    ODB-->>OS: committed ✓
    OS->>Kafka: PUBLISH order.order.failed {orderId, reason:STOCK_UNAVAILABLE, correlationId}

    par Downstream compensation — parallel
        Kafka-->>CS: CONSUME OrderFailed
        CS->>CS: Rebuild cart from order line items
        CS->>Redis: SET cart:user:{userId} = reactivatedCart (TTL 7 days)
    and
        Kafka-->>NS: CONSUME OrderFailed
        NS->>NS: Build stock unavailable email
        NS->>Customer: "Item out of stock" email + cart restored message
    end

    Note over OS,IS: Payment service also consumes OrderFailed —<br/>if payment was INITIATED but not yet AUTHORISED,<br/>cancel the payment intent with the gateway
```

---

## 11. SD-09 — Order Cancellation + Refund (Saga D)

```mermaid
sequenceDiagram
    actor Customer
    participant GW as API Gateway
    participant OS as Order Service
    participant ODB as order_db
    participant Kafka
    participant IS as Inventory Service
    participant IDB as inventory_db
    participant PS as Payment Service
    participant PDB as payment_db
    participant PayGW as Payment Gateway
    participant NS as Notification Service

    Customer->>GW: DELETE /orders/{orderId} + JWT
    GW->>OS: DELETE /orders/{orderId}

    OS->>ODB: SELECT order WHERE id = orderId
    OS->>OS: Validate: status in [PENDING, CONFIRMED] (customer-cancellable states)
    OS->>ODB: BEGIN TX — UPDATE order (status=CANCELLED) + INSERT outbox (OrderCancelled)
    ODB-->>OS: committed ✓

    OS-->>GW: HTTP 200 {orderId, status:CANCELLED}
    GW-->>Customer: HTTP 200

    OS->>Kafka: PUBLISH order.order.cancelled {orderId, correlationId}

    par Compensation — parallel
        Kafka-->>IS: CONSUME OrderCancelled
        IS->>IDB: DELETE StockReservation, UPDATE reservedQty -= qty
        IS->>Kafka: PUBLISH inventory.stock.released {orderId, correlationId}
    and
        Kafka-->>PS: CONSUME OrderCancelled
        PS->>PDB: SELECT payment WHERE orderId = orderId

        alt Payment was CAPTURED
            PS->>PayGW: POST /refunds {paymentId, amount, idempotencyKey}
            PayGW-->>PS: HTTP 200 {refundId, status:PENDING}
            PS->>PDB: BEGIN TX — INSERT refund (status=PENDING) + INSERT outbox (RefundInitiated)

            Note over PayGW,PS: Gateway processes refund (async)
            PayGW->>GW: POST /webhooks/payment {event:REFUND_PROCESSED, refundId}
            GW->>PS: POST /webhooks/payment
            PS->>PDB: BEGIN TX — UPDATE refund (status=PROCESSED) + INSERT outbox (RefundProcessed)
            PS->>Kafka: PUBLISH payment.refund.processed {orderId, refundId, amount, correlationId}

            Kafka-->>NS: CONSUME RefundProcessed
            NS->>Customer: Cancellation + refund confirmation email
        else Payment was AUTHORISED (not CAPTURED)
            PS->>PayGW: POST /payment-intents/{id}/cancel
            PayGW-->>PS: HTTP 200 {status:CANCELLED}
            PS->>PDB: UPDATE payment (status=CANCELLED)
            Kafka-->>NS: CONSUME OrderCancelled
            NS->>Customer: Cancellation confirmation email (no refund — card not charged)
        else Payment was INITIATED or FAILED
            Note over PS: No refund needed — payment never completed
            Kafka-->>NS: CONSUME OrderCancelled
            NS->>Customer: Cancellation confirmation email
        end
    end
```

---

## 12. SD-10 — Stockout → Catalog Unpublish (Saga E)

```mermaid
sequenceDiagram
    participant IS as Inventory Service
    participant IDB as inventory_db
    participant Kafka
    participant PC as Product Catalog Service
    participant PDB as catalog_db
    participant ES as Search Index
    participant NS as Notification Service

    Note over IS: StockCommitted brings availableQty to 0
    IS->>IDB: UPDATE inventoryItem (availableQty = 0)
    IS->>IS: Invariant check: availableQty = 0 → emit ProductOutOfStock + LowStockAlertTriggered
    IS->>Kafka: PUBLISH inventory.product.out-of-stock {sku, productId, correlationId}
    IS->>Kafka: PUBLISH inventory.stock.low-alert {sku, productId, availableQty:0, correlationId}

    par
        Kafka-->>PC: CONSUME ProductOutOfStock
        PC->>PDB: BEGIN TX — UPDATE product (status=UNPUBLISHED)
        PDB-->>PC: committed ✓
        PC->>ES: DELETE /products/{productId} (de-index)
        ES-->>PC: acknowledged ✓
        PC->>Kafka: PUBLISH catalog.product.unpublished {productId, reason:OUT_OF_STOCK, correlationId}
    and
        Kafka-->>NS: CONSUME LowStockAlertTriggered
        NS->>NS: Check preference: Admin always receives operational alerts
        NS->>AdminEmail: Low stock alert email {sku, productName, currentQty:0}
    end

    Note over PC,ES: Product no longer visible in search or browse.<br/>Replenishment → StockReplenished → re-publish flow reverses this.
```

---

## 13. SD-11 — Notification Retry + DLQ Escalation

```mermaid
sequenceDiagram
    participant NS as Notification Service
    participant NDB as notification_db
    participant Email as Email Provider
    participant DLQ as Dead Letter Queue

    Note over NS: Initial dispatch attempt
    NS->>Email: POST /send {to, subject, body, correlationId}
    Email-->>NS: HTTP 503 Service Unavailable

    NS->>NDB: UPDATE notification SET retryCount=1, nextRetryAt=now()+1min, status=PENDING

    Note over NS: RetryScheduler fires after 1 min
    NS->>Email: POST /send (retry 1)
    Email-->>NS: HTTP 503

    NS->>NDB: UPDATE notification SET retryCount=2, nextRetryAt=now()+5min

    Note over NS: RetryScheduler fires after 5 min
    NS->>Email: POST /send (retry 2)
    Email-->>NS: HTTP 503

    NS->>NDB: UPDATE notification SET retryCount=3, nextRetryAt=now()+15min

    Note over NS: RetryScheduler fires after 15 min
    NS->>Email: POST /send (retry 3 — final)
    Email-->>NS: HTTP 503

    NS->>NDB: UPDATE notification SET status=DLQ, abandondedAt=now()
    NS->>DLQ: ROUTE notification {id, userId, type, channel, correlationId, failureReason}

    Note over DLQ: Ops team monitors DLQ.<br/>Manual re-send via Admin API: POST /notifications/{id}/resend

    Note over NS: Idempotency: Each attempt carries same correlationId.<br/>If provider delivers despite 5xx, deduplication on (userId, sourceEventId, channel)<br/>prevents duplicate delivery on retry.
```

---

## 14. SD-12 — Admin: Manual Stock Replenishment

```mermaid
sequenceDiagram
    actor Admin
    participant GW as API Gateway
    participant IS as Inventory Service
    participant IDB as inventory_db
    participant Kafka
    participant PC as Product Catalog Service
    participant PDB as catalog_db
    participant ES as Search Index
    participant NS as Notification Service

    Admin->>GW: PUT /inventory/{sku}/replenish {qty: 100, warehouseId} + JWT (ADMIN role)
    GW->>GW: Validate JWT, verify ADMIN role claim
    GW->>IS: PUT /inventory/{sku}/replenish

    IS->>IDB: BEGIN TX — UPDATE inventoryItem SET onHandQty += 100
    IS->>IS: Check: was availableQty = 0 before replenishment?

    alt Product was out of stock
        IS->>Kafka: PUBLISH inventory.stock.replenished {sku, newQty, wasOutOfStock:true, correlationId}

        Kafka-->>PC: CONSUME StockReplenished (wasOutOfStock=true)
        PC->>PDB: BEGIN TX — UPDATE product (status=PUBLISHED)
        PC->>ES: POST /products (re-index)
        PC->>Kafka: PUBLISH catalog.product.published {productId, correlationId}

        Kafka-->>NS: CONSUME StockReplenished
        NS->>Admin: Low stock alert cleared email
    else Was already in stock (qty top-up)
        IS->>Kafka: PUBLISH inventory.stock.replenished {sku, newQty, wasOutOfStock:false, correlationId}
    end

    IDB-->>IS: committed ✓
    IS-->>GW: HTTP 200 {sku, onHandQty, availableQty, reservedQty}
    GW-->>Admin: HTTP 200
```

---

## 15. Timing and TTL Summary

| Flow | TTL / Timeout | What happens on expiry |
|---|---|---|
| Guest cart (Redis) | 30 minutes inactivity | Cart deleted; `CartAbandoned` event published → abandonment email |
| Authenticated cart (Redis) | 7 days | Cart deleted; no abandonment email for logged-in users |
| Payment intent (15 min) | 15 min after `OrderPlaced` | `PaymentExpired` event → Order fails → stock released → cart reactivated |
| Stock reservation | 15 min TTL on `StockReservation` row | `ReservationExpired` → `StockReleased` event |
| Refresh token | 7 days | Token invalid; user must re-login |
| Access token (JWT) | 15 min | Token rejected at gateway; client must call `/auth/refresh` |
| Notification retry back-off | 1 min → 5 min → 15 min | After 3rd failure → routed to DLQ |
| Return window | 30 days from `OrderDelivered` | `ReturnWindowExpired` event; return requests rejected after this |

---

## 16. Open Questions

| # | Question | Severity | Flow affected | Target ADR |
|---|---|---|---|---|
| OQ-SD-01 | In SD-06, Payment and Inventory consume `OrderPlaced` in parallel. If Inventory reservation fails and Payment is already AUTHORISED — which service detects and compensates? Order service must handle a `StockReservationFailed` arriving after `PaymentAuthorised`. | High | SD-06 / SD-08 | ADR-003 (saga) |
| OQ-SD-02 | In SD-06, should Cart validate stock before calling checkout, or rely on the saga? Sync pre-check reduces failure rate but adds Cart→Inventory coupling. | High | SD-05 / SD-06 | OQ-C3-01 |
| OQ-SD-03 | In SD-09, if the refund webhook from the gateway never arrives — when does the Order consider the refund resolved? Reconciliation job needed. | Medium | SD-09 | ADR-004 |
| OQ-SD-04 | In SD-11, should retry scheduling use in-process `@Scheduled` or a dedicated Kafka retry topic (more resilient on service restart)? | Medium | SD-11 | ADR-008 |
