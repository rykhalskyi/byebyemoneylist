---
created: 2026-09-24
type: plan
status: in-progress
summary: Implementation plan for To Buy list redesign (plain-text notes without prices/categories/products), dedicated card design, active state, and FAB updates.
---

# 10. To Buy List Redesign — Implementation Plan

Implements the clean separation between freeform planning notes (**To Buy**) and money-bearing records (**Purchases / Income / Subscriptions**) described in `wiki/raw/4-shopping-list-redesign.md`.

## Core Concepts

- **To Buy (`NEED_TO_BUY`)**:
  - Auto-generated name: `To Buy dd.MM.yyyy`.
  - Items are plain-text strings only (`productId = 0L`, `customName = name`, no price, quantity default 1.0).
  - No catalog product creation on adding items; picking a catalog suggestion copies only the name string.
  - No store, no category, no list-level or item-level prices.
  - Cannot be purchased or converted into a purchase record.
  - Active flag (`isActive`): Exactly one (the most recently created) To Buy list is `isActive = true`. Creating a new one deactivates previous ones.
  - Custom card design: No category stripe, no price box, highlights active state with distinct accent styling, single "Add item" button inside.
  - Excluded from monthly/annual expense totals.
- **Purchases (`PURCHASE`)**:
  - Finalized records (former `isFinished = true`), month-grouped, money-bearing, linked with store/categories.
- **Income & Subscriptions (`INCOME`, `SUBSCRIPTION`)**:
  - Money-bearing records; support recurrence.
- **Removed Concepts**:
  - `isNew` / New list editing mode.
  - `inStore` shopping mode.
  - `isArchived` / Archive mode.

---

## Phases

### Phase 1: Data Model & Database Migration 29 → 30
- [x] Add `ListKind` enum: `NEED_TO_BUY`, `PURCHASE`, `INCOME`, `SUBSCRIPTION`.
- [x] Update `ShoppingListEntity`:
  - Add `kind: String? = null` (with helper `listKind: ListKind`).
  - Add `isActive: Boolean = false`.
- [x] Write Room migration `29_TO_30`:
  - `ALTER TABLE shopping_lists ADD COLUMN kind TEXT`
  - `ALTER TABLE shopping_lists ADD COLUMN isActive INTEGER NOT NULL DEFAULT 0`
  - Backfill SQL:
    - `isIncome = 1` $\rightarrow$ `kind = 'INCOME'`
    - `isSubscription = 1` $\rightarrow$ `kind = 'SUBSCRIPTION'`
    - `isFinished = 1` $\rightarrow$ `kind = 'PURCHASE'`
    - `isFinished = 0` $\rightarrow$ `kind = 'NEED_TO_BUY'`
    - Set `isActive = 1` for the newest `NEED_TO_BUY` list.
  - Convert open items to pure text (`customName = COALESCE(customName, p.name)`, `productId = 0`, quantity `1.0`, price/discount `NULL`).
- [x] Update database version to 30 and export schema `30.json`.

### Phase 2: DAO & Repository Operations
- [x] `ShoppingListDao`:
  - Query for active To Buy list: `SELECT * FROM shopping_lists WHERE kind = 'NEED_TO_BUY' AND isActive = 1 LIMIT 1`.
  - Deactivate previous To Buy lists query: `UPDATE shopping_lists SET isActive = 0 WHERE kind = 'NEED_TO_BUY'`.
  - Filter out `NEED_TO_BUY` lists from `getFinishedListsInTimeRange`.
- [x] `ShoppingListRepository`:
  - `createToBuyList()`: creates a new list with title `"To Buy " + SimpleDateFormat("dd.MM.yyyy")`, `kind = NEED_TO_BUY`, `isActive = true`, deactivates prior To Buy lists.
  - `addToBuyItem(listId: Long, name: String)`: inserts item with `productId = 0L`, `customName = name`.
  - `processPurchase`: guard against processing a `NEED_TO_BUY` list directly.
  - Remove all remaining `inStore` and `isArchived` repository code.

### Phase 3: Add Item Flow for To Buy Lists
- [x] `AddProductViewModel` & `AddProductScreen`:
  - When `listKind == NEED_TO_BUY`:
    - Hide "Add to catalog" option.
    - Free text input $\rightarrow$ "Add to list" adds text directly as a plain-text item.
    - Clicking existing catalog suggestion $\rightarrow$ adds product name as plain text (`productId = 0L`, `customName = product.name`), skipping price input.
  - Barcode scan resolves to plain-text item name (no catalog link); LLM receipt import hidden for To Buy lists.

### Phase 4: Floating Action Button & Creation UX
- [ ] `SpeedDialFab`:
  - "To Buy" (or "Create List"): Directly invokes `createToBuyList()`.
  - "Add Subscription": Dedicated action opening subscription setup.
  - "Add Income": Opens income dialog.
  - "Purchase": Opens purchase flow.

### Phase 5: Card UI & List Screen
- [ ] `ShoppingListCard`:
  - Dedicated look for `kind == NEED_TO_BUY`:
    - Checklist / ShoppingCart icon in header.
    - Title: `"To Buy dd.MM.yyyy"`.
    - No category indicator bar.
    - No price badge.
    - No store / date subtitles.
    - Card container styling differentiates `isActive = true` (accent color/border) from `isActive = false`.
    - Simple checkbox item rows with item name; edit item name on tap, swipe to delete.
    - Action button: `"Add item"`.
    - Dropdown menu: Delete only.
- [ ] `ShoppingListsScreen`:
  - Status filter tabs: `All`, `To Buy`, `Purchases`, `Income`, `Subscriptions`.
  - Remove in-store mode and archive mode UI remnants.

### Phase 6: Totals, Sync & Cleanup
- [ ] Ensure `ShoppingList.calculateActualPrice`, `itemsTotal`, and expense aggregations skip `NEED_TO_BUY`.
- [ ] Check sync layer to ignore unmapped `NEED_TO_BUY` plain-text items.

### Phase 7: Verification & Tests
- [ ] Room migration test `29 -> 30`.
- [ ] Unit tests for `ShoppingListViewModel`, repository active list switches, and item insertions.
- [ ] Build and test verification (`./gradlew testDebugUnitTest`).

## Updates
- [2026-09-25]: Completed Phase 1. Added open-item-to-plain-text conversion and newest-`NEED_TO_BUY` activation to `MIGRATION_29_TO_30`; exported and committed `app/schemas/.../30.json`; extended `MigrationTest.migrate29To30` to assert item conversion and active flag. Also repaired the incomplete refactor bundled into the Phase 1 commit so the project compiles: restored deleted sync strings, removed duplicate `status_and_type`, dropped dead `inStore`/recurring/income filter code, fixed missing `listKind`/`Surface`/`CalendarMonth` imports, and updated `ShoppingListViewModelTest`/`PurchaseLogicTest` to the kind-based model. `./gradlew testDebugUnitTest` and `assembleDebug` pass (287 unit tests). Instrumented migration test requires a device/emulator.
- [2026-09-25]: Verified Phase 2 (DAO & Repository Operations). The queries (`getActiveToBuyList`, `deactivateAllToBuyLists`, `getFinishedListsInTimeRange` excludes `NEED_TO_BUY`), `createToBuyList()`, `addToBuyItem()` and the `processPurchase` guard were already present in the Phase 1 commit and are wired into the FAB (`ShoppingListsScreen`) and add-item screen; no `inStore`/`isArchived` repository code remains. Added `ToBuyListRepositoryTest` (JVM/Mockito: active switching, plain-text insertion, `NEED_TO_BUY` purchase guard) and three instrumented tests in `ShoppingListRepositoryTest` (active flag switch, plain-text persistence, time-range exclusion). `testDebugUnitTest` now 291 green.
- [2026-09-25]: Completed Phase 3 (Add Item Flow for To Buy Lists). The dialog already switched free text to "Add to list" and routed catalog suggestions to `addToBuyItem(name)`; closed the remaining invariant leaks: barcode scanning a known product now stores only the product name as plain text for To Buy lists (`AddProductViewModel.onBarcodeScanned`), and the LLM receipt-import button is hidden for To Buy lists (it would otherwise create catalog products/priced items). Added 4 tests to `AddProductViewModelTest` (To Buy flag, plain-text add, barcode→plain text, normal-list barcode still links catalog): 5/5 green; full `testDebugUnitTest` green.
