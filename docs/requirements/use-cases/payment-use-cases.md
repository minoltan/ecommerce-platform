# Use Case Diagram — Payment

```mermaid
graph TD
    Customer(["👤 Customer"])
    System(["⚙️ System"])
    Gateway(["🏦 Payment Gateway (External)"])

    subgraph UC_Payment ["Payment Bounded Context"]
        UC1["Initiate Payment"]
        UC2["Authorise Payment"]
        UC3["Capture Payment"]
        UC4["Handle Payment Failure"]
        UC5["Process Gateway Webhook"]
        UC6["Initiate Refund"]
        UC7["View Payment Status"]
        UC8["View Payment History"]
    end

    OrderContext(["📦 Order Context"])
    NotificationContext(["🔔 Notification Context"])

    Customer --> UC7
    Customer --> UC8

    System --> UC1
    System --> UC5

    Gateway -.->|webhook callback| UC5

    UC1 -.->|calls gateway API| Gateway
    UC2 -.->|triggered by webhook AUTHORISED| UC5
    UC3 -.->|triggered by webhook CAPTURED| UC5
    UC4 -.->|triggered by webhook FAILED| UC5

    UC2 -.->|publishes PaymentAuthorised| OrderContext
    UC4 -.->|publishes PaymentFailed| OrderContext
    UC6 -.->|triggered by ReturnApproved / OrderCancelled| UC6
    UC6 -.->|calls gateway refund API| Gateway
    UC6 -.->|publishes RefundIssued| NotificationContext
```

## Use Case Descriptions

| ID | Use Case | Primary Actor | Precondition | Postcondition |
|---|---|---|---|---|
| UC-PM-01 | Initiate Payment | System | Order PENDING | Payment INITIATED; redirect URL returned to customer |
| UC-PM-02 | Authorise Payment | Gateway (webhook) | Payment INITIATED | Payment AUTHORISED; PaymentAuthorised event published |
| UC-PM-03 | Capture Payment | Gateway (webhook) | Payment AUTHORISED | Payment CAPTURED; fulfilment triggered |
| UC-PM-04 | Handle Payment Failure | Gateway (webhook) | Payment INITIATED | Payment FAILED; PaymentFailed event published |
| UC-PM-05 | Process Gateway Webhook | System | Valid HMAC signature | Idempotent state transition; duplicate webhooks ignored |
| UC-PM-06 | Initiate Refund | System | Payment CAPTURED | Refund INITIATED; gateway refund API called |
| UC-PM-07 | View Payment Status | Customer | Order exists | Current payment status (masked card, amount) |
| UC-PM-08 | View Payment History | Customer | Authenticated | List of payments and refunds per order |

## Payment State Machine

```mermaid
stateDiagram-v2
    [*] --> INITIATED : PaymentInitiated
    INITIATED --> AUTHORISED : Gateway webhook AUTHORISED
    INITIATED --> FAILED : Gateway webhook FAILED / Timeout
    AUTHORISED --> CAPTURED : Gateway webhook CAPTURED
    CAPTURED --> REFUND_INITIATED : RefundInitiated
    REFUND_INITIATED --> REFUNDED : Gateway webhook REFUNDED
    REFUND_INITIATED --> REFUND_FAILED : Gateway error
    FAILED --> [*]
    REFUNDED --> [*]
```
