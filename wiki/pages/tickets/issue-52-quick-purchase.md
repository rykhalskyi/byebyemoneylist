---
created: 2026-08-11
type: ticket
tags: [issue-52, quick-purchase, dashboard, ux, task-3]
related: [[specs/dashboard-ux-improvements]] [[plans/dashboard-ux-improvements]] [[plans/quick-purchase-flow]]
---

# Issue #52 — Dashboard UX: Streamlined Quick Purchase Flow

## Ticket Summary

Replace the current Quick Purchase widget behavior (which navigates to the
Shopping tab with `open_purchase` flag and opens the complex `PurchaseDialog`)
with a dedicated streamlined two-step flow: price+store entry → category
selection grid.

## Current Status

Task 1 (emoji icons, #50) and Task 2.a (empty list with category, phantom bug
fix, virtual product in analytics) are **complete**. Task 3 (this ticket) is
the remaining work.

## Dependencies Satisfied

| Dependency | Status |
|---|---|
| Task 1 — emoji on `CategoryEntity` for grid display | Done (commit `d55c0db`) |
| Task 2.a — `processPurchase()` accepts `categoryId` | Done (commit `414bd6e`) |
| Task 2.a — virtual "Quick Purchase" product in `SpendingCalculator` | Done (commit `414bd6e`) |
| Task 2.a — phantom `productId = 0L` bug removed | Done (commit `414bd6e`) |

## What Needs to Be Done

1. **Create** `data/PurchaseListNameGenerator.kt` — centralized list name generation
   (`{Store} {dd.MM.yy}` format with same-day duplicate counter)
2. **Create** `ui/viewmodel/QuickPurchaseViewModel.kt` — state management
3. **Create** `ui/components/dashboard/QuickPurchaseScreen.kt` — two-step UI
4. **Modify** `ui/navigation/Screen.kt` — add `quick_purchase` route
5. **Modify** `ui/components/main/MainScreen.kt` — add composable in `NavHost`
6. **Modify** `ui/components/dashboard/widgets/QuickPurchaseWidgetCard.kt` — change
   navigation target from Shopping tab to `quick_purchase` route
7. **Modify** `res/values/strings.xml` (+ DE, UK) — new strings

No DB migration, no new entities, no DAO changes — all infrastructure
(`processPurchase`, `categoryId` support, virtual product in analytics) already
exists.

## Key Decisions

- **No store confirmation dialog** — the user types the store name and it is
  auto-created by `processPurchase()` (same behavior as existing manual purchase).
  The PurchaseDialog's confirmation dialog is only needed there because of the
  multi-field form context.
- **No new ViewModel wiring in `MainScreen.kt`** — `QuickPurchaseScreen` creates
  its own `QuickPurchaseViewModel` via `viewModel(factory = ...)`, consistent
  with `ProductScreen` pattern.
- **No confirmation step after category tap** — immediate create as specified
  in the ticket.

## Files Touched

| # | File | Action |
|---|------|--------|
| 1 | `data/PurchaseListNameGenerator.kt` | Create |
| 2 | `ui/viewmodel/QuickPurchaseViewModel.kt` | Create |
| 3 | `ui/components/dashboard/QuickPurchaseScreen.kt` | Create |
| 4 | `ui/navigation/Screen.kt` | Modify |
| 5 | `ui/components/main/MainScreen.kt` | Modify |
| 6 | `ui/components/dashboard/widgets/QuickPurchaseWidgetCard.kt` | Modify |
| 7 | `res/values/strings.xml` | Modify |
| 8 | `res/values-de/strings.xml` | Modify |
| 9 | `res/values-uk/strings.xml` | Modify |

## See Also

- Focused plan: [[plans/quick-purchase-flow]]
- Epic spec: [[specs/dashboard-ux-improvements]]
- Epic plan: [[plans/dashboard-ux-improvements]]
- Epic research: [[research/dashboard-epic-analysis]]
- GitHub: [rykhalskyi/byebyemoneylist#52](https://github.com/rykhalskyi/byebyemoneylist/issues/52)
