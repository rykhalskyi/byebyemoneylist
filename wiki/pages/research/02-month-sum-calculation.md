---
created: 2026-08-14
type: research
tags: [spending, analytics, dashboard, shopping-list, sum, calculateActualPrice]
related:
  - "../project-overview.md"
  - "../plans/01-dashboard-ux-improvements.md"
---

# 2. Canonical Month Spending Sum

## Topic & Context

The app showed three *different* values for "current month expenses" on the same
device/database: **widget 1621**, **shopping list screen 1708**, **analytics 1698**.
The same figure was computed in three independent places, each with slightly
different semantics.

## Findings — where the three sums lived (and how they differed)

| Screen | Code location | Lists counted | Date field | Price rule applied |
|---|---|---|---|---|
| Widget | `DashboardRepository.getThisMonthSpending()` (`data/local/DashboardRepository.kt`) | finished only | `purchaseDate ?: createDate` | **No** — summed raw `itemPrice * qty - discount` |
| List screen | `ShoppingListViewModel.buildDisplayItems()` (`ui/viewmodel/ShoppingListViewModel.kt`) | **all** (incl. drafts) | `createDate` only | Yes (`calculateActualPrice`) |
| Analytics | `AnalyticsViewModel.loadAnalyticsData()` (`ui/viewmodel/AnalyticsViewModel.kt`) | finished only | `purchaseDate ?: createDate` | Yes (`calculateActualPrice`) |

Three semantic questions disagreed across the three implementations:

1. **Which lists count** — finished-only vs all lists.
2. **Which date counts** — `purchaseDate ?: createDate` vs `createDate`.
3. **Whether to apply the actual-price rule** — the widget ignored it, so it
   under-reported whenever `finalTotal` (the real amount paid) differed from the
   sum of itemized prices.

## Canonical semantics (decided)

1. **Finished lists only** — `isFinished == true` or `isIncome == true`.
2. **Attributed by `purchaseDate ?: createDate`** (i.e. `ShoppingList.sortDate`).
3. **Apply the actual-price rule** (`PURCHASE_PRICE` / `BIGGER_VALUE`) via
   `ShoppingList.calculateActualPrice(rule)`.

## Canonical formula

```
monthExpenses = Σ over non-income lists of abs( calculateActualPrice(rule) )
```

Where `calculateActualPrice(rule)` (defined in `data/ShoppingList.kt`) returns:

- `PURCHASE_PRICE` (default): `finalTotal` if non-zero, otherwise `itemsTotal`.
- `BIGGER_VALUE`: `maxOf(itemsTotal, finalTotal)`.

The result is sign-adjusted (`negative` for expenses, `positive` for income), so
the canonical sum takes the absolute value and excludes income lists.

## Centralized implementation

A single source of truth now lives in `data/SpendingCalculator.kt`:

```kotlin
fun sumExpenses(lists: List<ShoppingList>, rule: String): Double
fun sumExpenses(lists: List<ShoppingListEntity>, items: List<ShoppingListItemWithProduct>, rule: String): Double
fun sumExpenses(adjustedItems: List<AdjustedItem>): Double
```

All three overloads reduce to the same formula above. Consumers:

- **Widget** — `DashboardRepository.getSpentToday() / getThisMonthSpending() / getLastMonthSpending()`
  now call `sumExpenses(lists, items, preferencesManager.getActualPriceRule())`.
  `DashboardRepository` gained a `PreferencesManager` constructor dependency.
- **Analytics** — `currentTotal = sumExpenses(adjustedItems)`.
- **List screen** — `buildDisplayItems()` now groups by `sortDate`
  (`purchaseDate ?: createDate`), filters to finished lists, and uses
  `sumExpenses(..., rule)` for year/month header totals.

## Rule of thumb for future code

Any new place that needs a "spent in a period" number **must**:

1. Select finished (or income) lists within the target range, attributed by
   `purchaseDate ?: createDate` (the SQL in
   `ShoppingListDao.getFinishedListsInTimeRange` already encodes this).
2. Call `sumExpenses(...)` rather than summing item prices manually.

Do not re-derive the sum from raw `itemPrice * quantity - discount` — that skips
the user's actual-price rule and is the exact bug that produced the 1621 value.

## Recommended actions

- Consider replacing any remaining hand-rolled spending sums (e.g. per-category
  totals in `DashboardRepository.calculateCategoryTotal`) with the same
  `calculateActualPrice(rule)` rule for consistency.

## Updates

- [2026-08-15]: Centralized the "expand category to descendants" logic. Added a
  top-level `expandCategoryIds(Set<Long>, List<CategoryEntity>): Set<Long>` in
  `SpendingCalculator.kt` (next to `getAllDescendantIds`) and removed the
  duplicated expansions in `AnalyticsScreen`, `AgentQueryExecutor`, and
  `DashboardRepository`. Also extracted product aggregation/filtering to
  top-level functions in `ProductStatsCalculator.kt`
  (`computeProductAggregates`, `computeProductStats`, `filterProductStats`) and
  removed the unused `ProductStatsCalculator` class. The AnalyticsScreen product
  stats tab and the agent tools (`GET_TOP_PRODUCTS`, `GET_SPENT_BY_PRODUCT`) now
  share the same category filter and group by `productId` (not name), so agent
  product totals match Analytics product stats.
