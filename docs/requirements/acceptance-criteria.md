# RE-05: Acceptance Criteria — Given / When / Then

**Phase:** Requirement Engineering
**Scope:** Critical user stories across all bounded contexts
**Status:** Draft v0.1

---

## US-UA-01 — User Registration

**Story:** As a Guest, I want to register with my email and password.

```
Scenario: Successful registration
  Given a guest provides a valid email not already registered
  And a password meeting the minimum policy (8 chars, 1 uppercase, 1 number)
  When the guest submits the registration form
  Then the system creates an account in UNVERIFIED state
  And sends a verification email to the provided address
  And returns HTTP 201 with the userId (no token issued yet)

Scenario: Registration with duplicate email
  Given an account already exists for the provided email
  When the guest submits the registration form
  Then the system returns HTTP 409
  And the response body contains error code USER_EMAIL_CONFLICT
  And no new account is created
  And no email is sent

Scenario: Registration with invalid password
  Given the guest provides a password shorter than 8 characters
  When the guest submits the registration form
  Then the system returns HTTP 422
  And the response body lists the specific validation failures
  And no account is created

Scenario: Registration with malformed email
  Given the guest provides "notanemail" as the email address
  When the guest submits the registration form
  Then the system returns HTTP 422
  And the response body contains error code INVALID_EMAIL_FORMAT
```

---

## US-UA-03 — User Login

**Story:** As a Verified User, I want to log in with my email and password.

```
Scenario: Successful login
  Given a verified user provides correct email and password
  When the user submits the login request
  Then the system returns HTTP 200
  And the response body contains a JWT access token (TTL 15 min)
  And the response body contains a refresh token (TTL 7 days)
  And a UserLoggedIn event is published

Scenario: Login with incorrect password
  Given a user provides a correct email but wrong password
  When the user submits the login request
  Then the system returns HTTP 401
  And the response body contains error code INVALID_CREDENTIALS
  And no token is issued

Scenario: Login with unverified account
  Given a registered but unverified user submits login
  When the user submits the login request
  Then the system returns HTTP 403
  And the response body contains error code EMAIL_NOT_VERIFIED
  And a hint to resend the verification email is included

Scenario: Login for deactivated account
  Given an admin has deactivated the user's account
  When the user submits the login request
  Then the system returns HTTP 403
  And the response body contains error code ACCOUNT_DEACTIVATED
```

---

## US-UA-05 — Password Reset

**Story:** As an Authenticated User, I want to reset my password via email.

```
Scenario: Successful password reset request
  Given a user provides a valid registered email
  When the user submits a password reset request
  Then the system returns HTTP 200 (regardless of whether the email exists)
  And if the email exists, a reset link with a 1-hour token is sent
  And a PasswordResetRequested event is published

Scenario: Password reset with valid token
  Given the user clicks a reset link with a valid, unexpired token
  And provides a new password meeting the policy
  When the user submits the new password
  Then the system updates the password
  And invalidates all existing refresh tokens for the user
  And returns HTTP 200
  And a PasswordResetCompleted event is published

Scenario: Password reset with expired token
  Given the user clicks a reset link with a token older than 1 hour
  When the user submits the new password
  Then the system returns HTTP 400
  And the response body contains error code RESET_TOKEN_EXPIRED
  And the password is not changed
```

---

## US-PC-03 — Product Search

**Story:** As a Customer, I want to search for products by keyword.

```
Scenario: Search returns matching products
  Given at least 3 published products match the keyword "wireless headphones"
  When the customer searches for "wireless headphones"
  Then the system returns HTTP 200
  And the response contains a paginated list of matching products
  And each result includes: productId, name, price, thumbnail, stock status
  And results are sorted by relevance by default

Scenario: Search returns no results
  Given no published products match the keyword "xyzabc123"
  When the customer searches for "xyzabc123"
  Then the system returns HTTP 200
  And the response contains an empty results array
  And the response includes a suggestions field (empty or with alternatives)

Scenario: Search filters by category and price range
  Given products in category "Electronics" with prices from ₹500 to ₹50,000
  When the customer searches with filters: category=Electronics, minPrice=1000, maxPrice=5000
  Then the system returns only published products within those constraints
  And no products outside the price range or category are returned

Scenario: Search performance
  Given the product catalog has 100,000 published products
  When the customer submits a keyword search
  Then the system responds within 200ms at p95
```

---

## US-CT-01 — Add Item to Cart

**Story:** As a Customer, I want to add a product to my cart.

```
Scenario: Authenticated user adds an in-stock item
  Given the customer is logged in
  And the product is published and has available stock ≥ 1
  When the customer adds the product (qty 1) to their cart
  Then the system creates or updates the cart for the user
  And adds the line item with the current price snapshotted
  And returns HTTP 200 with the updated cart summary
  And an ItemAddedToCart event is published

Scenario: Guest user adds an item (cart created)
  Given the customer is not logged in (session token provided)
  And the product is published and in stock
  When the customer adds the product to the cart
  Then the system creates a guest cart identified by the session token
  And adds the line item with snapshotted price
  And returns HTTP 200 with the cart summary

Scenario: Customer adds item with insufficient stock
  Given a product has 2 items in stock
  And the customer already has 2 of that item in their cart
  When the customer tries to add 1 more
  Then the system returns HTTP 422
  And the response contains error code INSUFFICIENT_STOCK
  And the cart is not modified

Scenario: Customer adds an unpublished product
  Given a product is in DRAFT or UNPUBLISHED state
  When the customer attempts to add it to cart
  Then the system returns HTTP 404
  And the cart is not modified
```

---

## US-OR-01 — Order Placement

**Story:** As a Customer, I want to place an order from my cart.

```
Scenario: Successful order placement
  Given the customer has a cart with 2 in-stock items
  And all items have sufficient stock available
  And the customer provides a valid delivery address
  When the customer submits the order
  Then the system creates an Order in PENDING state
  And publishes an OrderPlaced event
  And the Inventory service reserves stock for all line items
  And the Payment service initiates a payment session
  And returns HTTP 201 with orderId and payment redirect URL

Scenario: Order fails due to insufficient stock
  Given the customer has a cart with an item that has 0 available stock
  When the customer submits the order
  Then the system returns HTTP 422
  And the response identifies which item is unavailable
  And no Order is created
  And no payment is initiated
  And no stock is reserved

Scenario: Order fails due to payment timeout
  Given the customer submits a valid order
  And the Payment service does not respond within 3 seconds
  When the payment initiation times out
  Then the Order transitions to FAILED state
  And the Inventory service releases any reserved stock
  And the customer is notified via email of the failure
  And the system returns HTTP 503 to the caller

Scenario: Concurrent order causes stock conflict
  Given exactly 1 unit of a product is available
  And two customers simultaneously attempt to order that product
  When both orders are submitted at the same time
  Then exactly one order succeeds with stock reserved
  And the second order fails with HTTP 422 and INSUFFICIENT_STOCK
  And no overselling occurs

Scenario: Order with price-changed cart item
  Given a product's price was updated after the customer added it to cart
  When the customer submits the order
  Then the system creates the order with the ORIGINAL snapshotted price
  And includes a price-change warning in the response
```

---

## US-OR-04 — Order Cancellation

**Story:** As a Customer, I want to cancel an order that has not yet been shipped.

```
Scenario: Customer cancels a PENDING order (pre-payment)
  Given the customer has an order in PENDING state
  When the customer submits a cancellation request
  Then the system transitions the order to CANCELLED
  And publishes an OrderCancelled event
  And the Inventory service releases reserved stock
  And returns HTTP 200 with the updated order status
  And no payment refund is triggered (payment not yet captured)

Scenario: Customer cancels a CONFIRMED order (post-payment)
  Given the customer has an order in CONFIRMED state
  And payment has been captured
  When the customer submits a cancellation request
  Then the system transitions the order to CANCELLED
  And the Inventory service releases committed stock
  And the Payment service initiates a full refund
  And the customer is notified via email of the cancellation and refund

Scenario: Customer attempts to cancel a SHIPPED order
  Given the customer has an order in SHIPPED state
  When the customer submits a cancellation request
  Then the system returns HTTP 409
  And the response contains error code ORDER_NOT_CANCELLABLE
  And the order state is unchanged
  And a message suggests the customer file a return request instead

Scenario: Cancellation for non-existent order
  Given the customer provides an orderId that does not exist
  When the customer submits a cancellation request
  Then the system returns HTTP 404
  And the response contains error code ORDER_NOT_FOUND
```

---

## US-PM-01 — Payment with Card

**Story:** As a Customer, I want to pay for my order using a credit or debit card.

```
Scenario: Successful card payment
  Given the customer has an order in PENDING state
  And provides valid card details via the payment gateway
  When the payment gateway authorises the transaction
  Then the Payment service records the payment as AUTHORISED
  And publishes a PaymentAuthorised event
  And the Order transitions to CONFIRMED
  And the customer receives a payment confirmation email

Scenario: Card declined by gateway
  Given the customer provides a card that is declined by the gateway
  When the gateway returns a declined response
  Then the Payment service records the payment as FAILED
  And publishes a PaymentFailed event
  And the Order transitions to FAILED
  And the Inventory service releases reserved stock
  And the customer is notified via email with an option to retry

Scenario: Duplicate webhook from payment gateway
  Given a PaymentAuthorised webhook has already been processed for transactionId TXN-001
  When the gateway retries the same webhook for TXN-001
  Then the system acknowledges the webhook with HTTP 200
  And does not create a duplicate payment record
  And does not re-publish PaymentAuthorised
```

---

## US-IN-01 — Stock Reservation

**Story:** As the System, I want to reserve stock when an order is placed.

```
Scenario: Successful stock reservation
  Given an OrderPlaced event is received for 2 units of SKU-ABC
  And SKU-ABC has 5 available units
  When the Inventory service processes the event
  Then a StockReservation is created for 2 units
  And availableQuantity is decremented by 2 (from 5 to 3)
  And a StockReserved event is published

Scenario: Reservation fails due to insufficient stock
  Given an OrderPlaced event is received for 3 units of SKU-XYZ
  And SKU-XYZ has only 1 available unit
  When the Inventory service processes the event
  Then no reservation is created
  And a StockReservationFailed event is published
  And availableQuantity is not modified

Scenario: Reservation prevents negative stock under concurrency
  Given SKU-DEF has exactly 1 available unit
  And two OrderPlaced events arrive simultaneously for 1 unit each
  When both events are processed concurrently
  Then exactly one reservation succeeds
  And exactly one StockReservationFailed event is published
  And availableQuantity does not go below 0

Scenario: Reserved stock released on order cancellation
  Given an order has reserved 2 units of SKU-ABC
  And an OrderCancelled event is received
  When the Inventory service processes the event
  Then the StockReservation is removed
  And availableQuantity is incremented by 2
  And a StockReleased event is published
```

---

## US-NT-01 — Order Confirmation Email

**Story:** As a Customer, I want to receive an email confirming my order has been placed.

```
Scenario: Order confirmation email sent successfully
  Given an OrderConfirmed event is received for customer@example.com
  When the Notification service processes the event
  Then an email is queued using the order-confirmation template
  And the email includes: orderId, item list, total, estimated delivery date
  And the email is dispatched within 5 seconds of the event
  And a NotificationDelivered record is created

Scenario: Email delivery fails and is retried
  Given the email provider returns a 5xx error on first attempt
  When the Notification service retries the dispatch
  Then the system retries up to 3 times with exponential back-off
  And on the 3rd failure, a NotificationFailed event is recorded
  And the failed notification is moved to a dead-letter queue for investigation

Scenario: Notification suppressed by user preference
  Given a customer has opted out of marketing emails
  When a CartAbandoned event is received for that customer
  Then the Notification service does not send the abandonment email
  And a NotificationSuppressed record is created with reason PREFERENCE_OPT_OUT
  And transactional notifications (e.g., order confirmation) are still sent
```
