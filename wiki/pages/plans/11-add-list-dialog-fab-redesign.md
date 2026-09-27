---
created: 2026-09-27
type: plan
status: implemented
tags: [shopping-list, fab, dialog, cleanup, clipboard]
related:
  - "10-to-buy-list-redesign.md"
---

# 11. Add List Dialog & SpeedDialFab Redesign — Implementation Plan

Implements `wiki/raw/5-shopping-list-redesign-2.md`: collapse the three "create" FAB actions
(To Buy, Subscription, Income) into a single **Add** action that opens one tabbed **AddListDialog**,
keep **Purchase** unchanged, and delete the clipboard import / list-sharing feature.

Related: [10. To Buy List Redesign](10-to-buy-list-redesign.md) (introduced the To Buy list, the
current 4-action FAB and the separate create dialogs this plan consolidates).

## Summary

| Before | After |
|---|---|
| `SpeedDialFab` = Income, Subscription, To Buy, Purchase | `SpeedDialFab` = **Add**, Purchase |
| Add → `CreateIncomeDialog` | Add → **`AddListDialog`** with tabs **To Buy** / **Subscription** / **Income** |
| Subscription → `CreateShoppingListDialog(isSubscription=true)` | Subscription tab: name + all categories + interval selectbox (default Monthly) |
| To Buy → `createToBuyList()` directly from FAB | To Buy tab → `createToBuyList()` |
| Clipboard import + `SharedListDto` ACTION_SEND share | Removed |

## Decisions (confirmed with requester)

1. **Subscription categories = all categories** (no filtering). Categories have no `isSubscription`
   flag; only products do, so no DB change is introduced.
2. **To Buy tab = no fields**; confirming creates a new `To Buy dd.MM.yyyy` list (same repository call
   as today's FAB action). No name input.
3. **Clipboard removal covers all list sharing**: the clipboard import flow **and** the `SharedListDto`
   `ACTION_SEND` "Share List" menu item are removed. The **folder-based Shared Lists** feature
   (`isShared`, "Share"/"Unshare" toggle, `SyncFolderRepository`, `ListSyncEngine`) is a separate
   cloud-share feature and is **kept** (not a clipboard concern).

## Target Files

**Modified**
- `SpeedDialFab.kt` — 2 actions.
- `ShoppingListsScreen.kt` — single `showAddListDialog` state, wire callbacks, remove clipboard/import/share code.
- `ShoppingListCard.kt` — drop `onShareList` param + "Share List" menu item.
- `CreateShoppingListDialog.kt` — drop `onImportFromClipboard` param + "From Clipboard" button.
- `ShoppingListViewModel.kt` — drop `importSharedList`; make `RecurringPeriodSelector` reusable if needed.
- `strings.xml`, `values-uk/strings.xml`, `values-de/strings.xml` — add `add_list`; remove share/import strings.
- `SpeedDialFabTest.kt` — update to the 2-action API.

**New**
- `ui/components/shoppinglist/AddListDialog.kt`
- `app/src/androidTest/.../AddListDialogTest.kt` (optional but recommended)

**Deleted**
- `data/SharedListDto.kt`

---

## Phases

### Phase 1: SpeedDialFab (2 actions)
- [x] Change signature to `SpeedDialFab(onAdd: () -> Unit = {}, onPurchase: () -> Unit = {}, modifier)`.
- [x] Remove `onCreateToBuy`, `onCreateSubscription`, `onCreateIncome`.
- [x] Render two `SpeedDialAction`s: **Add** (label `R.string.add_list`, `Icons.Default.Add`, index 0)
      and **Purchase** (`Icons.Default.ShoppingCart`, index 1). Purchase behavior unchanged.
- [x] Remove now-unused imports (`ArrowUpward`, `CalendarMonth`, `ContentPaste`).
- [x] Update `SpeedDialFabTest`: hidden-until-open; Add fires `onAdd`; Purchase fires `onPurchase`.

### Phase 2: AddListDialog (new)
- [x] New `AddListDialog(categories, onDismiss, onCreateToBuy, onCreateSubscription, onCreateIncome, initialTab = TO_BUY)`.
- [x] Top selector = 3 tab-like buttons (`SingleChoiceSegmentedButtonRow` or `TabRow`):
      **To Buy** accented/default-selected, **Subscription**, **Income**. Switching tabs swaps the
      body fields while the dialog stays open.
- [x] **To Buy tab**: no fields, short hint; primary confirm button (accented) → `onCreateToBuy()` +
      dismiss. One tap creates the list (no name input).
- [x] **Subscription tab**: name field; `CategoryChipsField` over **all** `categories`; interval
      selectbox (WEEK / MONTH / YEAR) **defaulting to `MONTH`**; recurring forced `true`,
      `isForwardEmpty = false`. Confirm → `onCreateSubscription(name, categoryIds, interval)`.
- [x] **Income tab**: fields identical to today's `CreateIncomeDialog` — name, income categories
      (`categories.filter { it.isIncome }`), recurring checkbox, period selector, start-empty.
      Confirm → `onCreateIncome(...)`.
- [x] Extract the shared period dropdown: reuse `RecurringPeriodSelector` (currently private in
      `CreateIncomeDialog.kt`) / `RecurringSettingsSection` (in `CreateShoppingListDialog.kt`) so the
      Subscription and Income tabs share it instead of duplicating.
- [x] Validate: name required for Subscription/Income; confirm disabled when blank.

### Phase 3: Wire into ShoppingListsScreen
- [x] Replace `showCreateSubscriptionDialog` / `showCreateIncomeDialog` with a single
      `var showAddListDialog`.
- [x] FAB: `onAdd = { showAddListDialog = true }`, `onPurchase = { showPurchaseDialog = true }`.
- [x] `AddListDialog` callbacks:
  - `onCreateToBuy = { viewModel.createToBuyList(); showAddListDialog = false }`
  - `onCreateSubscription = { name, ids, period -> viewModel.createList(name, ids, "", isRecurring = true, recurringPeriod = period, isForwardEmpty = false, isSubscription = true, isIncome = false); showAddListDialog = false }`
  - `onCreateIncome = { name, ids, isRecurring, period, forwardEmpty -> viewModel.createList(name, ids, "", isRecurring, period, forwardEmpty, isSubscription = false, isIncome = true); showAddListDialog = false }`
- [x] Remove the two old `showCreateSubscriptionDialog` / `showCreateIncomeDialog` dialog blocks.
- [x] **Keep** the existing edit dialogs (`editingList` block, `CreateIncomeDialog` /
      `CreateShoppingListDialog`) so editing an existing list is unchanged.

### Phase 4: Remove clipboard import + list sharing
- [x] `ShoppingListsScreen.kt`: remove `checkClipboard()`, `showImportDialog`, `importCodePrefix`,
      the import `AlertDialog` block, `importSharedList` call, and the `onShareList` wiring
      (including the `SharedItemDto`/`SharedListDto` builder). Remove now-unused imports
      (`ClipboardManager`, `Context`, `Intent`, `SharedItemDto`, `SharedListDto`).
- [x] `CreateShoppingListDialog.kt`: remove `onImportFromClipboard` param, the "From Clipboard"
      dismiss button, and the `import_from_clipboard` usage.
- [x] `ShoppingListCard.kt`: remove `onShareList` param and the `R.string.share_list` menu item.
- [x] `ShoppingListViewModel.kt`: remove `importSharedList(...)` and the `SharedListDto` import.
- [x] Delete `data/SharedListDto.kt`.
- [x] Strings removed from all three locales: `share_list`, `import_code_prefix`,
      `dialog_import_title`, `dialog_import_message`, `dialog_import_confirm`,
      `dialog_import_success`, `dialog_import_error`, `import_from_clipboard`,
      `import_no_list_found`. **Keep** `shared_lists_*`, `share_toggle_*` (folder feature).
- [x] Strings added: `add_list` ("Add List" / "Додати список" / "Liste hinzufügen").

### Phase 5: Verification & Tests
- [x] `./gradlew testDebugUnitTest` (baseline 300 green).
- [x] `./gradlew assembleDebug`.
- [x] `./gradlew compileDebugAndroidTestKotlin`.
- [ ] Instrumented (device/emulator): `SpeedDialFabTest`, new `AddListDialogTest`
      (default tab = To Buy, Subscription shows interval default Monthly, Income shows income
      categories, each confirm fires the right callback). _Suites compile (`compileDebugAndroidTestKotlin`);
      not executed here (no device/emulator)._
- [ ] Manual smoke: Add → To Buy creates list; Add → Subscription creates recurring subscription;
      Add → Income creates income; Purchase unchanged; edit income/subscription list still works;
      no "From Clipboard"/"Share List" entry points remain. _Deferred to a device run._

## Risk Assessment

- **Tab semantics**: the note calls the three tab buttons "buttons"; this plan uses a short confirm
  button per tab rather than making tab selection destructive. Low risk, easy to adjust if the
  requester wants the To Buy tab tap itself to create.
- **Sharing scope**: removing `SharedListDto` is safe — it is referenced only by the import flow,
  the share menu item, and `ShoppingListViewModel.importSharedList`. The folder-based shared-lists
  code does not use it. Confirm the folder "Share List" toggle isn't also expected to go.
- **Dialog duplication**: Income tab vs `CreateIncomeDialog` (edit path) share field logic; extract
  a shared composable to avoid drift.
- **No DB migration** in this plan.

## Open Questions

- Should the legacy `CreateShoppingListDialog` subscription toggle stay for editing existing
  subscription lists (it is still reachable via the edit path), or be simplified to match the new
  dialog? This plan keeps it as-is.

## Updates
- [2026-09-27]: Implemented phases 1–4. Phase 1: `SpeedDialFab` reduced to `onAdd`/`onPurchase`.
  Phase 2: new `AddListDialog` (segmented To Buy / Subscription / Income tabs; To Buy accented and
  no fields; Subscription = name + all categories + interval default `MONTH`; Income = income
  categories + recurring/period/start-empty). Extracted shared `RecurringPeriodSelector.kt`; income
  edit dialog now reuses it. Phase 3: single `showAddListDialog` state wired to `createToBuyList()`
  / `createList(...)`; edit paths untouched. Phase 4: deleted `data/SharedListDto.kt`, removed
  `importSharedList`, `checkClipboard`, import dialog, `onShareList` card menu item, and the
  `onImportFromClipboard` dialog button; removed the share/import strings from en/uk/de and added
  `add_list`, `subscription_interval`, `new_to_buy_list_hint`. The folder-based Shared Lists feature
  (`isShared`/`onToggleSharing`/`SyncFolderRepository`/`ListSyncEngine`) was intentionally kept.
- [2026-09-27]: Verification — `./gradlew testDebugUnitTest` (300 tests) green, `assembleDebug` green,
  `compileDebugAndroidTestKotlin` green. Updated `SpeedDialFabTest` to the 2-action API and added
  `AddListDialogTest`. Instrumented suites and manual smoke require a device/emulator.
- [2026-09-27]: Follow-up fix — long tab labels (e.g. "Subscription"/localized) squeezed the
  segmented control. Added `Modifier.horizontalScroll(rememberScrollState())` to the
  `SingleChoiceSegmentedButtonRow` and `maxLines = 1, softWrap = false` on the tab labels so the
  tabs scroll horizontally instead of compressing/wrapping.
- [2026-09-27]: UX revision — horizontal scrolling still looked poor, so the three type selectors
  are now stacked vertically, one below another: a full-width filled `Button` (primary accent) for
  the selected type and full-width `OutlinedButton`s for the others. Removed the segmented-button
  imports. Resolves long-label layout breakage in all locales.

