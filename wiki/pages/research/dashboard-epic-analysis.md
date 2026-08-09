---
created: 2026-08-09
type: research
tags: [dashboard, quick-purchase, category-icons, default-product, epic]
related: [[specs/dashboard-ux-improvements]] [[plans/dashboard-ux-improvements]]
---

# Dashboard UX Epic — Research & Analysis

## Source
Raw idea: `wiki/raw/idea-dashboard.txt`

## Context
The app currently has a Dashboard with 4 widget types (Spent Today, This Month,
Quick Purchase, Category Spending). The Quick Purchase widget simply opens the
existing `PurchaseDialog`. This epic proposes three improvements to the dashboard
UX: category emojis, default products per category, and a streamlined Quick
Purchase flow with a dedicated category-selection screen.

### Existing Widget System (already implemented)
- Dashboard with 2-column `LazyVerticalGrid`
- 4 widget types: `SPENT_TODAY`, `THIS_MONTH`, `QUICK_PURCHASE`, `CATEGORY_SPENDING`
- Widget add via FAB + `AddWidgetDialog`
- Widget delete via **long-press** → confirmation `AlertDialog` → `confirmRemoveWidget()`
  (`DashboardScreen.kt:136`, `DashboardViewModel.kt:156-176`)
- Widget configs serialized to JSON in `SharedPreferences` via `PreferencesManager`
- Default widgets (if none saved): `SPENT_TODAY` + `THIS_MONTH`

## Task 1: Emoji Icons for Categories

### Current State
- `CategoryEntity` fields: `id`, `name`, `color` (hex), `parentId`, `isIncome`
- Categories use 7 predefined colors shown as dots in lists, pickers, cards
- No icon/emoji support exists
- `CategoryDialog` has color picker with 7 colored circles

### Decision
- **Emoji** (not Material Icons). No extra dependency needed.
- **Supplement** colors (show emoji + colored background, not replace).
- Emoji stored as nullable `String` on `CategoryEntity`, max 2 chars.

### Key Changes
1. `CategoryEntity`: add `emoji: String?` field
2. DB migration v20→v21: add `emoji TEXT` column
3. `CategoryDialog`: add emoji picker grid with categorized emojis
4. All category display points: show emoji alongside color
5. New `CategoryEmoji` object with curated emoji list (~150 emojis in
   categories: Food, Transport, Home, Health, Shopping, Entertainment, Money,
   People, Nature, Other)

### Touch Points
- `CategoryEntity.kt` (data model)
- `AppDatabase.kt` (migration)
- `CategoryDialog.kt` (emoji picker)
- `CategoryPickerSheet.kt` (display emoji)
- `CategoryChipsField` in various screens (display emoji)
- `ShoppingListCard.kt` (category header)
- `DashboardScreen.kt` (category widget chips)
- `CatalogScreen.kt` / `CategoriesTab.kt` (category list items)

## Task 2: Default Product per Category

### Current State
- `ProductEntity` fields: `id`, `name`, `barcode`, `picturePath`, `categoryId`,
  `status`, `changedAt`, `isSubscription`, `isFavorite`, `isIncome`
- Products have full lifecycle: catalog, price history, aliases, analogs
- No concept of "virtual" or "hidden" products

### Decision
- Approach B: Flagged `ProductEntity` — add `isDefault: Boolean = false`.
- Default products auto-created when user sets one for a category.
- Name format: `"Quick-{CategoryName}"` (e.g., "Quick-Groceries").
- Hidden from catalog by filtering `isDefault = false` in product queries.
- No price history (`PriceEntity` rows) for default products — the purchase
  itself is the price event.
- One default product per category enforced at repository level.

### Key Changes
1. `ProductEntity`: add `isDefault: Boolean = false`
2. DB migration: add `isDefault INTEGER NOT NULL DEFAULT 0`
3. `ProductRepository` / `ProductDao`: filter out `isDefault=true` from:
   - `getAllProductsOnce()`
   - `getNormalProducts()`
   - Catalog search
4. `CategoryRepository`: add `getDefaultProductForCategory(categoryId)` 
   and `setDefaultProductForCategory(categoryId, productName)`
5. `CategoryDialog`: add "Default Product" field
6. Analytics works automatically — `SpendingCalculator` aggregates by productId
   from `ShoppingListItems`

### Why Not Separate Table (Approach C)?
A separate table would require parallel queries for every product lookup,
duplicate the product concept, and prevent reuse of existing merge/remap logic.
The flag approach is simpler and leverages all existing product infrastructure.

### Touch Points
- `ProductEntity.kt`
- `AppDatabase.kt` (migration, bump to v21 — combined with emoji migration)
- `ProductDao.kt`
- `ProductRepository.kt`
- `CategoryRepository.kt` (default product CRUD)
- `CategoryDialog.kt`
- `CatalogScreen.kt` / `ProductsTab.kt` (filtering)

## Task 3: Streamlined Quick Purchase

### Current State
- Dashboard `QUICK_PURCHASE` widget exists, but it just opens `PurchaseDialog`
- `PurchaseDialog` is designed for finishing shopping lists with itemized content
- Flow: Dashboard → navigate to Shopping tab with `open_purchase` flag →
  PurchaseDialog with list name, store, price, optional receipt scanning

### Proposed Flow
```
Dashboard → tap Quick Purchase widget
         → QuickPurchaseScreen (new full-screen route)
              Step 1: Price input + optional store name
              Step 2: Category selection grid (hierarchical, emoji+color buttons)
              Tap category → immediate create → snackbar → pop back to Dashboard
```

### Key Decisions
- **Full-screen route** (`quick_purchase`), not dialog/bottom sheet
- **3-column grid** for category buttons on the selection screen
- **Exclude income categories** from the grid
- **Immediate create** on category tap (no confirmation step)
- **Auto-generated list name**: `"Purchase {dd.MM.yy} {counter}"`
- Store field reuses existing `SmartSelectField` with auto-create logic

### List Name Generator
New centralized class `PurchaseListNameGenerator`:
- Counts existing lists starting with `"Purchase {date}"` for the same day
- If count = 0 → `"Purchase 09.08.26"`
- If count > 0 → `"Purchase 09.08.26 2"`, `"Purchase 09.08.26 3"`, etc.

### Key Changes
1. `Screen.kt`: add `QuickPurchase` route `"quick_purchase"`
2. `MainScreen.kt`: add composable for new route in NavHost
3. `QuickPurchaseViewModel.kt` (new): manages price, store, category list,
   calls `ShoppingListRepository.processPurchase()`
4. `QuickPurchaseScreen.kt` (new): two-step UI
5. `CategoryGridStep.kt` (new or inline): 3-column hierarchical category grid
6. `QuickPurchaseWidget.kt` (update): change `createOnTap` to navigate to
   `"quick_purchase"` instead of shopping tab
7. `PurchaseListNameGenerator.kt` (new): centralized list naming
8. Strings for new UI labels

### Purchase Creation Logic
When user taps a category button:
1. Generate list name via `PurchaseListNameGenerator`
2. Resolve store via `StoreRepository.getOrCreate()` (same as existing)
3. Find/create default product for category: `ProductEntity(name="Quick-{cat}", categoryId=catId, isDefault=true)`
4. Call `ShoppingListRepository.processPurchase()` with auto-generated list,
   single `ShoppingListItem` pointing to default product, price, store
5. Refresh dashboard widget data
6. Show snackbar and navigate back

### Touch Points
- `Screen.kt`
- `MainScreen.kt`
- `QuickPurchaseViewModel.kt` (new)
- `QuickPurchaseScreen.kt` (new)
- `PurchaseListNameGenerator.kt` (new)
- `DashboardWidget.kt` / `QuickPurchaseWidget.kt` (update `createOnTap`)
- `strings.xml` (EN, DE, UK)

## Database Migration (Single, Combined v20→v21)
Both Task 1 and Task 2 add columns — combine into one migration:
```sql
ALTER TABLE categories ADD COLUMN emoji TEXT;
ALTER TABLE products ADD COLUMN isDefault INTEGER NOT NULL DEFAULT 0;
```

## Widget Deletion (Already Implemented)
Widget deletion from the Dashboard is already fully functional:
- Long-press on any widget card calls `viewModel.requestRemoveWidget(config)`
- Confirmation `AlertDialog` with "Delete" / "Cancel"
- `DashboardViewModel.confirmRemoveWidget()` removes config, re-saves, re-indexes orders
- No changes needed for this feature

## Files Summary

### New Files (4)
| File | Purpose |
|------|---------|
| `data/CategoryEmoji.kt` | Curated emoji lists by category |
| `data/PurchaseListNameGenerator.kt` | Auto-generate list names |
| `ui/components/dashboard/QuickPurchaseScreen.kt` | New Quick Purchase UI |
| `ui/viewmodel/QuickPurchaseViewModel.kt` | Quick Purchase state management |

### Modified Files (14+)
| File | Task |
|------|------|
| `data/local/entity/CategoryEntity.kt` | Task 1: add emoji |
| `data/local/entity/ProductEntity.kt` | Task 2: add isDefault |
| `data/local/AppDatabase.kt` | Tasks 1+2: migration |
| `data/local/dao/ProductDao.kt` | Task 2: filter isDefault |
| `data/local/repository/ProductRepository.kt` | Task 2: filter + default product CRUD |
| `data/local/repository/CategoryRepository.kt` | Task 2: default product helpers |
| `ui/components/category/CategoryDialog.kt` | Tasks 1+2: emoji picker + default product field |
| `ui/components/category/CategoryPickerSheet.kt` | Task 1: show emoji |
| `ui/components/shoppinglist/ShoppingListCard.kt` | Task 1: show emoji |
| `ui/components/dashboard/DashboardScreen.kt` | Task 1: show emoji |
| `ui/components/dashboard/widgets/QuickPurchaseWidget.kt` | Task 3: update navigation |
| `ui/navigation/Screen.kt` | Task 3: add route |
| `ui/components/main/MainScreen.kt` | Task 3: add composable |
| `res/values/strings.xml` | Tasks 1+3: new strings |

## Updates
- [2026-08-09]: Initial analysis. Widget deletion (long-press) confirmed as already implemented.
