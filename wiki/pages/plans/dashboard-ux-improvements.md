---
created: 2026-08-09
type: plan
tags: [dashboard, quick-purchase, category-icons, default-product, ux, epic]
related: [[specs/dashboard-ux-improvements]] [[research/dashboard-epic-analysis]]
---

# Dashboard UX Improvements — Implementation Plan

## Overview
Three interconnected features implemented across ~22 files. One DB column added
in a single migration. Four new source files. No new DB tables. Task 2.a needs no
schema change (the virtual "Quick Purchase" product is synthesized at analytics
time). Widget deletion (long-press) is already implemented and requires no changes.

## Step-by-Step Plan

### Phase 1: Database Changes (Task 1)

**Step 1.1** — Update `CategoryEntity.kt`
- Add `val emoji: String? = null`

**Step 1.2** — DB migration in `AppDatabase.kt`
- Bump version to 21
- Add `MIGRATION_20_TO_21`:
  ```sql
  ALTER TABLE categories ADD COLUMN emoji TEXT;
  ```
- Note: Task 2.a (empty list with category) needs **no** schema change — the
  list category already lives in `ShoppingListCategoryCrossRef`; the virtual
  product is computed at analytics time, not stored.

### Phase 2: Category Emoji UI (Task 1)

**Step 2.1** — Create `data/CategoryEmoji.kt`
- Object with categorized emoji lists (8-10 categories, ~150 emojis total)
- Constants for each category group: Food, Transport, Home, Health, Shopping,
  Entertainment, Money, People, Nature, Other

**Step 2.2** — Update `CategoryDialog.kt`
- Add emoji picker section below color picker
- Emoji picker: dialog with tab row for categories, grid of emoji buttons
- Selected emoji shown above picker, with "Clear" button

**Step 2.3** — Update display points (show emoji everywhere color is shown)
- `CategoryPickerSheet.kt`: emoji + colored circle on each category item
- `ShoppingListCard.kt`: emoji in category indicator line
- `DashboardScreen.kt`: emoji on category widget cards
- `CategoryChipsField` (used in various screens): emoji on chips
- `CategoriesTab.kt`: emoji in category list items

### Phase 3: Empty List with Category (Task 2.a, supersedes default product)

**Step 3.1** — Add batch DAO query for list→category cross-refs
- Add to `ShoppingListDao.kt`:
  ```kotlin
  @Query("SELECT * FROM shopping_list_category_cross_ref WHERE shoppingListId IN (:listIds)")
  fun getCategoryCrossRefsForListsSync(listIds: List<Long>): List<ShoppingListCategoryCrossRef>
  ```
- Needed by `computeAdjustedItems()` to resolve category for empty lists
  (existing `getCategoriesForShoppingListSync` is single-list only)

**Step 3.2** — Update `ShoppingListRepository.processPurchase()`
- Add `categoryId: Long? = null` parameter
- When creating a **new** list (`ShoppingListRepository.kt:57-61`) and
  `categoryId != null`, pass `listOf(categoryId)` to `insertShoppingList()`
  (which already supports `categoryIds`):
  ```kotlin
  insertShoppingList(entity, listOf(categoryId))
  ```
- **Bug fix:** remove the phantom item insertion at `ShoppingListRepository.kt:74-76` —
  when `items.isEmpty()`, do **not** insert `ShoppingListItemEntity(productId = 0L)`
- When **finishing an existing list** (`listId != null`): preserve existing
  categories — do not overwrite them. Existing lists already have categories
  from `autoAssignListCategoryFromItems` or manual assignment
- Keep `autoAssignListCategoryFromItems()` for itemized lists (unchanged)

**Step 3.3** — Add mandatory category picker to PurchaseDialog (manual mode only)
- Category is mandatory **only for MANUAL mode** (no items, just price); scan
  mode with items auto-assigns via `autoAssignListCategoryFromItems`
- **`PurchaseDialogViewModel.kt`**: add `selectedCategoryId: Long?` state field
- **`PurchaseDialog.kt`**: add category picker UI (dropdown or `SmartSelectField`
  for categories) visible in `PurchaseMode.MANUAL` only:
  - Use existing `SmartSelectField` or `CategoryPickerSheet` pattern
  - Filter categories: exclude income categories
  - Save button disabled until a category is selected
- **`onConfirm` callback**: pass `categoryId: Long?` (null in scan mode)
- **Callers**: `ShoppingListsScreen.kt:670` and any other call sites pass
  the selected category through to `viewModel.processPurchase()`
- **`ShoppingListViewModel.processPurchase()` and `DashboardViewModel.processPurchase()`**:
  accept and forward `categoryId: Long?` to `repository.processPurchase()`

**Step 3.4** — Virtual "Quick Purchase" product in analytics
- Update `data/SpendingCalculator.kt` (`computeAdjustedItems`): after processing
  all lists and their items, iterate lists again — for each list that has
  **zero items** and a non-null `finalTotal`, load its category from the batch
  cross-refs (new DAO query from Step 3.1) and synthesize an `AdjustedItem`:
  - `productName = "Quick Purchase"`
  - `productId = 0L` (consistent sentinel; no real product)
  - `categoryId` = the list's category from `ShoppingListCategoryCrossRef`
  - `itemTotal` = `list.finalTotal`
  - `quantity = 1.0`, `isIncome = list.isIncome`
- The single `categoryId` is used (empty lists carry at most one category)
- This makes empty lists appear in product stats with correct category
  attribution, and removes `hasProductTotalMismatch` — the virtual item's
  `itemTotal` balances the `finalTotal` fallback at `AnalyticsViewModel.kt:312`

### Phase 4: Quick Purchase Flow (Task 3)

**Step 4.1** — Create `data/PurchaseListNameGenerator.kt`
```kotlin
class PurchaseListNameGenerator(private val database: AppDatabase) {
    suspend fun generate(date: Long = System.currentTimeMillis()): String {
        val dateStr = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
            .format(Date(date))
        val baseName = "Purchase $dateStr"
        val startOfDay = // compute start of day millis
        val endOfDay = // compute end of day millis
        val sameDayLists = database.shoppingListDao()
            .getFinishedListsInTimeRange(startOfDay, endOfDay)
        val sameDayCount = sameDayLists.count {
            it.name.startsWith(baseName)
        }
        return if (sameDayCount == 0) baseName
               else "$baseName ${sameDayCount + 1}"
    }
}
```

**Step 4.2** — Create `QuickPurchaseViewModel.kt`
- State: `price: String`, `storeText: String`, `categories: List<CategoryEntity>`,
  `selectedCategory: CategoryEntity?`, `isLoading: Boolean`,
  `error: String?`, `purchaseComplete: Boolean`
- Actions: `updatePrice()`, `updateStore()`, `selectCategory()`,
  `confirmStoreCreation()`
- On `selectCategory(category)`:
  1. Generate list name
  2. Resolve/create store (reuse existing `StoreRepository.getOrCreate` pattern)
  3. Create an **empty** list with the selected category
     (`ShoppingListCategoryCrossRef`) — no items, no phantom product
  4. Call `ShoppingListRepository.processPurchase()` (empty items; the
     phantom-item branch is removed)
  5. Set `purchaseComplete = true`
- Factory wired through `ByeByeMoneyApplication`

**Step 4.3** — Create `QuickPurchaseScreen.kt`
- Scaffold with TopAppBar "Quick Purchase" and back navigation
- Step 1 (price + store):
  - `OutlinedTextField` for price (numeric keyboard)
  - `SmartSelectField` for store (reuse existing component)
  - "Next" button (disabled if price is empty or invalid)
- Step 2 (category grid):
  - "Select Category" header
  - `LazyVerticalGrid(columns = GridCells.Fixed(3))`
  - Each cell: large `ElevatedCard` with emoji + colored background + name
  - Parent categories as full-width section headers via `GridItemSpan`
  - Filter: only `isIncome = false` categories
- Loading state with `CircularProgressIndicator`
- Snackbar on successful purchase creation

**Step 4.4** — Add navigation route
- `Screen.kt`: add `object QuickPurchase : Screen("quick_purchase", R.string.quick_purchase, Icons.Default.Add)`
  (not a bottom-nav tab; icon is placeholder)
- `MainScreen.kt`: add `composable("quick_purchase") { QuickPurchaseScreen(...) }`
  in the top-level NavHost, outside bottom-nav section

**Step 4.5** — Update `QuickPurchaseWidget.kt`
- Change `createOnTap` to navigate to `"quick_purchase"` instead of
  the old shopping-tab-with-open-purchase-flag approach

**Step 4.6** — Update strings
- `res/values/strings.xml` (EN): quick purchase, select category, enter price,
  enter store (optional), purchase saved, etc.
- `res/values-de/strings.xml` (DE): translated equivalents
- `res/values-uk/strings.xml` (UK): translated equivalents

### Phase 5: Testing

**Step 5.1** — Unit tests
- `PurchaseListNameGeneratorTest`: verify naming with 0, 1, 2 same-day lists
- `QuickPurchaseViewModelTest`: verify state transitions, empty-list purchase creation with category
- `SpendingCalculatorTest`: verify virtual "Quick Purchase" product for empty lists with `finalTotal`

**Step 5.2** — UI tests
- Dashboard widget tap navigates to QuickPurchaseScreen
- Category grid shows only expense categories in hierarchy
- Purchase creates an **empty** ShoppingList with correct name, store, category (no phantom item)

### Phase 6: Verification
- Build: `./gradlew assembleDebug`
- Lint: `./gradlew lint`
- Unit tests: `./gradlew test`
- Manual smoke test: full Quick Purchase flow on device/emulator

## Widget Deletion (Already Implemented)
Not part of this plan — already fully functional:
- Long-press on widget → `requestRemoveWidget()` → confirmation dialog →
  `confirmRemoveWidget()` → re-save configs → re-index orders
- Files: `DashboardScreen.kt:136`, `DashboardViewModel.kt:156-176`

## File Manifest

| # | File | Action | Phase |
|---|------|--------|-------|
| 1 | `data/local/entity/CategoryEntity.kt` | Modify | 1 |
| 2 | `data/local/AppDatabase.kt` | Modify | 1 |
| 3 | `data/CategoryEmoji.kt` | **Create** | 2 |
| 4 | `ui/components/category/CategoryDialog.kt` | Modify | 2 |
| 5 | `ui/components/category/CategoryPickerSheet.kt` | Modify | 2 |
| 6 | `ui/components/shoppinglist/ShoppingListCard.kt` | Modify | 2 |
| 7 | `ui/components/dashboard/DashboardScreen.kt` | Modify | 2 |
| 8 | `ui/components/dashboard/widgets/QuickPurchaseWidget.kt` | Modify | 4 |
| 9 | `data/local/dao/ShoppingListDao.kt` | Modify | 3 |
| 10 | `data/local/repository/ShoppingListRepository.kt` | Modify | 3 |
| 11 | `ui/components/product/PurchaseDialog.kt` | Modify | 3 |
| 12 | `ui/viewmodel/PurchaseDialogViewModel.kt` | Modify | 3 |
| 13 | `ui/viewmodel/ShoppingListViewModel.kt` | Modify | 3 |
| 14 | `ui/viewmodel/DashboardViewModel.kt` | Modify | 3 |
| 15 | `data/SpendingCalculator.kt` | Modify | 3 |
| 16 | `ui/viewmodel/AnalyticsViewModel.kt` | Modify | 3 |
| 17 | `data/PurchaseListNameGenerator.kt` | **Create** | 4 |
| 18 | `ui/viewmodel/QuickPurchaseViewModel.kt` | **Create** | 4 |
| 19 | `ui/components/dashboard/QuickPurchaseScreen.kt` | **Create** | 4 |
| 20 | `ui/navigation/Screen.kt` | Modify | 4 |
| 21 | `ui/components/main/MainScreen.kt` | Modify | 4 |
| 22 | `res/values/strings.xml` + DE/UK | Modify | 4 |

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Empty list shows a phantom "Unknown" product if the bug fix is missed | Medium | Remove `productId = 0L` insertion (`ShoppingListRepository.kt:74-76`); test manual purchase with no items |
| Virtual "Quick Purchase" product misses category for lists with multiple/empty categories | Medium | Use batch query `getCategoryCrossRefsForListsSync`; empty lists carry exactly one category |
| Analytics total mismatch persists if virtual product total doesn't match `finalTotal` | Medium | Unit-test `computeAdjustedItems` with an empty list that has `finalTotal` |
| Emoji rendering issues on older Android fonts | Low | Use common emojis; test on API 29 emulator |
| Quick Purchase creates duplicate lists due to race condition | Medium | Use synchronized `generateListName` with DB-level check |
| Existing widgets JSON deserialization fails with new type | Low | Quick Purchase type already exists; no new widget types |
| Navigation back from Quick Purchase doesn't refresh dashboard | Low | `DashboardViewModel.observeDatabaseChanges()` already triggers on DB writes |

## Estimated Effort
- Phase 1 (DB): 20 min
- Phase 2 (Emoji UI): 2 hours
- Phase 3 (Empty list + analytics virtual product): 2 hours
- Phase 4 (Quick Purchase): 3 hours
- Phase 5 (Tests): 1.5 hours
- **Total**: ~8.75 hours

## Updates
- [2026-08-10]: Task 2 (default product per category) cancelled — replaced by Task 2.a (empty list with category). Phase 1 now emoji-only migration; Phase 3 rewritten for empty-list creation + phantom-item bug fix + virtual "Quick Purchase" product in analytics; manifest/risks/estimates updated accordingly.
- [2026-08-10]: Task 1 (emoji icons) now tracked separately as [[plans/category-emojis]] (focused plan for Issue #50). This page remains the epic-level plan.
- [2026-08-10]: Phase 3 refined after code review vs issue #51. Added: batch DAO query `getCategoryCrossRefsForListsSync` (existing `getCategoriesForShoppingListSync` is single-list only); `PurchaseDialog` and `PurchaseDialogViewModel` changes for mandatory category picker in manual mode; clarified category is mandatory only for manual purchases (not scan mode); `processPurchase()` accepts `categoryId: Long? = null`; finishing existing lists preserves their categories; `ProductStatsCalculator.kt` dropped from Task 2.a scope (virtual product synthesized in `computeAdjustedItems` only). Manifest updated with PurchaseDialog files and `DashboardViewModel.kt`.
