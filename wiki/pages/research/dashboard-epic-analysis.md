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
UX: category emojis, empty lists with a mandatory category (Task 2.a, replacing
the cancelled default-product idea), and a streamlined Quick
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

## Task 2: ~~Default Product per Category~~ CANCELLED → Task 2.a: Empty List with Category

### Status: Cancelled
The "default product per category" idea (Task 2) was **cancelled**. A list would
contain a product that doesn't exist (a phantom), which breaks the concept of a
shopping list itself. Superseded by Task 2.a below. No `isDefault` flag, no
"Quick-{Category}" product rows.

## Task 2.a: Empty List with Category

### Current State
- `ProductEntity` has no virtual/hidden concept.
- Manual purchase (only a total price, `items.isEmpty()`) goes through
  `ShoppingListRepository.processPurchase()` (`ShoppingListRepository.kt:20`).
  The list is created at `ShoppingListRepository.kt:57-61` with **no** category
  cross-refs, then a phantom item is inserted:
  ```kotlin
  if (items.isEmpty()) {
      // Manual entry with only total price
      insertShoppingListItem(ShoppingListItemEntity(
          id = generateId(), shoppingListId = targetListId,
          productId = 0L, quantity = 1.0, isChecked = isChecked, position = 0))
  }
  ```
  (`ShoppingListRepository.kt:74-76`) — this is the **Id == 0 bug**.
- List→category mapping is stored in `ShoppingListCategoryCrossRef` via
  `syncCategories()` (`ShoppingListRepository.kt:311-316`).

### Decision (Task 2.a)
- Manual purchase creates an **empty list** (no phantom item).
- **Category is mandatory** for a list; assigned via `ShoppingListCategoryCrossRef`.
- A list with no items is restricted to **one category**.
- Bug fix: manual purchase must not insert the `productId = 0L` item.
- In analytics, during the products-expenses statistics computation, if a list is
  empty, synthesize a **virtual product "Quick Purchase"** carrying the list's
  category.

### Bug — Manual Purchase Creates a List With a Phantom Item
- **Root cause:** `ShoppingListRepository.kt:74-76` — when `items.isEmpty()`
  (manual purchase with only total price), a `ShoppingListItemEntity` with
  `productId = 0L` is inserted into the list.
- **Trigger path:** `PurchaseDialog` → `ShoppingListsScreen.kt:670` →
  `ShoppingListViewModel.processPurchase()` (`ShoppingListViewModel.kt:534`);
  also `DashboardViewModel.processPurchase()` (`DashboardViewModel.kt:234`).
- **Impact:** in analytics the phantom item surfaces as an "Unknown" product
  (`SpendingCalculator.kt:38`, `UNKNOWN_PRODUCT_NAME`), polluting product stats
  and list content.
- **Fix:** skip item insertion when `items.isEmpty()` — create only the empty
  list and assign its category.

### How Analytics Is Calculated (products expenses)
1. `AnalyticsViewModel.loadAnalyticsData()` (`AnalyticsViewModel.kt:252`) calls
   `computeAdjustedItems()` (`SpendingCalculator.kt:69-122`).
2. `computeAdjustedItems()` loads finished lists in the time range
   (`getFinishedListsInTimeRange`), then their items
   (`getItemsWithProductForListsSync`), and emits one `AdjustedItem` **per
   shopping-list item**.
3. `ProductStatsCalculator.computeProductStats()` (`ProductStatsCalculator.kt:7`)
   groups `AdjustedItem`s by `productId` into `ProductStat`s.
4. **Empty lists emit zero `AdjustedItem`s** → they are absent from product
   stats, while `currentTotal`/`listSpendingMap` *do* include them via the
   `list.finalTotal` fallback (`AnalyticsViewModel.kt:310-316`). Result: any
   finished empty list triggers `hasProductTotalMismatch`
   (`AnalyticsViewModel.kt:377-378`).

### Fix — Virtual "Quick Purchase" Product in Analytics
During products-expenses computation, when a list has no items (and has a
`finalTotal`), synthesize an `AdjustedItem`/`ProductStat`:
- `productName = "Quick Purchase"`
- `categoryId` = the list's category from `ShoppingListCategoryCrossRef`
- `itemTotal` = `list.finalTotal`

This makes empty lists appear in product stats, restores category attribution,
and resolves the products/list total mismatch.

### Key Changes
1. `ShoppingListRepository.processPurchase()`:
   - Accept the selected category and write `ShoppingListCategoryCrossRef`
   - Do **not** insert the phantom `productId = 0L` item when `items.isEmpty()`
2. Enforce mandatory category + single-category-while-empty at the purchase flow
3. `SpendingCalculator.computeAdjustedItems()` / `ProductStatsCalculator`:
   synthesize the virtual "Quick Purchase" product for empty lists

### Touch Points
- `ShoppingListRepository.kt` (empty list creation, category assignment, bug fix)
- `ShoppingListDao.kt` (read list category cross-refs for analytics)
- `SpendingCalculator.kt` / `ProductStatsCalculator.kt` (virtual product)
- `AnalyticsViewModel.kt` (product stats aggregation feed)
- `ShoppingListViewModel.kt` / `DashboardViewModel.kt` (pass category on purchase)
- Purchase dialog / Quick Purchase flow (mandatory category UI)

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
3. Create an **empty** list with the selected category
   (`ShoppingListCategoryCrossRef`) — no phantom product, no items
4. Call `ShoppingListRepository.processPurchase()` with the empty list, the
   total price, and the store (skip item insertion)
5. Refresh dashboard widget data
6. Show snackbar and navigate back

The list shows up in analytics via the virtual "Quick Purchase" product with the
list's category (see Task 2.a).

### Touch Points
- `Screen.kt`
- `MainScreen.kt`
- `QuickPurchaseViewModel.kt` (new)
- `QuickPurchaseScreen.kt` (new)
- `PurchaseListNameGenerator.kt` (new)
- `DashboardWidget.kt` / `QuickPurchaseWidget.kt` (update `createOnTap`)
- `strings.xml` (EN, DE, UK)

## Database Migration (v20→v21)
Task 1 adds the emoji column; Task 2.a needs **no** schema change (no
`isDefault`, no virtual product storage — it is synthesized at analytics time):
```sql
ALTER TABLE categories ADD COLUMN emoji TEXT;
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

### Modified Files
| File | Task |
|------|------|
| `data/local/entity/CategoryEntity.kt` | Task 1: add emoji |
| `data/local/AppDatabase.kt` | Task 1: migration (emoji only) |
| `data/local/repository/ShoppingListRepository.kt` | Task 2.a: empty list, category, phantom-item bug fix |
| `data/local/dao/ShoppingListDao.kt` | Task 2.a: read list category for analytics |
| `data/SpendingCalculator.kt` / `ProductStatsCalculator.kt` | Task 2.a: virtual "Quick Purchase" product |
| `ui/components/category/CategoryDialog.kt` | Task 1: emoji picker |
| `ui/components/category/CategoryPickerSheet.kt` | Task 1: show emoji |
| `ui/components/shoppinglist/ShoppingListCard.kt` | Task 1: show emoji |
| `ui/components/dashboard/DashboardScreen.kt` | Task 1: show emoji |
| `ui/components/dashboard/widgets/QuickPurchaseWidget.kt` | Task 3: update navigation |
| `ui/navigation/Screen.kt` | Task 3: add route |
| `ui/components/main/MainScreen.kt` | Task 3: add composable |
| `res/values/strings.xml` | Tasks 1+3: new strings |

## Updates
- [2026-08-09]: Initial analysis. Widget deletion (long-press) confirmed as already implemented.
- [2026-08-10]: **Task 2 (default product per category) cancelled** — phantom product breaks the concept of lists. Replaced by Task 2.a (empty list with category). Documented the Id==0 bug (`ShoppingListRepository.kt:74-76`), the analytics pipeline (`computeAdjustedItems` → `ProductStatsCalculator`), and the virtual "Quick Purchase" product fix for empty lists. Migration reduced to emoji-only; no `isDefault` column.
