# RE-04: User Stories — Epics and Stories

**Phase:** Requirement Engineering
**Format:** Jira-ready (Epic → Story hierarchy)
**Status:** Draft v0.1

---

## Epic Index

| Epic ID | Name | Bounded Context | Priority |
|---|---|---|---|
| EP-01 | User Identity & Access | User/Auth | Must |
| EP-02 | Product Discovery | Product Catalog | Must |
| EP-03 | Shopping Cart | Cart | Must |
| EP-04 | Order Management | Order | Must |
| EP-05 | Payment Processing | Payment | Must |
| EP-06 | Inventory Management | Inventory | Must |
| EP-07 | Notifications | Notification | Should |

---

## EP-01: User Identity & Access

**Goal:** Allow customers and admins to securely register, authenticate, and manage their identity.
**Context:** User/Auth | **Priority:** Must

---

**US-UA-01**
```
As a Guest,
I want to register with my email and password,
So that I can create an account and start shopping.

context: User/Auth
priority: Must
type: functional
estimate: 3 points
tags: registration, onboarding
```

**US-UA-02**
```
As a Registered User,
I want to verify my email address via a link sent to me,
So that the platform confirms I own the email before activating my account.

context: User/Auth
priority: Must
type: functional
estimate: 2 points
tags: email-verification, security
```

**US-UA-03**
```
As a Verified User,
I want to log in with my email and password,
So that I can access my account and its features.

context: User/Auth
priority: Must
type: functional
estimate: 2 points
tags: authentication, JWT
```

**US-UA-04**
```
As an Authenticated User,
I want my session to stay active without re-logging in for 7 days,
So that I have a seamless experience across sessions.

context: User/Auth
priority: Must
type: functional
estimate: 2 points
tags: refresh-token, session
```

**US-UA-05**
```
As an Authenticated User,
I want to reset my password via email when I forget it,
So that I can regain access to my account without contacting support.

context: User/Auth
priority: Must
type: functional
estimate: 3 points
tags: password-reset, self-service
```

**US-UA-06**
```
As an Authenticated User,
I want to update my profile (name, phone, delivery addresses),
So that my account information stays current.

context: User/Auth
priority: Must
type: functional
estimate: 2 points
tags: profile-management
```

**US-UA-07**
```
As an Admin,
I want to deactivate a user account,
So that I can suspend access for policy violations or user requests.

context: User/Auth
priority: Must
type: functional
estimate: 2 points
tags: admin, account-management
```

---

## EP-02: Product Discovery

**Goal:** Enable customers to browse, search, and view products efficiently.
**Context:** Product Catalog | **Priority:** Must

---

**US-PC-01**
```
As an Admin,
I want to create a product with name, description, price, category, and images,
So that I can list products for customers to purchase.

context: Product Catalog
priority: Must
type: functional
estimate: 5 points
tags: product-management, admin
```

**US-PC-02**
```
As an Admin,
I want to publish or unpublish a product,
So that I control which products are visible to customers.

context: Product Catalog
priority: Must
type: functional
estimate: 1 point
tags: product-lifecycle
```

**US-PC-03**
```
As a Customer,
I want to search for products by keyword,
So that I can quickly find what I am looking for.

context: Product Catalog
priority: Must
type: functional
estimate: 5 points
tags: search, discovery
```

**US-PC-04**
```
As a Customer,
I want to filter search results by category, price range, and attributes,
So that I can narrow down results to relevant products.

context: Product Catalog
priority: Must
type: functional
estimate: 3 points
tags: filter, search
```

**US-PC-05**
```
As a Customer,
I want to view a product's full details (images, description, variants, stock status),
So that I can make an informed purchase decision.

context: Product Catalog
priority: Must
type: functional
estimate: 3 points
tags: product-detail, pdp
```

**US-PC-06**
```
As an Admin,
I want to update a product's price and see the previous price retained for display,
So that customers can see a "was / now" comparison.

context: Product Catalog
priority: Should
type: functional
estimate: 2 points
tags: pricing, promotions
```

**US-PC-07**
```
As an Admin,
I want to manage product variants (size, colour) each with their own SKU and price,
So that a single product listing covers all its variations.

context: Product Catalog
priority: Must
type: functional
estimate: 5 points
tags: variants, sku
```

---

## EP-03: Shopping Cart

**Goal:** Allow customers to build and manage their cart before checkout.
**Context:** Cart | **Priority:** Must

---

**US-CT-01**
```
As a Customer,
I want to add a product to my cart,
So that I can collect items before purchasing.

context: Cart
priority: Must
type: functional
estimate: 3 points
tags: cart, add-to-cart
```

**US-CT-02**
```
As a Customer,
I want to update the quantity or remove an item from my cart,
So that I can adjust my selection before checkout.

context: Cart
priority: Must
type: functional
estimate: 2 points
tags: cart, item-management
```

**US-CT-03**
```
As a Customer,
I want to see a cart summary with item prices, subtotal, discounts, and total,
So that I know exactly what I will be charged before placing the order.

context: Cart
priority: Must
type: functional
estimate: 2 points
tags: cart-summary, pricing
```

**US-CT-04**
```
As a Customer,
I want to apply a coupon code to my cart,
So that I can receive a promotional discount.

context: Cart
priority: Should
type: functional
estimate: 3 points
tags: coupon, discount
```

**US-CT-05**
```
As a Guest Customer,
I want my cart to be preserved when I log in,
So that I do not lose my selections when I authenticate.

context: Cart
priority: Must
type: functional
estimate: 3 points
tags: cart-merge, guest-to-auth
```

**US-CT-06**
```
As a Customer,
I want to be notified if a product's price has changed since I added it to my cart,
So that I can decide whether to proceed before checkout.

context: Cart
priority: Should
type: functional
estimate: 2 points
tags: price-change, cart-validation
```

---

## EP-04: Order Management

**Goal:** Allow customers to place, track, and manage their orders.
**Context:** Order | **Priority:** Must

---

**US-OR-01**
```
As a Customer,
I want to place an order from my cart,
So that I can purchase the items I have selected.

context: Order
priority: Must
type: functional
estimate: 8 points
tags: checkout, order-placement, saga
```

**US-OR-02**
```
As a Customer,
I want to receive an order confirmation with a unique order ID,
So that I have a reference for tracking and support.

context: Order
priority: Must
type: functional
estimate: 1 point
tags: order-confirmation
```

**US-OR-03**
```
As a Customer,
I want to view my order history with status and item details,
So that I can track my past and current purchases.

context: Order
priority: Must
type: functional
estimate: 3 points
tags: order-history
```

**US-OR-04**
```
As a Customer,
I want to cancel an order that has not yet been shipped,
So that I can change my mind before fulfilment.

context: Order
priority: Must
type: functional
estimate: 5 points
tags: order-cancellation, saga-compensation
```

**US-OR-05**
```
As a Customer,
I want to request a return within 30 days of delivery,
So that I can get a refund for items I am not satisfied with.

context: Order
priority: Should
type: functional
estimate: 5 points
tags: returns, refund
```

**US-OR-06**
```
As an Admin,
I want to view and manage all orders across all customers,
So that I can support fulfilment and resolve customer issues.

context: Order
priority: Must
type: functional
estimate: 5 points
tags: admin, order-management
```

---

## EP-05: Payment Processing

**Goal:** Securely process payments and refunds for orders.
**Context:** Payment | **Priority:** Must

---

**US-PM-01**
```
As a Customer,
I want to pay for my order using a credit or debit card,
So that I can complete my purchase.

context: Payment
priority: Must
type: functional
estimate: 8 points
tags: payment, card, gateway-integration
```

**US-PM-02**
```
As a Customer,
I want to receive confirmation that my payment was successful,
So that I am confident my order has been placed.

context: Payment
priority: Must
type: functional
estimate: 1 point
tags: payment-confirmation
```

**US-PM-03**
```
As a Customer,
I want to be informed immediately if my payment fails,
So that I can retry with a different payment method.

context: Payment
priority: Must
type: functional
estimate: 3 points
tags: payment-failure, retry
```

**US-PM-04**
```
As a Customer,
I want to receive a refund when my order is cancelled or return is approved,
So that I get my money back in a timely manner.

context: Payment
priority: Must
type: functional
estimate: 5 points
tags: refund, cancellation
```

**US-PM-05**
```
As the System,
I want to process payment gateway webhooks idempotently,
So that retried webhook calls do not result in duplicate payments or refunds.

context: Payment
priority: Must
type: functional
estimate: 3 points
tags: webhook, idempotency, reliability
```

---

## EP-06: Inventory Management

**Goal:** Track stock levels and prevent overselling.
**Context:** Inventory | **Priority:** Must

---

**US-IN-01**
```
As the System,
I want to reserve stock when an order is placed,
So that two customers cannot simultaneously purchase the last available item.

context: Inventory
priority: Must
type: functional
estimate: 5 points
tags: stock-reservation, concurrency
```

**US-IN-02**
```
As the System,
I want to release reserved stock when an order is cancelled or payment fails,
So that the stock becomes available for other customers.

context: Inventory
priority: Must
type: functional
estimate: 3 points
tags: stock-release, saga-compensation
```

**US-IN-03**
```
As an Inventory Manager,
I want to replenish stock for a SKU by entering received quantities,
So that the available stock reflects new deliveries.

context: Inventory
priority: Must
type: functional
estimate: 3 points
tags: replenishment, stock-management
```

**US-IN-04**
```
As an Inventory Manager,
I want to receive an alert when a SKU's stock falls below the reorder level,
So that I can initiate a replenishment order before stockout.

context: Inventory
priority: Must
type: functional
estimate: 3 points
tags: low-stock, alert
```

**US-IN-05**
```
As a Customer,
I want to see whether a product is in stock, low stock, or out of stock on the product page,
So that I know if I can purchase it before adding to cart.

context: Inventory
priority: Must
type: functional
estimate: 2 points
tags: stock-visibility, pdp
```

---

## EP-07: Notifications

**Goal:** Keep customers and admins informed of key events via their preferred channels.
**Context:** Notification | **Priority:** Should

---

**US-NT-01**
```
As a Customer,
I want to receive an email confirming my order has been placed,
So that I have a record of my purchase.

context: Notification
priority: Must
type: functional
estimate: 3 points
tags: transactional-email, order-confirmation
```

**US-NT-02**
```
As a Customer,
I want to receive a notification when my order is shipped with tracking details,
So that I know when to expect my delivery.

context: Notification
priority: Must
type: functional
estimate: 2 points
tags: shipping-notification, tracking
```

**US-NT-03**
```
As a Customer,
I want to control which types of notifications I receive and on which channel,
So that I am not overwhelmed with unwanted communications.

context: Notification
priority: Should
type: functional
estimate: 3 points
tags: preferences, opt-in, opt-out
```

**US-NT-04**
```
As a Customer,
I want to receive a reminder email if I abandoned my cart,
So that I can easily return and complete my purchase.

context: Notification
priority: Could
type: functional
estimate: 3 points
tags: cart-abandonment, re-engagement
```
