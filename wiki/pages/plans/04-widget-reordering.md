---
created: 2026-08-12
type: plan
tags: [dashboard, widgets, reorder, drag-and-drop, ux]
related:
  - "../plans/01-dashboard-ux-improvements.md"
---

# 4. Widget Reordering — Implementation Plan

## Overview

Add drag-and-drop reordering to dashboard widgets. Reuses the existing
`sh.calvin.reorderable:3.1.0` library (already used for shopping-list reordering)
with its grid API. No schema changes, no new dependencies. Order persistence
already exists via `order` in `DashboardWidgetConfig` stored in prefs JSON.

## Design Decisions

- **Drag handle, not whole-card drag**: all widget cards use
  `combinedClickable(onClick, onLongClick)` where long-press = delete widget.
  A dedicated `Icons.Default.DragHandle` in the card's top-end corner avoids
  the gesture conflict, mirroring `ShoppingListCard.kt:626`.
- **Interface extension**: `DashboardWidget.Card()` gains
  `dragHandleModifier: Modifier = Modifier` (default keeps old call sites working).

## Step-by-Step Plan

**Step 1** — `data/DashboardWidget.kt`
- Add `dragHandleModifier: Modifier` to the `Card()` interface.
- **No default value** — a `@Composable` interface method with a default
  parameter value generates a `Card$default` bridge in `ComposeDefaultImpls`
  that dispatches to the abstract method, but implementations only get the
  mask variant (`Card(..., Composer, int, int)`), producing
  `AbstractMethodError` at runtime (verified via `javap` + device).

**Step 2** — Four widget card files
- `CategoryWidgetCard.kt`, `SpentTodayWidgetCard.kt`, `ThisMonthWidgetCard.kt`,
  `QuickPurchaseWidgetCard.kt`: accept `dragHandleModifier`, render a small
  `DragHandle` icon aligned `TopEnd` inside a wrapping `Box`.
- Overrides must NOT re-specify the default (Kotlin restriction).

**Step 3** — `ui/viewmodel/DashboardViewModel.kt`
- Add `reorderWidgets(reorderedWidgets: List<DashboardWidget>)`:
  re-index `order` by list position, `saveWidgetConfigs(...)`, update
  `_uiState.widgets`.

**Step 4** — `ui/components/dashboard/DashboardScreen.kt`
- `rememberLazyGridState()` + `rememberReorderableLazyGridState` with a local
  `localWidgets` list synced from `uiState.widgets` when not dragging
  (`isAnyDragging` guard, same pattern as `ShoppingListsScreen.kt:308-315`).
- Wrap items in `ReorderableItem(state, key = config.id)`, animate elevation
  via `animateDpAsState` + `graphicsLayer { shadowElevation }`.
- `dragHandleModifier = Modifier.draggableHandle(onDragStarted/onDragStopped)`;
  on drag stop call `viewModel.reorderWidgets(localWidgets)`.

**Step 5** — Strings
- Add `reorder_widget` to `values/`, `values-de/`, `values-uk/`.

**Step 6** — Tests
- `DashboardViewModelTest`: verify `reorderWidgets` re-indexes + persists new
  order to PreferencesManager.

**Step 7** — Verification
- `./gradlew compileDebugKotlin`, `./gradlew testDebugUnitTest`, manual smoke
  test on device (drag reorders, long-press delete still works, order survives restart).

## File Manifest

| # | File | Action |
|---|------|--------|
| 1 | `data/DashboardWidget.kt` | Modify |
| 2 | `ui/components/dashboard/widgets/CategoryWidgetCard.kt` | Modify |
| 3 | `ui/components/dashboard/widgets/SpentTodayWidgetCard.kt` | Modify |
| 4 | `ui/components/dashboard/widgets/ThisMonthWidgetCard.kt` | Modify |
| 5 | `ui/components/dashboard/widgets/QuickPurchaseWidgetCard.kt` | Modify |
| 6 | `ui/viewmodel/DashboardViewModel.kt` | Modify |
| 7 | `ui/components/dashboard/DashboardScreen.kt` | Modify |
| 8 | `res/values*/strings.xml` | Modify |
| 9 | `app/src/test/.../DashboardViewModelTest.kt` | Modify |

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Drag handle overlays card content | Low | 20dp icon at top-end; cards have padding |
| Reorder flicker from concurrent DB refresh | Low | `isAnyDragging` guard prevents resync during drag |
| Long-press delete conflicts with drag | Medium | Handled via dedicated handle, not whole-card drag |

## Estimated Effort
~1 hour including tests.

## Updates
- [2026-08-12]: Created.
- [2026-08-12]: Crash fix — `Card()` interface must NOT have a default value for
  `dragHandleModifier`. Kotlin generates a `Card$default` bridge for `@Composable`
  interface methods with defaults that dispatches to the abstract method, but
  implementations only receive the mask variant → `AbstractMethodError` on
  QuickPurchase widget. Removed `= Modifier`; single call site passes it explicitly.
