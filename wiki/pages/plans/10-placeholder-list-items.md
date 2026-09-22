---
created: 2026-09-22
type: plan
tags: [shopping-list, placeholders, migration, sync]
related:
  - "../specs/03-placeholder-list-items.md"
---

# 10. Placeholder Shopping-List Items — Implementation Plan (Ticket 1)

## Scope
Ticket 1 (P1) of the epic in
[../specs/03-placeholder-list-items.md](../specs/03-placeholder-list-items.md):
the **placeholder item model + manual add/display**, no LLM.

In: `isPlaceholder`/`matchState` columns, migration 29→30, DAO/repo/domain
mapping, "Add as placeholder" in the Add-product screen, placeholder editing,
placeholder visual style, sync deferral guard, totals exclusion.
Out: scan-a-paper-list, LLM reconciliation, analog marking, server changes.

## Steps

### 1. Model + migration
- `ShoppingListItemEntity`: add `isPlaceholder: Boolean = false`,
  `matchState: String? = null`.
- `PurchaseItem`: add `isPlaceholder: Boolean = false`, `matchState: String? = null`.
- `AppDatabase`: `version = 30`; add `MIGRATION_29_TO_30`
  (`ALTER TABLE shopping_list_items ADD COLUMN isPlaceholder INTEGER NOT NULL DEFAULT 0`,
  `ADD COLUMN matchState TEXT`); register in `addMigrations(...)`; export `30.json`.

### 2. DAO + repository
- `ShoppingListItemWithProduct`: add `isPlaceholder: Boolean`.
- Both join queries (`getItemsWithProductForListsSync`, `getAllItemsWithProduct`)
  select `sli.isPlaceholder`.
- `ShoppingListRepository`: add `addPlaceholderItem(listId, name, quantity)`.
- `duplicateShoppingList`: reset `matchState = null` when copying.

### 3. Add as placeholder
- `AddProductViewModel.addPlaceholder(name, quantity, onComplete)`: insert
  `productId = 0L`, `customName = name`, `isPlaceholder = true`, no product/price.
- `AddProductScreen`: when the query is non-blank, add an "Add as placeholder"
  row next to "Create new"; open a small `PlaceholderInputDialog` (name + qty, no
  price) and call `addPlaceholder`.

### 4. Edit placeholder
- `EditPurchaseItemDialog`: when `item.isPlaceholder`, show an editable name field
  and hide the price/discount fields; extend `onConfirm` with `newName`.
- `ShoppingListViewModel.updatePurchaseItem`: accept `newName`; update `customName`.
- `ShoppingListsScreen`: pass `newName` through.

### 5. Display
- `ShoppingListViewModel` mapping: carry `isPlaceholder`/`matchState`.
- `ShoppingListCard`: render placeholder item names in italic / muted style so they
  are visually distinct from catalog items.

### 6. Sync
- `ShoppingListsSyncRepository.pushCreateList`: extend the deferral guard so
  placeholder items count as "pending" — a placeholder-only list is not pushed as
  an empty server list. Placeholder items are already skipped by
  `syncableItemDigest` (no product serverId).

### 7. Tests
- Unit: placeholder contributes 0 to `ShoppingList.itemsTotal`; model defaults.
- Instrumented (if runnable): DAO insert/read roundtrip keeps `isPlaceholder`.

## Files
- `data/local/entity/ShoppingListEntity.kt`
- `data/PurchaseItem.kt`
- `data/local/AppDatabase.kt`
- `data/local/dao/ShoppingListDao.kt`
- `data/local/repository/ShoppingListRepository.kt`
- `ui/viewmodel/AddProductViewModel.kt`
- `ui/viewmodel/ShoppingListViewModel.kt`
- `ui/components/product/AddProductScreen.kt`
- `ui/components/shared/PlaceholderInputDialog.kt` (new)
- `ui/components/product/EditPurchaseItemDialog.kt`
- `ui/components/shoppinglist/ShoppingListsScreen.kt`
- `ui/components/shoppinglist/ShoppingListCard.kt`
- `data/sync/ShoppingListsSyncRepository.kt`
- `res/values/strings.xml` (+ de/uk)
- `app/schemas/.../30.json` (generated)

## Risks
- Migration correctness; placeholder vs coupon (`productId == 0L`) conflation —
  mitigated by the explicit `isPlaceholder` flag.
- Sync: ensure placeholder-only lists are deferred, not pushed empty.

## Outcome
Implemented 2026-09-22. Placeholders are now first-class New-list items.

- **Model/migration**: `shopping_list_items.isPlaceholder` + `matchState` (Room
  `version = 30`, `MIGRATION_29_TO_30`, schema `30.json`); `PurchaseItem` mirrors
  both; `ShoppingListItemWithProduct.isPlaceholder` surfaced through both DAO joins.
- **Add flow**: `ShoppingListRepository.addPlaceholderItem` stores `productId = 0L`,
  `customName = text`, `isPlaceholder = true` (no product/price).
  `AddProductViewModel.addPlaceholder` inserts the item directly with quantity 1 —
  no dialog. For a non-blank query the **free-text "Add \"x\"" row is the
  default/primary** action; creating a catalog product is the explicit secondary
  "Add \"x\" to catalog" row. The term "placeholder" is never shown in the UI.
- **Edit**: `EditPurchaseItemDialog` shows an editable name field (no price/discount)
  for placeholders; `ShoppingListViewModel.updatePurchaseItem(newName)` persists it.
- **Display**: `ShoppingListCard` renders placeholder names italic/muted.
- **Sync**: `ShoppingListsSyncRepository.pushCreateList` defers lists whose only
  items are placeholders (no empty server list); placeholder items are already
  skipped by `syncableItemDigest`.
- **Totals**: placeholders add 0 to `itemsTotal` (null price), covered by a test.
- **Tests**: `PlaceholderItemTest` (totals + `addPlaceholderItem` insertion),
  `MigrationTest.migrate29To30`. `testDebugUnitTest` and
  `compileDebugAndroidTestKotlin` pass.

Caveats: instrumented tests (incl. `migrate29To30`) were compiled, not run (no
emulator). `lintDebug` fails on 41 pre-existing errors in untouched files; the new
files add no lint errors.
