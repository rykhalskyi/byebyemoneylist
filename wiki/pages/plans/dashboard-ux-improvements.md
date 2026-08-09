---
created: 2026-08-09
type: plan
tags: [dashboard, quick-purchase, category-icons, default-product, ux, epic]
related: [[specs/dashboard-ux-improvements]] [[research/dashboard-epic-analysis]]
---

# Dashboard UX Improvements — Implementation Plan

## Overview
Three interconnected features implemented across ~18 files. Two DB columns
added in a single migration. Four new source files. No new DB tables.
Widget deletion (long-press) is already implemented and requires no changes.

## Step-by-Step Plan

### Phase 1: Database Changes (Tasks 1+2 combined)

**Step 1.1** — Update `CategoryEntity.kt`
- Add `val emoji: String? = null`

**Step 1.2** — Update `ProductEntity.kt`
- Add `val isDefault: Boolean = false`

**Step 1.3** — Add DB migration in `AppDatabase.kt`
- Bump version to 21
- Add `MIGRATION_20_TO_21`:
  ```sql
  ALTER TABLE categories ADD COLUMN emoji TEXT;
  ALTER TABLE products ADD COLUMN isDefault INTEGER NOT NULL DEFAULT 0;
  ```

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

### Phase 3: Default Product (Task 2)

**Step 3.1** — Update `ProductDao.kt`
- Add `getDefaultProductForCategory(categoryId: Long): ProductEntity?`
- Modify catalog/list queries to exclude `isDefault = 1`

**Step 3.2** — Update `ProductRepository.kt`
- Add `getDefaultProductForCategory(categoryId: Long): ProductEntity?`
- Add `setDefaultProductForCategory(categoryId: Long, categoryName: String): ProductEntity`
  - Upserts: if a default product already exists for this category, reuse it
  - Creates new with `isDefault = true`, `categoryId`,
    `name = "Quick-{categoryName}"`
  - If another product was default for this category, unset its `isDefault`
- Add `isDefault = false` filter to normal product queries

**Step 3.3** — Update `CategoryDialog.kt`
- Add "Default Product" text field pre-filled with "Quick-{categoryName}"
- Save/update default product when category is saved

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
  3. Resolve/create default product for category
  4. Call `ShoppingListRepository.processPurchase()`
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
- `QuickPurchaseViewModelTest`: verify state transitions, purchase creation
- `ProductRepositoryTest`: verify isDefault filtering, default product CRUD

**Step 5.2** — UI tests
- Dashboard widget tap navigates to QuickPurchaseScreen
- Category grid shows only expense categories in hierarchy
- Purchase creates ShoppingList with correct name, store, product

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
| 2 | `data/local/entity/ProductEntity.kt` | Modify | 1 |
| 3 | `data/local/AppDatabase.kt` | Modify | 1 |
| 4 | `data/CategoryEmoji.kt` | **Create** | 2 |
| 5 | `ui/components/category/CategoryDialog.kt` | Modify | 2, 3 |
| 6 | `ui/components/category/CategoryPickerSheet.kt` | Modify | 2 |
| 7 | `ui/components/shoppinglist/ShoppingListCard.kt` | Modify | 2 |
| 8 | `ui/components/dashboard/DashboardScreen.kt` | Modify | 2 |
| 9 | `ui/components/dashboard/widgets/QuickPurchaseWidget.kt` | Modify | 4 |
| 10 | `data/local/dao/ProductDao.kt` | Modify | 3 |
| 11 | `data/local/repository/ProductRepository.kt` | Modify | 3 |
| 12 | `data/local/repository/CategoryRepository.kt` | Modify | 3 |
| 13 | `data/PurchaseListNameGenerator.kt` | **Create** | 4 |
| 14 | `ui/viewmodel/QuickPurchaseViewModel.kt` | **Create** | 4 |
| 15 | `ui/components/dashboard/QuickPurchaseScreen.kt` | **Create** | 4 |
| 16 | `ui/navigation/Screen.kt` | Modify | 4 |
| 17 | `ui/components/main/MainScreen.kt` | Modify | 4 |
| 18 | `res/values/strings.xml` + DE/UK | Modify | 4 |

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Default product appears in catalog despite filtering | Medium | Test all catalog queries; verify `isDefault=0` filter |
| Emoji rendering issues on older Android fonts | Low | Use common emojis; test on API 29 emulator |
| Quick Purchase creates duplicate lists due to race condition | Medium | Use synchronized `generateListName` with DB-level check |
| Existing widgets JSON deserialization fails with new type | Low | Quick Purchase type already exists; no new widget types |
| Navigation back from Quick Purchase doesn't refresh dashboard | Low | `DashboardViewModel.observeDatabaseChanges()` already triggers on DB writes |

## Estimated Effort
- Phase 1 (DB): 30 min
- Phase 2 (Emoji UI): 2 hours
- Phase 3 (Default Product): 1.5 hours
- Phase 4 (Quick Purchase): 3 hours
- Phase 5 (Tests): 1.5 hours
- **Total**: ~8.5 hours
