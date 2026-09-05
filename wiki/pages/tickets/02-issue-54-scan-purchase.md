---
created: 2026-08-13
type: ticket
tags: [scan-purchase, dashboard, widget, llm, issue-54]
related:
  - "../plans/05-scan-purchase-widget.md"
  - "../specs/01-dashboard-ux-improvements.md"
---

# 2. Ticket #54 — Scan Purchase Widget

## Summary

Implemented the **"Scan Purchase"** dashboard widget. The action button is
**enabled only while an LLM profile is active**; tapping it navigates to the
Shopping tab and **auto-opens the PurchaseDialog**, whose built-in Scan /
Gallery / PDF buttons provide the receipt-scanning entry point.

## What Was Implemented

- **New widget card** `ScanPurchaseWidgetCard.kt` — `ScanPurchaseWidget :
  DashboardWidget`, cloned from `QuickPurchaseWidgetCard`:
  - `DocumentScanner` icon in a primary `Surface`, title + full-width button
    using `widget_scan_purchase`.
  - `llmActive = preferencesManager.getActiveProfileId() != null` gates the
    button (`enabled`) and the card `onTap`.
- **Widget framework** (`data/DashboardWidget.kt`): new `SCAN_PURCHASE` enum
  value, `WidgetData.ScanPurchase`, factory mapping.
- **DashboardViewModel**: `SCAN_PURCHASE -> WidgetData.ScanPurchase` branch in
  `refreshWidgetData()`.
- **AddWidgetDialog**: Scan Purchase entry with `DocumentScanner` icon.
- **Dialog-trigger plumbing**:
  - Widget `createOnTap` sets `savedStateHandle["open_purchase_dialog"] = true`
    on the Shopping back-stack entry (try/catch fallback to the current entry),
    then navigates to `Screen.Shopping`.
  - `MainScreen` Shopping composable observes the flag on the target entry via
    `getStateFlow(...).collectAsStateWithLifecycle()` and additionally reads +
    clears the fallback flag from `navController.previousBackStackEntry
    ?.savedStateHandle` (covers the fresh-install first visit).
  - `ShoppingListsScreen` gained `openPurchaseDialog: Boolean = false` and
    `onOpenPurchaseDialogHandled: () -> Unit = {}`; a `LaunchedEffect` opens the
    dialog and acknowledges (one-shot).
- **Strings**: `widget_scan_purchase` → "Scan Purchase" (values/strings.xml).

## Deviations from Plan

None of consequence. The plan's navigation mechanism was validated during
implementation:
- `NavBackStackEntry.previousBackStackEntry` does **not** exist in navigation
  compose 2.8.9 — used `NavController.previousBackStackEntry` instead.
- First-visit fallback reads the Dashboard entry's flag via
  `navController.previousBackStackEntry` (the entry below Shopping), not
  `backStackEntry.previousBackStackEntry`.

## Decisions

- Inactive profile → disabled button + gated card tap; no hint text (matches
  existing widget-label precedent, minimal scope).
- English-only string (existing `widget_*` strings are untranslated in DE/UK).
- No DB migration, no `ShoppingListViewModel` / `PurchaseDialog` changes.

## Files Touched

- **New**: `ui/components/dashboard/widgets/ScanPurchaseWidgetCard.kt`
- **Modify**: `data/DashboardWidget.kt`
- **Modify**: `ui/viewmodel/DashboardViewModel.kt`
- **Modify**: `ui/components/dashboard/AddWidgetDialog.kt`
- **Modify**: `ui/components/main/MainScreen.kt`
- **Modify**: `ui/components/shoppinglist/ShoppingListsScreen.kt`
- **Modify**: `res/values/strings.xml`

## Verification

- `./gradlew compileDebugKotlin` — clean
- `./gradlew assembleDebug` — APK built (`app-debug.apk`)
- Manual UI checks pending (button gating, dialog open on first visit)

## Related

- Plan: [plans/05-scan-purchase-widget](../plans/05-scan-purchase-widget.md)
- Epic spec: [specs/01-dashboard-ux-improvements](../specs/01-dashboard-ux-improvements.md)
- GitHub: [rykhalskyi/byebyemoneylist#54](https://github.com/rykhalskyi/byebyemoneylist/issues/54)
