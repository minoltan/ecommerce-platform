# ADR-0001: Monetary Value Representation — Integer Minor Units

**Status:** Accepted
**Date:** 2026-06-10
**Phase:** ARCH (decision applies unchanged to Phase 1 and Phase 2)
**Bounded contexts affected:** Payment, Order, Cart, Product Catalog, Inventory (anywhere a price/amount is stored or computed)

---

## Context

Every bounded context that touches money — product pricing (Catalog), cart totals and
discounts (Cart), order line totals (Order), and payment/refund amounts (Payment) —
must agree on a single representation for monetary values before any LLD or schema can
be written. This is currently an open hotspot:

- **H-PM-3** (event-storming.md, Priority 5 — Payment): "Floating-point rounding in
  monetary amounts. All amounts stored and computed in paise (integers only)." Flagged
  **High** severity and an explicit ADR candidate.
- The Payment aggregate's invariant `sum(all refunds for payment) ≤ capturedAmount`
  must hold exactly — any rounding drift between Order's computed total, Cart's
  snapshot total, and Payment's captured amount would silently corrupt this invariant.
- Cart computes `total = Σ(snapshotPrice × qty) − discount + tax` (event-storming.md,
  Priority 3). This arithmetic crosses three services (Cart → Order → Payment) and must
  produce byte-identical results in each, or the saga's compensating logic (refunds,
  partial cancellations) cannot reconcile amounts.
- The platform currently targets INR (Razorpay) but `system-context.md` OQ-SC-01 leaves
  the gateway/currency choice open for international expansion — the representation
  must not assume INR-only (paise = 2 decimal places) forever.

Floating-point types (`double`, `float`) are unsuitable: binary floating-point cannot
represent decimal fractions like 0.10 exactly, and repeated arithmetic (discounts, tax,
partial refunds) accumulates rounding error that is unacceptable for financial records
and audit (NFR-CONS / financial audit requirement noted in container-diagram.md §5,
30-day Kafka retention on `order.*`/`payment.*` for audit).

---

## Decision

**All monetary amounts are stored, transmitted, and computed as signed 64-bit integers
representing the value in the currency's smallest unit ("minor units")** — e.g., paise
for INR, cents for USD. A separate `currency` field (ISO 4217 code, e.g., `INR`, `USD`)
is stored alongside every amount.

Concretely:

- **Database columns:** `BIGINT` (e.g., `amount_minor_units BIGINT NOT NULL`,
  `currency CHAR(3) NOT NULL`) in every table holding a price, total, discount, tax, or
  refund amount (`product.price`, `cart_item.snapshot_price`, `order_line.unit_price`,
  `order.total`, `payment.amount`, `refund.amount`, etc.).
- **Java domain model:** a `Money` value object wrapping `long amountMinorUnits` and
  `Currency currency` (java.util.Currency for the minor-unit divisor lookup, e.g.,
  INR/USD → 2, JPY → 0). `Money` implements `add`, `subtract`, `multiply(int qty)` and
  `compareTo` using integer arithmetic only — no `BigDecimal`/`double` in the domain
  layer.
- **JSON wire format (REST + Kafka events):** amounts are serialized as integers (e.g.,
  `"totalMinorUnits": 150000` for ₹1,500.00), never as decimal strings or floats.
  Display formatting (`₹1,500.00`) is a presentation-layer concern only (frontend or a
  dedicated formatting utility), never persisted or transmitted.
- **Arithmetic rules:** all intermediate calculations (discounts, tax, line totals)
  happen in minor units using integer/long arithmetic. Percentage-based discounts and
  tax round to the nearest minor unit using a single, shared rounding function (HALF_UP)
  applied identically in Cart, Order, and Payment — implemented once as a shared utility
  to guarantee identical results across services.
- **Currency-specific minor unit count:** the `Money` value object derives the minor
  unit divisor (100 for INR/USD, 1 for JPY, etc.) from the ISO 4217 currency code via
  `java.util.Currency.getDefaultFractionDigits()` — not hardcoded to 2.

---

## Consequences

### Positive

- **Eliminates floating-point rounding entirely.** Integer arithmetic is exact;
  `sum(refunds) ≤ capturedAmount` (Payment's core invariant) can be checked with a
  simple `<=` on longs with no epsilon comparisons.
- **Cross-service consistency.** Cart, Order, and Payment computing the same total from
  the same inputs (snapshot price × qty − discount + tax) using the same shared
  rounding utility produces byte-identical results — critical for saga reconciliation
  (H-CT-1, H-OR-1).
- **Audit-safe.** Kafka events and DB rows store exact integer amounts; financial
  reconciliation and audit queries (`SUM(amount_minor_units)`) are exact, no
  cumulative drift over millions of rows.
- **Currency-agnostic from day one.** Storing the currency code alongside the integer
  amount means adding a second currency (per OQ-SC-01) requires no schema change —
  only the `Money` value object's divisor lookup changes per row.
- **`BIGINT` headroom.** A 64-bit signed integer supports amounts up to ~9.2 × 10¹⁸
  minor units — far beyond any realistic order total, with no overflow risk.

### Negative

- **Display conversion required everywhere.** Every UI/API consumer must divide by the
  currency's minor-unit divisor before display — this is pushed to the frontend /
  presentation layer and must be documented in the API spec (e.g., OpenAPI field
  description: "amount in minor units; divide by 10^currency.fractionDigits for
  display").
- **No native `DECIMAL` semantics in the DB.** Some ad-hoc SQL reports that a DBA might
  write expecting `DECIMAL` columns will instead see large integers (e.g., `150000`
  instead of `1500.00`) — mitigated by a documented naming convention
  (`*_minor_units` suffix on every such column) and a SQL view layer for reporting if
  needed later.
- **Rounding function must be shared and versioned.** If Cart, Order, and Payment each
  implement their own rounding logic (even both "correct" HALF_UP implementations),
  subtle differences (e.g., rounding at each line item vs. rounding the final total)
  can still diverge. This is mitigated by extracting a shared `MoneyMath` library
  published as a common Maven module (`common-money` in the multi-module build) and
  consumed by all three services — but requires discipline to avoid each service
  reimplementing it.

---

## Alternatives Rejected

### `DECIMAL(19,4)` columns + `java.math.BigDecimal`

Store amounts as `DECIMAL(19,4)` in MySQL and `BigDecimal` in Java.

Pros: Native decimal type, human-readable in DB tools, no manual divisor lookup.

Rejected because:
- `BigDecimal` arithmetic still requires explicit `RoundingMode` and `scale()` calls at
  every operation — the same "shared rounding discipline" problem exists, but with more
  ceremony (`BigDecimal.valueOf(...).setScale(2, RoundingMode.HALF_UP)` everywhere)
  rather than plain `long` arithmetic.
- JSON serialization of `BigDecimal`/`DECIMAL` is inconsistent across libraries
  (Jackson can serialize as a JSON number with trailing zeros stripped, e.g.,
  `1500` vs `1500.00`, depending on configuration) — integers serialize unambiguously.
- `DECIMAL(19,4)` hardcodes 4 decimal places, which doesn't generalise cleanly to
  zero-decimal currencies (JPY) without per-currency scale logic anyway — the
  currency-aware divisor problem doesn't go away, it just moves into `BigDecimal.scale`
  management.

### Floating-point (`double`/`float`) with rounding at display time only

Store and compute as `double`, round only when displaying to the user.

Rejected outright — this is the exact anti-pattern H-PM-3 was raised to prevent.
Repeated arithmetic across the Cart → Order → Payment saga (each a separate service,
separate process, separate rounding) would accumulate drift that breaks the
`sum(refunds) ≤ capturedAmount` invariant in ways that are extremely difficult to
reproduce and debug (a 1-paise discrepancy appearing only after specific discount/tax
combinations).

### Store amount as a decimal string (`"1500.00"`)

Store and transmit amounts as strings to avoid numeric type ambiguity entirely.

Rejected because:
- Pushes parsing/arithmetic burden onto every consumer (must `BigDecimal.parse` before
  any computation) — same rounding-discipline problem as the `BigDecimal` alternative,
  plus added parsing overhead.
- Loses the ability to do arithmetic in SQL (`SUM()`, `>=` comparisons) without casting.
- No advantage over integer minor units for the audit/exactness goal, while adding
  parsing complexity.
