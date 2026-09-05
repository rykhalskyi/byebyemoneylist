---
created: 2026-08-13
type: plan
tags: [scan-purchase, dashboard, widget, llm, issue-54]
related:
  - "../tickets/02-issue-54-scan-purchase.md"
---

# 5. Scan Purchase Widget (Issue #54) — Implementation Plan

Focused implementation plan for **Issue #54 — Dashboard widget: Scan Purchase,
an LLM-gated shortcut to the PurchaseDialog**. Follows the established widget
framework from the Dashboard epic
([specs/01-dashboard-ux-improvements](../specs/01-dashboard-ux-improvements.md), [research/01-dashboard-epic-analysis](../research/01-dashboard-epic-analysis.md)).

## Summary

Add a new dashboard widget **"Scan Purchase"**:
- The card's action button is **enabled only while an LLM profile is active**
  (`PreferencesManager.getActiveProfileId() != null`).
- Tapping it navigates to the **Shopping tab** and **auto-opens the
  PurchaseDialog** — the dialog already contains the Scan / Gallery / PDF
  receipt-scanning affordances (`PurchaseDialog.kt:177-207`).

## Key Decisions

- **LLM-active gate**: `getActiveProfileId() != null`, the same definition used
  by `CompositeScanner`. In the closed-test build (`SILICON_FLOW_KEY` set) this
  is non-null unless the user explicitly deselects the profile.
- **Inactive behavior**: button rendered disabled; the card `onTap` is also
  gated (no-op) so both entry points behave consistently. No hint text.
- **Dialog trigger**: `savedStateHandle` flag `open_purchase_dialog`, mirroring
  the existing widget→Shopping communication
  (`CategoryWidgetCard.kt:173-189`). `showPurchaseDialog` stays local to
  `ShoppingListsScreen`; a new `openPurchaseDialog` param bridges it in.
- **First-visit edge case**: `getBackStackEntry(Screen.Shopping.route)` throws
  if Shopping was never visited (fresh install). Fallback writes the flag to the
  current (Dashboard) entry; `MainScreen` reads it back via
  `navController.previousBackStackEntry?.savedStateHandle`. Note
  `NavBackStackEntry.previousBackStackEntry` does NOT exist in navigation 2.8.9
  — only `NavController.previousBackStackEntry`.
- **Strings**: English only in `values/strings.xml`, matching the existing
  widget-label precedent (widget_* strings are untranslated in DE/UK).

## Step-by-Step Implementation

### Step 1: Create `ui/components/dashboard/widgets/ScanPurchaseWidgetCard.kt`
Clone the `QuickPurchaseWidgetCard` layout:
- `ElevatedCard` (primaryContainer), icon `Icons.Default.DocumentScanner` in a
  40dp primary `Surface`, title `widget_scan_purchase`, full-width `Button`
  with `enabled = llmActive`, `DragHandle` overlay.
- `llmActive` read from `(context.applicationContext as ByeByeMoneyApplication)
  .preferencesManager.getActiveProfileId() != null`.
- `createOnTap`: set `open_purchase_dialog` flag (targeted entry, try/catch
  fallback to current entry), then navigate to `Screen.Shopping` with the
  standard bottom-nav `popUpTo`/`launchSingleTop`/`restoreState` options.

### Step 2: Modify `data/DashboardWidget.kt`
- Add `SCAN_PURCHASE` to `DashboardWidgetType`.
- Add `object ScanPurchase : WidgetData()`.
- Add `SCAN_PURCHASE -> ScanPurchaseWidget(config)` to `createDashboardWidget`.

### Step 3: Modify `ui/viewmodel/DashboardViewModel.kt`
Add `DashboardWidgetType.SCAN_PURCHASE -> WidgetData.ScanPurchase` to
`refreshWidgetData()`.

### Step 4: Modify `ui/components/dashboard/AddWidgetDialog.kt`
Add `DashboardWidgetType.SCAN_PURCHASE to (stringResource(...widget_scan_purchase)
to Icons.Default.DocumentScanner)` to the `widgetTypes` list.

### Step 5: Modify `ui/components/main/MainScreen.kt`
Shopping composable now receives `backStackEntry`:
- Observe the target entry's `savedStateHandle.getStateFlow("open_purchase_dialog", false)`.
- `LaunchedEffect(Unit)` reads + clears `navController.previousBackStackEntry
  ?.savedStateHandle` flag (first-visit fallback).
- Pass `openPurchaseDialog = targetFlag || previousFlag` and
  `onOpenPurchaseDialogHandled = { backStackEntry.savedStateHandle[...] = false }`
  to `ShoppingListsScreen`.

### Step 6: Modify `ui/components/shoppinglist/ShoppingListsScreen.kt`
Add `openPurchaseDialog: Boolean = false` and `onOpenPurchaseDialogHandled:
() -> Unit = {}` params. A `LaunchedEffect(openPurchaseDialog)` sets
`showPurchaseDialog = true` then invokes the handled callback (one-shot).

### Step 7: String resources
`res/values/strings.xml`: `widget_scan_purchase` → "Scan Purchase".

## File Manifest

| # | File | Action |
|---|------|--------|
| 1 | `ui/components/dashboard/widgets/ScanPurchaseWidgetCard.kt` | **Create** |
| 2 | `data/DashboardWidget.kt` | Modify |
| 3 | `ui/viewmodel/DashboardViewModel.kt` | Modify |
| 4 | `ui/components/dashboard/AddWidgetDialog.kt` | Modify |
| 5 | `ui/components/main/MainScreen.kt` | Modify |
| 6 | `ui/components/shoppinglist/ShoppingListsScreen.kt` | Modify |
| 7 | `res/values/strings.xml` | Modify |

No DB migration. No changes to `ShoppingListViewModel` or `PurchaseDialog`.

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| First-visit: Shopping entry missing → `getBackStackEntry` throws | Medium | Fallback writes flag on current entry; `MainScreen` reads `navController.previousBackStackEntry` |
| Dialog reopening on later navigation | Medium | Flag cleared via `onOpenPurchaseDialogHandled` (target entry) and `remove` (previous entry); `LaunchedEffect` is keyed on the flag |
| `getActiveProfileId()` non-null default in closed-test build | Low | Matches `CompositeScanner` semantics; user can deselect profile |
| Disabled button looks non-obvious | Low | Accepted for v1; card tap gated too for consistency |

## Testing Checklist

- [ ] `./gradlew compileDebugKotlin` — builds cleanly
- [ ] `./gradlew assembleDebug` — APK builds
- [ ] Scan Purchase appears in the Add Widget dialog
- [ ] Button disabled with no active LLM profile; enabled when active
- [ ] Tap → Shopping tab with PurchaseDialog open (incl. fresh-install first visit)
- [ ] Dialog opens exactly once; not re-opened on later Shopping navigation
- [ ] Existing widgets and Quick Purchase flow unaffected

## Related

- Ticket: [tickets/02-issue-54-scan-purchase](../tickets/02-issue-54-scan-purchase.md)
- Epic spec: [specs/01-dashboard-ux-improvements](../specs/01-dashboard-ux-improvements.md)
- Epic research: [research/01-dashboard-epic-analysis](../research/01-dashboard-epic-analysis.md)
- GitHub: [rykhalskyi/byebyemoneylist#54](https://github.com/rykhalskyi/byebyemoneylist/issues/54)
