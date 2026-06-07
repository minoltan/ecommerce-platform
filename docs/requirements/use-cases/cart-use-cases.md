# Use Case Diagram — Cart

```mermaid
graph TD
    Guest(["👤 Guest"])
    Customer(["👤 Customer"])
    System(["⚙️ System (Timer)"])

    subgraph UC_Cart ["Cart Bounded Context"]
        UC1["View Cart"]
        UC2["Add Item to Cart"]
        UC3["Update Item Quantity"]
        UC4["Remove Item from Cart"]
        UC5["Apply Coupon"]
        UC6["Remove Coupon"]
        UC7["Merge Guest Cart on Login"]
        UC8["Initiate Checkout"]
        UC9["Clear Cart"]
        UC10["Abandon Cart (auto)"]

        UC8 -.->|includes| UC11["Validate Cart"]
    end

    CouponService(["🎟️ Coupon / Promo Service"])
    OrderContext(["📦 Order Context"])

    Guest --> UC1
    Guest --> UC2
    Guest --> UC3
    Guest --> UC4
    Guest --> UC7

    Customer --> UC1
    Customer --> UC2
    Customer --> UC3
    Customer --> UC4
    Customer --> UC5
    Customer --> UC6
    Customer --> UC8
    Customer --> UC9

    System --> UC10

    UC5 -.->|validates| CouponService
    UC8 -.->|triggers CartCheckedOut event| OrderContext
    UC10 -.->|publishes CartAbandoned event| UC10
```

## Use Case Descriptions

| ID | Use Case | Primary Actor | Precondition | Postcondition |
|---|---|---|---|---|
| UC-CT-01 | View Cart | Guest / Customer | — | Current cart summary with totals |
| UC-CT-02 | Add Item to Cart | Guest / Customer | Product PUBLISHED, in stock | Item added with price snapshot |
| UC-CT-03 | Update Item Quantity | Customer | Item in cart | Quantity updated; stock re-validated |
| UC-CT-04 | Remove Item from Cart | Customer | Item in cart | Item removed; totals recalculated |
| UC-CT-05 | Apply Coupon | Customer | Cart not empty | Discount applied if coupon valid |
| UC-CT-06 | Remove Coupon | Customer | Coupon applied | Discount removed; totals recalculated |
| UC-CT-07 | Merge Guest Cart on Login | Guest → Customer | Guest cart exists | Guest items merged into user cart |
| UC-CT-08 | Initiate Checkout | Customer | Cart not empty; all items in stock | CartCheckedOut event published |
| UC-CT-09 | Clear Cart | Customer | Cart exists | All items removed |
| UC-CT-10 | Abandon Cart | System | Cart inactive for 30 min | CartAbandoned event published |
