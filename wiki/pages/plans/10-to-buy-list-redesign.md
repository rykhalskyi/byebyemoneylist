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
- [ ] Add `ListKind` enum: `NEED_TO_BUY`, `PURCHASE`, `INCOME`, `SUBSCRIPTION`.
- [ ] Update `ShoppingListEntity`:
  - Add `kind: String? = null` (with helper `listKind: ListKind`).
  - Add `isActive: Boolean = false`.
- [ ] Write Room migration `29_TO_30`:
  - `ALTER TABLE shopping_lists ADD COLUMN kind TEXT`
  - `ALTER TABLE shopping_lists ADD COLUMN isActive INTEGER NOT NULL DEFAULT 0`
  - Backfill SQL:
    - `isIncome = 1` $\rightarrow$ `kind = 'INCOME'`
    - `isSubscription = 1` $\rightarrow$ `kind = 'SUBSCRIPTION'`
    - `isFinished = 1` $\rightarrow$ `kind = 'PURCHASE'`
    - `isFinished = 0` $\rightarrow$ `kind = 'NEED_TO_BUY'`
    - Set `isActive = 1` for the newest `NEED_TO_BUY` list.
  - Convert open items to pure text (`isPlaceholder` or `customName = COALESCE(customName, p.name)`, `productId = 0`).
- [ ] Update database version to 30 and export schema `30.json`.

### Phase 2: DAO & Repository Operations
- [ ] `ShoppingListDao`:
  - Query for active To Buy list: `SELECT * FROM shopping_lists WHERE kind = 'NEED_TO_BUY' AND isActive = 1 LIMIT 1`.
  - Deactivate previous To Buy lists query: `UPDATE shopping_lists SET isActive = 0 WHERE kind = 'NEED_TO_BUY'`.
  - Filter out `NEED_TO_BUY` lists from `getFinishedListsInTimeRange`.
- [ ] `ShoppingListRepository`:
  - `createToBuyList()`: creates a new list with title `"To Buy " + SimpleDateFormat("dd.MM.yyyy")`, `kind = NEED_TO_BUY`, `isActive = true`, deactivates prior To Buy lists.
  - `addToBuyItem(listId: Long, name: String)`: inserts item with `productId = 0L`, `customName = name`.
  - `processPurchase`: guard against processing a `NEED_TO_BUY` list directly.
  - Remove all remaining `inStore` and `isArchived` repository code.

### Phase 3: Add Item Flow for To Buy Lists
- [ ] `AddProductViewModel` & `AddProductScreen`:
  - When `listKind == NEED_TO_BUY`:
    - Hide "Add to catalog" option.
    - Free text input $\rightarrow$ "Add to list" adds text directly as a plain-text item.
    - Clicking existing catalog suggestion $\rightarrow$ adds product name as plain text (`productId = 0L`, `customName = product.name`), skipping price input.

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
