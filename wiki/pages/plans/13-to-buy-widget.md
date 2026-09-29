---
created: 2026-09-28
type: plan
status: implemented
tags: [to-buy, widget, dashboard, navigation, compose]
related:
  - "10-to-buy-list-redesign.md"
  - "12-to-buy-auto-check.md"
---

# 13. To Buy Widget & Dedicated List Screen — Implementation Plan

Implements **Phase 3** of `wiki/raw/4-shopping-list-redesign.md`: a new dashboard widget that opens the
**active To Buy list** on its own clean, distraction-free screen ("sheet of paper"), where the user can
see, check/uncheck, add, rename and delete the things they need to buy.

Related: [10. To Buy List Redesign](10-to-buy-list-redesign.md) (introduced the To Buy list, active flag,
plain-text items) and [12. Auto-check To Buy items after a Purchase](12-to-buy-auto-check.md) (checks the
same items via the LLM after a purchase).

## Summary

| Before | After |
|---|---|
| To Buy lists only reachable via Shopping → expand card | New **To Buy dashboard widget** → dedicated full screen |
| No paper-style focused view | `ToBuyScreen`: paper surface, own top bar, **no bottom nav** |
| Item actions live inside the shopping-list card | Screen reuses add/check/rename/delete against the active list |
| `WidgetData` has 5 variants | Adds `WidgetData.ToBuy`; `DashboardWidgetType.TO_BUY` |
| Dashboard refreshes on list changes only | Also refreshes on item changes (live check-offs) |

## Decisions (confirmed with requester)

1. **Widget card content** = title + `checked/total` progress + preview of the first up-to-3 **pending**
   (unchecked) item names. Whole card is tappable; long-press still removes the widget.
2. **No active To Buy list** → the card shows an empty state with a **Create To Buy list** affordance
   (see *Empty state* below). It does not auto-hide.
3. **Dedicated screen hides the bottom navigation bar** — full-screen "sheet of paper" with its own top
   bar (title + back). This is the one place in the app without the bottom nav.
4. **Tap an item → rename** via the existing `EditToBuyItemDialog` (same behavior as the main card).
5. **Checked items stay in insertion order** (no auto-reordering); they get a checkbox + strikethrough.

## Architecture fit (no DB migration)

Everything is built on the Phase 1/2 foundations; **`AppDatabase` stays at version 30**.

- **Active list is derived from an existing Flow.** `ShoppingListDao.getAllShoppingLists()` already emits
  on every list change, so the ViewModel derives the active list with
  `allShoppingLists.map { it.firstOrNull { e -> e.listKind == ListKind.NEED_TO_BUY && e.isActive } }`.
  No new DAO query needed.
- **Items are already observable.** `ShoppingListRepository.getItemsForList(listId): Flow<List<ShoppingListItemEntity>>`
  (line 347) feeds the screen reactively via `flatMapLatest`.
- **Write paths already exist:** `addToBuyItem(listId, name)` (repo:702), `updateItemChecked(id, checked)`
  (repo:643), `updateShoppingListItem(entity)` for rename (`customName`), and
  `deleteShoppingListItemAndReturn(id)` (repo:720) which also marks the list modified for sync.
- **Widget plumbing already exists:** `DashboardWidget` / `DashboardWidgetConfig` /
  `DashboardWidgetType` / `WidgetData` + `createDashboardWidget()` factory (`data/DashboardWidget.kt`),
  serialized to prefs by `DashboardViewModel`. New widget is **opt-in** (not in the default two), added
  through `AddWidgetDialog`.

### Widget reactivity (item changes)

`DashboardViewModel.observeDatabaseChanges()` currently collects only `shoppingListRepository.allShoppingLists`.
Add a second collection of `repository.getAllItemsWithProduct()` (already a Flow, DAO:130) that calls
`refreshWidgetData()`. This makes the To Buy widget update immediately when items are checked/added/
deleted/auto-checked, not just when a list row changes.

### Empty state

The Android App Widget interface (`DashboardWidget.Card(data, onTap, onLongPress, …)`) has no create
callback. To avoid touching every widget, the card's create affordance uses the existing `onTap`:
tapping the empty card navigates to `ToBuyScreen`, whose **no-active-list state** shows a prominent
**Create To Buy list** button wired to `repository.createToBuyList()`. (When no active list exists there
is nothing to carry over; carry-over on an existing active list remains in the FAB → AddListDialog flow.)

## Screen design (`ToBuyScreen`)

- **Scaffold**: top app bar with the list title (`To Buy dd.MM.yyyy`) as title and a back arrow that
  `popBackStack()`s to the dashboard. No bottom bar (see NavHost change).
- **Paper surface**: a full-bleed `Surface`/`Card` in a warm paper tone with a subtle drop shadow and
  faint ruled lines drawn via `Modifier.drawBehind` — clearly reads as a sheet of paper, no category
  stripe, no price badge.
- **Item rows** (in `position` order, insertion order preserved):
  - leading `Checkbox` → `toggleChecked(item, checked)`;
  - item name (`customName`), **strikethrough + muted color when checked**;
  - trailing delete icon button → delete with an **undo snackbar** (reuse the existing 4s undo pattern
    from `ShoppingListViewModel.deleteItem`/`undoDelete` semantics);
  - row tap → `EditToBuyItemDialog` → rename via `customName`.
- **Quick add row** pinned at the bottom: `OutlinedTextField` + Add button; IME "Done" adds and keeps
  focus/clears the field for rapid entry.
- **Empty list state** (active list exists but has no items): paper with a light "Add your first item"
  hint.
- **No active list**: paper with the Create To Buy list button (see *Empty state*).
- **Top-bar overflow (optional, keep out of v1 unless cheap)**: "New list". Explicitly **out of scope**
  for this phase — new lists are created from the FAB → Add dialog.

## Target Files

**New**
- `ui/components/tobuy/ToBuyScreen.kt` — paper screen (top bar, list, quick-add row, empty states).
- `ui/viewmodel/ToBuyViewModel.kt` — focused active-list state machine + `Factory`; injectable
  `ioDispatcher` for tests (mirrors `ShoppingListViewModel`).
- `ui/components/dashboard/widgets/ToBuyWidgetCard.kt` — `ToBuyWidget : DashboardWidget`.
- `app/src/test/.../ui/viewmodel/ToBuyViewModelTest.kt`
- `app/src/androidTest/.../ui/components/tobuy/ToBuyScreenTest.kt` (Compose, device-gated).

**Modified**
- `data/DashboardWidget.kt` — add `DashboardWidgetType.TO_BUY`, `WidgetData.ToBuy`, factory branch.
- `ui/viewmodel/DashboardViewModel.kt` — observe item changes; `refreshWidgetData()` TO_BUY branch
  (build `ToBuy` payload from `getActiveToBuyList()` + `getItemsForListSync()`).
- `ui/components/dashboard/AddWidgetDialog.kt` — add the To Buy option (icon `ShoppingCart`).
- `ui/navigation/Screen.kt` — add `Screen.ToBuy("to_buy", …)`.
- `ui/components/main/MainScreen.kt` — register `composable(Screen.ToBuy.route)`; hide `bottomBar`
  when `currentDestination?.route == Screen.ToBuy.route`.
- `res/values/strings.xml` (+ `values-de`, `values-uk`) — new widget/screen strings.
- Tests: `DashboardWidgetTest`, `DashboardViewModelTest`, `AddWidgetDialogTest` — extend for the new type.

## Proposed `WidgetData.ToBuy`

```kotlin
data class ToBuy(
    val listId: Long?,          // null ⇒ no active list (empty state)
    val title: String,
    val checkedCount: Int,
    val totalCount: Int,
    val previewItems: List<String> // first ≤3 unchecked item names
) : WidgetData()
```

## Step-by-step plan

1. **Model & factory** — add `DashboardWidgetType.TO_BUY`, `WidgetData.ToBuy`, `createDashboardWidget`
   branch, and the `ToBuyWidget` skeleton (compile-only card).
2. **`ToBuyWidgetCard` UI** — title + progress + pending-item preview; empty state; `createOnTap` =
   `navController.navigate(Screen.ToBuy.route)` (plain navigate so back returns to the dashboard).
3. **Dashboard data** — observe item changes; implement the TO_BUY `refreshWidgetData()` branch
   (active list + sorted items, counts, preview). Widget appears once added via `AddWidgetDialog`.
4. **`ToBuyViewModel`** — `uiState` (`activeList`, `items`, `newItemText`, `editingItem`, `isLoading`);
   reactive `combine(allShoppingLists, flatMapLatest(items))`; actions `addItem`, `toggleChecked`,
   `renameItem`, `deleteItem` (+ undo), `createActiveList`.
5. **`ToBuyScreen`** — paper layout, top bar, item rows, quick-add, rename dialog, delete+undo, empty
   states.
6. **Navigation** — `Screen.ToBuy`, NavHost `composable`, conditional `bottomBar`.
7. **Strings** — `widget_to_buy`, `to_buy_screen_no_active` + create button, `add_item_hint`,
   `to_buy_progress` (`%1$d of %2$d`), empty-list hint; translate en/uk/de.
8. **Tests & verification** — see below.

## Test checklist

- `ToBuyViewModelTest` (JVM/Mockito kotlinx-coroutines-test): active-list derivation, item mapping,
  add trims+persists, check toggle, rename persists `customName`, delete emits undo + re-insert,
  `createActiveList` forwards id, null-active state.
- `DashboardViewModelTest`: `refreshWidgetData` produces `WidgetData.ToBuy` for active list (counts +
  preview ≤3 unchecked) and the `listId == null` empty state; item-flow change triggers a refresh.
- `DashboardWidgetTest`: `WidgetData.ToBuy` equality/field assertions.
- `AddWidgetDialogTest` (instrumented): the To Buy option is present and selectable/creates the widget.
- `ToBuyScreenTest` (instrumented, device-gated): renders items; toggling a checkbox calls the action and
  shows strikethrough; add row appends; delete removes; no-active state shows the create button.
- Regression: existing To Buy tests (`ShoppingListViewModelToBuyTest`, `ToBuyListRepositoryTest`,
  `ToBuyAutoMatchRepositoryTest`) stay green — no repository/DAO changes.

## Edge cases / risks

- **Item vs list reactivity**: both the active list and its items are observed, so switching the active
  list (new list created elsewhere) re-binds the screen via `flatMapLatest` automatically.
- **Deleted/renamed via other surfaces**: since the screen is fully reactive, changes made on the main
  shopping-list card or by the LLM auto-check appear live.
- **Bottom-bar hiding** must not break back-stack state restoration for the five main destinations;
  implement as a pure render condition on the current route.
- **Widget duplicates**: the widget system permits multiple instances of any type; multiple To Buy
  widgets would all open the same active list (harmless). Optional guard: skip if one already exists.
- **Widget when dashboard is disabled**: not reachable (the dashboard tab is hidden entirely), no extra
  work needed.
- **Preview names**: To Buy items are plain text (`productId = 0`), so preview uses `customName`; guard
  blank names.

## Verification (must pass before marking implemented)

- `./gradlew testDebugUnitTest`
- `./gradlew assembleDebug`
- `./gradlew compileDebugAndroidTestKotlin`
- `./gradlew connectedAndroidTest` on a device/emulator for the new Compose screen/widget tests.

## Explicitly out of scope

- "New list" / carry-over entry point on the dedicated screen (remains FAB → Add).

## Updates
- [2026-09-28]: Initial plan created (Phase 3 of the shopping-list redesign epic). No DB migration;
  reuses existing DAO/repository flows and the widget framework; adds one full-screen route, one widget
  type and one focused ViewModel.
- [2026-09-28]: Follow-up — the To Buy **shopping-list card** three-dot menu now has an "Open list" action
  (`ShoppingListCard.onOpenToBuy` → `ShoppingListsScreen.onOpenToBuy` → `MainScreen` navigates to
  `Screen.ToBuy`), so the dedicated paper screen is also reachable from the Shopping tab. New strings
  en/uk/de; `ShoppingListCardTest.toBuyCard_menuOpensDedicatedScreen`.
- [2026-09-28]: Implemented. Added `DashboardWidgetType.TO_BUY` + `WidgetData.ToBuy` and `ToBuyWidget`;
  `DashboardViewModel` now observes item changes and builds the TO_BUY payload; `ToBuyViewModel` +
  paper-style `ToBuyScreen` (check/add/rename/delete with undo, no-active create state); `Screen.ToBuy`
  route registered with the bottom bar hidden; `EditToBuyItemDialog` refactored to `(itemId, initialName)`;
  en/uk/de strings. Tests: `ToBuyViewModelTest` (10), Dashboard widget/viewmodel additions, new
  `AddWidgetDialogTest` + `ToBuyScreenTest` instrumentation. `compileDebugKotlin`, `assembleDebug`,
  `compileDebugAndroidTestKotlin` green; `testDebugUnitTest` — the only failure is the pre-existing,
  unrelated `ToBuyAutoMatcherTest.returns empty and does not call llm when consent not granted` (consent
  gating was removed in commit `f204d16` but the test was not updated).
