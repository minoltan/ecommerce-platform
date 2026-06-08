# ADR-0001: Store All Monetary Values as BIGINT in Paise

**Status:** Accepted  
**Date:** 2026-06-08  
**Phase:** ARCH  
**Bounded contexts affected:** Order, Payment, Cart, Product Catalog  

---

## Context

The platform handles Indian Rupee (INR) amounts across product pricing, cart totals, order
totals, payment captures, and refunds. INR has 2 decimal places (1 ₹ = 100 paise).

Multiple representations are possible:

- `DECIMAL(12,2)` — stores ₹ and paise as a decimal number
- `BIGINT` — stores the amount in paise (smallest unit); ₹99.50 = `9950`
- `DOUBLE / FLOAT` — floating-point representation
- `VARCHAR` — store as string, parse at application layer

The choice affects: rounding correctness, JOIN/aggregation accuracy, PCI-DSS compliance
surface, and inter-service event contract design.

---

## Decision

All monetary values are stored as **`BIGINT` representing paise** (1/100 of a Rupee).

- Column naming convention: `amount_paise`, `price_paise`, `unit_price_paise`,
  `total_amount_paise` (suffix makes the unit self-documenting).
- Conversion to display currency (`₹`) happens **only** at the API response layer (Jackson
  serialiser / response DTO).
- Kafka event payloads carry `BIGINT` paise; the `Currency` field is always `"INR"` as a
  constant — not configurable in Phase 1.
- Arithmetic (discounts, tax, line-item sum) is performed in paise using integer maths;
  no `BigDecimal` division is applied inside the domain model.

---

## Consequences

### Positive

- **No floating-point rounding errors.** Integer arithmetic is exact; ₹0.01 is always
  `1`, never `0.009999...`.
- **Consistent across services.** All seven bounded contexts share the same wire format;
  no per-service currency handling logic.
- **Simpler DB aggregations.** `SUM(amount_paise)` returns an exact `BIGINT`; no
  `ROUND()` calls needed in SQL.
- **Audit-safe.** Paise values stored in ledger tables (payment_outbox, refunds) are
  immutable integers — no precision loss on replay.
- **PCI-DSS surface reduced.** Amount stored in our DB is an integer count of paise;
  no card number, no CVV, and the amount representation itself is unambiguous in any
  dispute resolution.

### Negative

- **Developer ergonomics.** Engineers must remember to divide by 100 when reading raw DB
  values during debugging. Mitigated by the `_paise` suffix convention and a shared
  `MoneyUtils.toPaise()` / `MoneyUtils.toRupees()` utility.
- **Max representable value.** `BIGINT` max ≈ 9.2 × 10¹⁸ paise ≈ ₹92 quadrillion —
  not a practical constraint for this platform.
- **Multi-currency not supported.** Hardcoding INR means currency conversion is out of
  scope. Acceptable for Phase 1; Phase 2 ADR would revisit if international expansion
  is planned.

---

## Alternatives Rejected

### `DECIMAL(12,2)`

Stores ₹ directly with two decimal places. Appears natural but introduces subtle risks:

- Some JDBC drivers silently convert `DECIMAL` to `double` under certain fetch modes,
  reintroducing floating-point errors.
- Aggregation across large result sets can accumulate rounding differences.
- The representation (₹ vs paise) is ambiguous without column naming discipline.

Rejected because `BIGINT` is strictly safer for financial arithmetic with no practical
downside at this scale.

### `DOUBLE` / `FLOAT`

Immediately rejected. IEEE 754 floating-point cannot exactly represent many decimal
fractions (e.g., `0.1 + 0.2 ≠ 0.3`). Unacceptable for financial ledgers.

### `VARCHAR` / `String`

Storing `"99.50"` as a string avoids DB-layer precision loss but pushes the problem to
the application: every caller must parse, validate, and perform arithmetic on strings.
Sorting and aggregation are incorrect or require casting. Rejected.

### `BigDecimal` (Java) stored as `DECIMAL`

`java.math.BigDecimal` with `DECIMAL(19,4)` is the pattern used by many fintech
applications. Viable, but:

- Requires scale discipline (`setScale(2, HALF_UP)`) at every arithmetic operation.
- Four-decimal storage wastes space with no benefit for INR which only has 2 places.
- Adds complexity with no accuracy benefit over `BIGINT` paise for a single-currency
  system.

Rejected in favour of the simpler integer approach.
