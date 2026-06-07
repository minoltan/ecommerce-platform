# RE-02: Functional Requirements

**Phase:** Requirement Engineering
**Standard:** DDD Bounded Context decomposition
**Status:** Draft v0.1

---

## BC-01: User / Auth

### FR-UA-001 — User Registration
The system shall allow a guest to register with email and password. On registration, a verification email shall be sent. The account shall remain inactive until email is verified.

### FR-UA-002 — Email Verification
The system shall send a time-limited (24h) verification link. Clicking the link shall activate the account and log the user in.

### FR-UA-003 — User Login
The system shall authenticate a registered, verified user with email and password and issue a JWT access token (15 min TTL) and a refresh token (7 days TTL).

### FR-UA-004 — Token Refresh
The system shall allow a client to exchange a valid refresh token for a new access token without re-authentication.

### FR-UA-005 — Logout
The system shall invalidate the refresh token on logout. All subsequent refresh requests with that token shall be rejected.

### FR-UA-006 — Password Reset
The system shall allow a user to request a password reset via email. The reset link shall expire after 1 hour. On reset, all active sessions shall be invalidated.

### FR-UA-007 — Profile Management
An authenticated user shall be able to view and update their display name, phone number, and delivery addresses. Email change shall require re-verification.

### FR-UA-008 — Account Deactivation
An admin shall be able to deactivate a user account. Deactivated accounts shall have all tokens revoked and shall not be able to log in.

### FR-UA-009 — Role-Based Access
The system shall support roles: `CUSTOMER`, `ADMIN`, `INVENTORY_MANAGER`. Role shall be embedded in the JWT claims. Protected endpoints shall enforce role-based authorisation.

---

## BC-02: Product Catalog

### FR-PC-001 — Product Creation
An admin shall be able to create a product with: name, description, category, base price, SKU, images, and attributes (size, colour, etc.). A product is created in `DRAFT` state.

### FR-PC-002 — Product Publishing
An admin shall be able to publish a draft product, making it visible to customers. Unpublishing shall hide it from all customer-facing views.

### FR-PC-003 — Product Variants
A product shall support multiple variants (e.g., size/colour combinations). Each variant shall have an independent SKU, price override, and stock linkage.

### FR-PC-004 — Category Management
An admin shall be able to create and organise a hierarchical category tree. A product shall belong to exactly one leaf-level category.

### FR-PC-005 — Product Search
Customers shall be able to search products by keyword, filter by category, price range, and attributes, and sort by price, relevance, or newest.

### FR-PC-006 — Price Update
An admin shall be able to update a product's price. The previous price shall be retained for display (was/now pricing). Cart line items snapshot the price at time of add.

### FR-PC-007 — Product Detail
Customers shall be able to view full product detail: images, description, attributes, available variants, stock status, and reviews (future phase).

### FR-PC-008 — Product Deletion
A product shall only be soft-deleted (marked `DELETED`). It shall be removed from search and hidden from customers but retained for order history integrity.

---

## BC-03: Cart

### FR-CT-001 — Cart Creation
The system shall create a cart automatically on a customer's first `AddItem` action. A guest cart shall be identified by a session token; a logged-in cart by `userId`.

### FR-CT-002 — Add / Update / Remove Items
A customer shall be able to add items, update quantities, and remove items from their cart. Quantity shall not exceed available stock.

### FR-CT-003 — Price Snapshot
When an item is added to cart, the current product price shall be snapshotted against the line item. If the price changes before checkout, the customer shall be warned.

### FR-CT-004 — Coupon Application
A customer shall be able to apply one coupon code per cart. The system shall validate the coupon (active, not expired, usage limit not exceeded) and apply the discount.

### FR-CT-005 — Cart Merger
When a guest with an active guest cart logs in, the guest cart items shall be merged into the user's existing cart (or become the user's cart if none exists).

### FR-CT-006 — Cart Expiry
An inactive cart shall expire after 30 days of inactivity. A cart with items shall publish a `CartAbandoned` event after 30 minutes of inactivity.

### FR-CT-007 — Cart Summary
The cart view shall display: line items with snapshots prices, subtotal, discount, taxes, estimated total, and a stock availability indicator per item.

### FR-CT-008 — Checkout Initiation
On checkout, the system shall validate: all items still in stock, prices have not changed, coupon still valid. If any check fails, the customer shall be notified before proceeding.

---

## BC-04: Order

### FR-OR-001 — Order Placement
On `CartCheckedOut`, an Order shall be created in `PENDING` state containing: line items with locked prices, delivery address, coupon applied, and calculated totals.

### FR-OR-002 — Order State Machine
Orders shall follow the state machine:
`PENDING` → `CONFIRMED` → `PROCESSING` → `SHIPPED` → `DELIVERED`
`PENDING` | `CONFIRMED` | `PROCESSING` → `CANCELLED`
`PENDING` → `FAILED`

### FR-OR-003 — Order Confirmation
Order shall move to `CONFIRMED` only after `PaymentAuthorised` is received. No manual confirmation by admin is required for standard orders.

### FR-OR-004 — Order Cancellation
A customer shall be able to cancel an order in `PENDING` or `CONFIRMED` state. Orders in `PROCESSING` or later shall require admin intervention. Cancellation shall trigger stock release and refund.

### FR-OR-005 — Order History
A customer shall be able to view their order history with status, items, totals, and tracking information (where available).

### FR-OR-006 — Order Detail
A customer shall be able to view full order detail including line items, applied discounts, shipping address, payment method (masked), and status timeline.

### FR-OR-007 — Returns
A customer shall be able to request a return within 30 days of delivery. Admin shall approve or reject. On approval, a refund shall be initiated.

### FR-OR-008 — Admin Order Management
An admin shall be able to view all orders, filter by status/date/customer, update order status, and add internal notes.

---

## BC-05: Payment

### FR-PM-001 — Payment Initiation
On `OrderPlaced`, the Payment service shall create a payment record in `INITIATED` state and call the payment gateway to create a payment session.

### FR-PM-002 — Payment Authorisation
The payment gateway shall redirect or notify on authorisation. The Payment service shall update the record to `AUTHORISED` and publish `PaymentAuthorised`.

### FR-PM-003 — Payment Capture
For card payments, capture shall occur immediately on authorisation. For pre-authorisation flows, capture shall occur on `OrderShipped`.

### FR-PM-004 — Payment Failure Handling
On payment failure or timeout (>3s), the Payment service shall publish `PaymentFailed`. The Order service shall transition the order to `FAILED` and Inventory shall release the reservation.

### FR-PM-005 — Webhook Idempotency
All payment gateway webhooks shall be processed idempotently using `transactionId` as a deduplication key. Duplicate webhooks shall be acknowledged without re-processing.

### FR-PM-006 — Refund
On `ReturnApproved` or `OrderCancelled` (post-payment), the Payment service shall initiate a refund via the gateway. Refund amount shall not exceed the captured amount.

### FR-PM-007 — Payment Methods
The system shall support: credit/debit card (tokenised), UPI (Phase 1), and buy-now-pay-later (Phase 2). No raw card data shall be stored.

### FR-PM-008 — Payment History
A customer shall be able to view their payment history per order: amount, method (masked), status, and refund status.

---

## BC-06: Inventory

### FR-IN-001 — Stock Reservation
On `OrderPlaced`, Inventory shall reserve the ordered quantity for each line item. If any item cannot be reserved, the service shall publish `StockReservationFailed`.

### FR-IN-002 — Stock Commitment
On `PaymentAuthorised`, Inventory shall commit the reserved stock (move from reserved → deducted from on-hand).

### FR-IN-003 — Stock Release
On `OrderCancelled` or `PaymentFailed`, Inventory shall release the reservation, restoring available quantity.

### FR-IN-004 — Stock Replenishment
An inventory manager shall be able to replenish stock by entering a received quantity. The system shall recalculate available stock and clear any `LowStockAlert` if the threshold is met.

### FR-IN-005 — Low Stock Alert
When available stock for a SKU falls below the configured reorder level, the system shall publish `LowStockAlertTriggered` and notify the inventory manager.

### FR-IN-006 — Out of Stock
When available stock reaches zero, `ProductOutOfStock` shall be published. The Catalog context shall unpublish the product variant.

### FR-IN-007 — Stock Visibility
The product detail page shall display availability status: `In Stock`, `Low Stock (< 5)`, `Out of Stock`. Exact quantities shall not be exposed to customers.

### FR-IN-008 — Manual Adjustment
An inventory manager shall be able to manually adjust stock (increase or decrease) with a mandatory reason code for audit purposes.

---

## BC-07: Notification

### FR-NT-001 — Event-Driven Dispatch
The Notification service shall consume domain events from all other contexts and dispatch the appropriate notification (email / SMS / push) based on the event type and user preferences.

### FR-NT-002 — User Notification Preferences
A user shall be able to configure which notification channels (email, SMS, push) they want for each notification type. Marketing notifications shall default to off.

### FR-NT-003 — Notification Templates
All notification content shall be driven by versioned templates. Templates shall support personalisation tokens (name, orderId, amount, etc.).

### FR-NT-004 — Retry on Failure
Failed notifications shall be retried up to 3 times with exponential back-off. After 3 failures, `NotificationFailed` shall be recorded and the event shall be moved to a dead-letter queue.

### FR-NT-005 — Notification Log
All sent notifications (success or failure) shall be logged with: recipient, channel, template used, timestamp, and delivery status.

### FR-NT-006 — Transactional vs Marketing
Transactional notifications (order confirmation, payment receipt) shall always be sent regardless of preference settings. Marketing notifications shall respect opt-in/opt-out.
