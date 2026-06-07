# Use Case Diagram — Order

```mermaid
graph TD
    Customer(["👤 Customer"])
    Admin(["👤 Admin"])
    System(["⚙️ System"])

    subgraph UC_Order ["Order Bounded Context"]
        UC1["Place Order"]
        UC2["View Order History"]
        UC3["View Order Detail"]
        UC4["Cancel Order"]
        UC5["Request Return"]
        UC6["Track Order Status"]
        UC7["Admin: View All Orders"]
        UC8["Admin: Update Order Status"]
        UC9["Admin: Approve / Reject Return"]
        UC10["Fail Order (compensating)"]
    end

    PaymentContext(["💳 Payment Context"])
    InventoryContext(["📦 Inventory Context"])
    NotificationContext(["🔔 Notification Context"])

    Customer --> UC1
    Customer --> UC2
    Customer --> UC3
    Customer --> UC4
    Customer --> UC5
    Customer --> UC6

    Admin --> UC7
    Admin --> UC8
    Admin --> UC9

    System --> UC10

    UC1 -.->|publishes OrderPlaced| PaymentContext
    UC1 -.->|publishes OrderPlaced| InventoryContext
    UC4 -.->|publishes OrderCancelled| PaymentContext
    UC4 -.->|publishes OrderCancelled| InventoryContext
    UC8 -.->|publishes OrderStatusUpdated| NotificationContext
    UC9 -.->|publishes ReturnApproved| PaymentContext
    UC10 -.->|compensates: OrderFailed| InventoryContext
```

## Use Case Descriptions

| ID | Use Case | Primary Actor | Precondition | Postcondition |
|---|---|---|---|---|
| UC-OR-01 | Place Order | Customer | Cart checked out; items in stock | Order PENDING; payment initiated; stock reserved |
| UC-OR-02 | View Order History | Customer | Authenticated | Paginated list of own orders |
| UC-OR-03 | View Order Detail | Customer | Order belongs to customer | Full order with items, address, payment, timeline |
| UC-OR-04 | Cancel Order | Customer | Order in PENDING or CONFIRMED | Order CANCELLED; stock released; refund if paid |
| UC-OR-05 | Request Return | Customer | Order DELIVERED within 30 days | Return request submitted |
| UC-OR-06 | Track Order Status | Customer | Order exists | Current status and timeline shown |
| UC-OR-07 | Admin: View All Orders | Admin | Authenticated as ADMIN | Filtered, paginated order list |
| UC-OR-08 | Admin: Update Order Status | Admin | Valid state transition | Order status updated; customer notified |
| UC-OR-09 | Admin: Approve Return | Admin | Return request exists | ReturnApproved event; refund initiated |
| UC-OR-10 | Fail Order (system) | System | Payment failed or timeout | Order FAILED; stock released; customer notified |

## Order State Machine

```mermaid
stateDiagram-v2
    [*] --> PENDING : OrderPlaced
    PENDING --> CONFIRMED : PaymentAuthorised
    PENDING --> FAILED : PaymentFailed / Timeout
    CONFIRMED --> PROCESSING : Admin action
    CONFIRMED --> CANCELLED : Customer / Admin cancel
    PROCESSING --> SHIPPED : Admin ships
    PROCESSING --> CANCELLED : Admin cancel
    SHIPPED --> DELIVERED : Delivery confirmed
    DELIVERED --> [*]
    CANCELLED --> [*]
    FAILED --> [*]
```
