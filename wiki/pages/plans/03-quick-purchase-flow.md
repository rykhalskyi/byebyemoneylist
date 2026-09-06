---
created: 2026-08-11
type: plan
tags: [quick-purchase, dashboard, ux, task-3, issue-52]
related:
  - "../specs/01-dashboard-ux-improvements.md"
  - "../plans/01-dashboard-ux-improvements.md"
  - "../tickets/01-issue-52-quick-purchase.md"
---

# 3. Quick Purchase Flow (Issue #52) — Implementation Plan

Focused implementation plan for **Issue #52 — Dashboard UX: Streamlined Quick
Purchase Flow**. This is Task 3 of the [specs/01-dashboard-ux-improvements](../specs/01-dashboard-ux-improvements.md) epic
(see the epic-level [plans/01-dashboard-ux-improvements](../plans/01-dashboard-ux-improvements.md) for the full three-task
plan).

## Summary

Replace the current Quick Purchase widget navigation (Shopping tab with
`open_purchase` flag → complex `PurchaseDialog`) with a dedicated two-step
full-screen flow:
1. Enter price + optional store
2. Select category from a 3-column emoji+color grid

## Preconditions

Tasks 1 and 2.a are complete — all infrastructure needed already exists:

- `ShoppingListRepository.processPurchase()` accepts `categoryId: Long?` and
  creates empty lists without phantom items
- `SpendingCalculator.kt` synthesizes virtual "Quick Purchase" products for
  empty lists in analytics
- `CategoryEntity.emoji` field is available for grid display
- `SmartSelectField` component exists for store auto-complete
- `StoreRepository.getOrCreate()` exists for store resolution
- `ShoppingListDao.getFinishedListsInTimeRange()` exists for date-based queries

## Key Decisions

- **Full-screen route** (`quick_purchase`), not dialog/bottom sheet
- **3-column grid** for category buttons on the selection screen
- **Exclude income categories** from the grid
- **Immediate create** on category tap (no confirmation step)
- **List name format**: `{Store} {dd.MM.yy}` (e.g., "Aldi 11.08.26"). If store
  is blank, use `Quick Purchase {dd.MM.yy}`. Auto-increment counter for
  same-day/same-name duplicates (e.g., "Aldi 11.08.26 2")
- **No store confirmation dialog** — the user types the store name explicitly;
  auto-creation without confirmation is acceptable for the quick flow.
- **ViewModel created internally** in `QuickPurchaseScreen` via factory
  (matches `ProductScreen` pattern), not wired through `MainScreen.kt`

## Step-by-Step Implementation

### Step 1: Create `data/PurchaseListNameGenerator.kt`

New centralized list-name generator class:

- Constructor takes `ShoppingListRepository`
- `suspend fun generate(store: String, date: Long = System.currentTimeMillis()): String`
- Computes `baseName = "$store dd.MM.yy"` (or `"Quick Purchase dd.MM.yy"` if store blank)
- Queries `shoppingListRepository.getFinishedListsInTimeRange(startOfDay, endOfDay)`
  for same-day lists whose name starts with `baseName`
- Returns `baseName` if 0 duplicates, else `"$baseName {count + 1}"`

### Step 2: Create `ui/viewmodel/QuickPurchaseViewModel.kt`

State class and ViewModel:

```kotlin
data class QuickPurchaseUiState(
    val price: String = "",
    val storeText: String = "",
    val expenseCategories: List<CategoryEntity> = emptyList(),
    val isLoading: Boolean = false,
    val purchaseComplete: Boolean = false,
    val error: String? = null,
)
```

- **Dependencies**: `StoreRepository`, `ShoppingListRepository`,
  `CategoryRepository`, `ProductRepository`, `PriceRepository`
- **Actions**: `updatePrice(text)`, `updateStore(text)`,
  `selectCategory(category)`, `dismissError()`
- **`selectCategory(category)`**: generates list name, parses price, calls
  `shoppingListRepository.processPurchase(listId = null, listName, storeName,
  price, items = emptyList(), ..., categoryId = category.id)`, sets
  `purchaseComplete = true` on success
- **`Factory`**: wired through `ByeByeMoneyApplication` (standard pattern
  matching `DashboardViewModel.Factory`)
- Loads expense categories (`!isIncome`) on init via `categoryRepository.allCategories`

### Step 3: Create `ui/components/dashboard/QuickPurchaseScreen.kt`

Two-step UI with local composable state for current step:

**Layout**: `Scaffold` with `TopAppBar` (title + back arrow)

**Step 1 — Price + Store**:
- `OutlinedTextField` for price (numeric keyboard, `KeyboardType.Decimal`)
- `SmartSelectField` for store (reuses existing `StoreEntity` list,
  auto-suggests, supports free-text new store)
- `Button("Next")` — disabled until price is a valid positive number
- Simple `Column` layout inside the Scaffold

**Step 2 — Category Grid**:
- "Select Category" title text
- `LazyVerticalGrid(columns = GridCells.Fixed(3))` of category cards
- Each card: `ElevatedCard` with the category's emoji (or fallback icon if
  null), colored background using `safeParseColor(category.color)`, and
  category name text
- Hierarchical order: parent categories first (with their children grouped
  underneath); `GridItemSpan(maxLineSpan)` for parent section headers
- On tap: call `viewModel.selectCategory(category)`

**Loading/Creation state**:
- While `isLoading`: show `CircularProgressIndicator` overlay
- On `purchaseComplete`: show snackbar + `navController.popBackStack()` via
  `LaunchedEffect`

**Navigation**:
- TopAppBar back arrow: `navController.popBackStack()`
- `QuickPurchaseScreen` receives `navController: NavController` parameter

### Step 4: Modify `ui/navigation/Screen.kt`

Add route:
```kotlin
object QuickPurchase : Screen("quick_purchase", R.string.quick_purchase_title, Icons.Default.Add)
```

Not added to `mainScreens` list (not a bottom-nav tab; icon is placeholder for
the `Screen` constructor requirement).

### Step 5: Modify `ui/components/main/MainScreen.kt`

Add composable in `NavHost` block:
```kotlin
composable(Screen.QuickPurchase.route) {
    QuickPurchaseScreen(navController = navController)
}
```

### Step 6: Modify `ui/components/dashboard/widgets/QuickPurchaseWidgetCard.kt`

Change `createOnTap()`:
- **Before**: navigates to `Screen.Shopping.route` with `open_purchase` flag
- **After**: navigates to `Screen.QuickPurchase.route` with same
  `popUpTo`/`launchSingleTop`/`restoreState` pattern

### Step 7: Add string resources

New strings in `res/values/strings.xml`:
- `quick_purchase_title` → "Quick Purchase"
- `enter_price` → "Enter price"
- `store_name_optional` → "Store (optional)"
- `select_category` → "Select Category"
- `next_button` → "Next"
- `purchase_saved` → "Purchase saved"

Add equivalents in `res/values-de/strings.xml` and `res/values-uk/strings.xml`.

## File Manifest

| # | File | Action | Effort |
|---|------|--------|--------|
| 1 | `data/PurchaseListNameGenerator.kt` | **Create** | 30 min |
| 2 | `ui/viewmodel/QuickPurchaseViewModel.kt` | **Create** | 45 min |
| 3 | `ui/components/dashboard/QuickPurchaseScreen.kt` | **Create** | 1.5 h |
| 4 | `ui/navigation/Screen.kt` | Modify | 5 min |
| 5 | `ui/components/main/MainScreen.kt` | Modify | 10 min |
| 6 | `ui/components/dashboard/widgets/QuickPurchaseWidgetCard.kt` | Modify | 10 min |
| 7 | `res/values/strings.xml` | Modify | 10 min |
| 8 | `res/values-de/strings.xml` | Modify | 10 min |
| 9 | `res/values-uk/strings.xml` | Modify | 10 min |

**Total estimated**: ~3.5 hours

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Category grid layout overflow on narrow screens | Low | 3-column grid is standard; test on API 29 small device |
| Same-day name collision if clock drifts | Low | Date range query covers full day; counter handles duplicates per-name |
| `processPurchase()` auto-creates store without address | Low | Optional store is acceptable; same behavior as existing manual entry |
| Navigation back doesn't refresh dashboard widgets | None | `DashboardViewModel.observeDatabaseChanges()` triggers on DB writes from `processPurchase()` |
| `QuickPurchaseScreen` not compiled due to new route not in NavHost | Medium | Step 5 must be tested with `./gradlew compileDebugKotlin` |

## Testing Checklist

- [ ] `./gradlew compileDebugKotlin` — builds cleanly
- [ ] `./gradlew assembleDebug` — APK builds
- [ ] Widget tap navigates to QuickPurchaseScreen (not Shopping tab)
- [ ] Price field accepts decimal input; Next disabled until valid
- [ ] Store field auto-completes from existing stores; free-text works
- [ ] "Next" transitions to category grid; back navigates to step 1
- [ ] Category grid shows only expense categories
- [ ] Category grid displays emoji + color + name
- [ ] Category tap creates purchase immediately; snackbar shows
- [ ] Navigation returns to dashboard; widgets refresh
- [ ] New store is auto-created on purchase
- [ ] List name follows `{Store} {dd.MM.yy}` format
- [ ] Same-day same-store purchase gets counter suffix

## Deviations from Epic Plan

- **Name format**: Epic plan says `"Purchase {dd.MM.yy} {counter}"`; ticket
  #52 specifies `{Store} {dd.MM.yy}`. This plan follows the ticket per
  [research/01-dashboard-epic-analysis](../research/01-dashboard-epic-analysis.md) Task 3 auto-generated list name decision.
- **No store confirmation dialog**: Epic plan mentions "Store auto-creation
  follows existing logic (confirmation dialog)". However, in the Quick Purchase
  flow the user explicitly types the store name, making confirmation redundant.
  The existing `processPurchase()` auto-creates stores without confirmation
  (same path as scan-mode purchases).
- **ViewModel creation pattern**: This plan creates the ViewModel inside
  `QuickPurchaseScreen` rather than passing it from `MainScreen.kt`. This is
  consistent with `ProductScreen` pattern and avoids unnecessary wiring in the
  top-level composable.

## Related

- Ticket: [tickets/01-issue-52-quick-purchase](../tickets/01-issue-52-quick-purchase.md)
- Epic spec: [specs/01-dashboard-ux-improvements](../specs/01-dashboard-ux-improvements.md)
- Epic plan: [plans/01-dashboard-ux-improvements](../plans/01-dashboard-ux-improvements.md)
- Epic research: [research/01-dashboard-epic-analysis](../research/01-dashboard-epic-analysis.md)
- GitHub: [rykhalskyi/byebyemoneylist#52](https://github.com/rykhalskyi/byebyemoneylist/issues/52)
