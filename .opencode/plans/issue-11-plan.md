# Issue #11: Dual-Price Feature: Show Estimated Total and User-Entered Total in Shopping Lists

## Summary
Implement a dual-price system for shopping lists where:
1. **Estimated total** = sum of product prices (with fallback logic)
2. **Final total** = user-entered amount (via Direct Purchase or Finish & Pay)
3. Products can have custom prices entered when added to a list

## Implementation Steps

### 1. Database Schema Changes

#### 1.1 Update ShoppingListItemEntity
**File:** `app/src/main/java/com/otakeeesen/byebyemoneylist/data/local/entity/ShoppingListItemEntity.kt`
- Add nullable `price: Double?` field to store user-entered price at item level
- Update entity definition with `@ColumnInfo(name = "price")`

#### 1.2 Create Database Migration
**File:** `app/src/main/java/com/otakeeesen/byebyemoneylist/data/local/AppDatabase.kt`
- Increment version from 5 to 6
- Add `MIGRATION_5_TO_6`: `ALTER TABLE shopping_list_items ADD COLUMN price REAL`

### 2. Data Model Updates

#### 2.1 Update PurchaseItem
**File:** `app/src/main/java/com/otakeeesen/byebyemoneylist/data/PurchaseItem.kt`
- Add `price: Double?` property (nullable)
- Update constructor

#### 2.2 Update ShoppingList
**File:** `app/src/main/java/com/otakeeesen/byebyemoneylist/data/ShoppingList.kt`
- Keep `estimatedTotal` property but enhance it to use item-specific prices first, then fallback
- Add `calculatedTotal` computed property that sums all item prices (using stored price or looked-up price)
- Ensure `estimatedTotal` uses the same logic

### 3. DAO Layer Updates

#### 3.1 Update ShoppingListDao
**File:** `app/src/main/java/com/otakeeesen/byebyemoneylist/data/local/dao/ShoppingListDao.kt`
- Modify `getAllItemsWithProduct()` query:
  - Add `sli.price AS itemPrice` to SELECT
  - Keep existing fields
- The query should return `ShoppingListItemWithProduct` with itemPrice field
- Add method: `getLatestPriceForProductAtStore(productId: Long, storeId: Long?): Double?`
  - Returns latest price for product at specific store (or null if storeId is null)
  - Uses COALESCE with subquery

#### 3.2 Update PriceDao
**File:** `app/src/main/java/com/otakeeesen/byebyemoneylist/data/local/dao/PriceDao.kt`
- Add: `getLatestPriceForProduct(productId: Long): PriceEntity?`
- Add: `getLatestPriceForProductAtStore(productId: Long, storeId: Long?): PriceEntity?`
  - If storeId is null, return global latest
  - Otherwise, return latest price for that product at that store

### 4. Repository Layer

#### 4.1 Create PriceRepository (NEW)
**File:** `app/src/main/java/com/otakeeesen/byebyemoneylist/data/local/repository/PriceRepository.kt`
- Inject `AppDatabase`
- Provide methods:
  - `getLatestPrice(productId: Long, storeId: Long?): PriceEntity?`
    - Uses PriceDao methods with store-specific fallback logic
  - `upsertPriceForProduct(productId: Long, storeId: Long?, value: Double)`
    - Insert new PriceEntity or update existing (based on latest)
    - Should create new entry with current timestamp

#### 4.2 Update ShoppingListRepository
**File:** `app/src/main/java/com/otakeeesen/byebyemoneylist/data/local/repository/ShoppingListRepository.kt`
- Add method: `getItemsForListWithPrices(listId: Long): Flow<List<ShoppingListItemWithProduct>>`
  - Or modify existing to include price data properly

### 5. ViewModel Updates

#### 5.1 Update ShoppingListViewModel
**File:** `app/src/main/java/com/otakeeesen/byebyemoneylist/ui/viewmodel/ShoppingListViewModel.kt`
- In the combine block where `ShoppingListItemWithProduct` is mapped to `PurchaseItem`:
  - Use `itemPrice` if not null
  - Else, call `priceRepository.getLatestPrice(item.productId, entity.storeId)` and use that value
  - Fallback to 0.0 if no price found
- Implement `finishAndPay(shoppingList: ShoppingList)`:
  - Show `FinishAndPayDialog` (to be created)
  - On confirm, update `ShoppingListEntity` with `finalTotal` and `isFinished = true`
  - Call `repository.updateShoppingList()`
- Add state for showing FinishAndPayDialog: `showFinishAndPayDialog: Boolean` and `currentShoppingList: ShoppingList?`

#### 5.2 Update AddProductViewModel
**File:** `app/src/main/java/com/otakeeesen/byebyemoneylist/ui/viewmodel/AddProductViewModel.kt`
- Modify `addExistingProduct(productId: Long, price: Double?, onComplete: () -> Unit)`
- Modify `createAndAddProduct(name: String, categoryName: String, barcode: String, price: Double?, onComplete: () -> Unit)`
- When adding item:
  - Insert `ShoppingListItemEntity` with provided `price` (can be null)
  - If `price` is not null, call `priceRepository.upsertPriceForProduct(productId, listStoreId, price)`
    - Need to get the storeId from the shopping list (query by listId)
- Add `PriceRepository` dependency to constructor

### 6. UI Components

#### 6.1 Create PriceInputDialog (NEW)
**File:** `app/src/main/java/com/otakeeesen/byebyemoneylist/ui/components/PriceInputDialog.kt`
- Composable dialog for entering/editing item price
- Parameters: `initialPrice: Double?`, `onConfirm: (Double) -> Unit`, `onDismiss: () -> Unit`
- Shows text field for price
- Pre-fill with `initialPrice` if not null
- Include "Keep current price" checkbox or button? Actually simpler: just allow editing; if left empty, means no custom price set (use lookup)
- Or: Show current known price as hint, user can override or leave blank to use default
- Validation: must be valid number or empty

#### 6.2 Update AddProductScreen
**File:** `app/src/main/java/com/otakeeesen/byebyemoneylist/ui/components/AddProductScreen.kt`
- When user taps a product (or creates new), show `PriceInputDialog`
- The dialog should:
  - Pre-fill with latest known price (from PriceDao lookup)
  - Allow user to accept (OK) or cancel
  - Pass the entered price to `viewModel.addExistingProduct(product.id, price, onBack)`
- Also when creating new product, pass price parameter

#### 6.3 Update ShoppingListCard
**File:** `app/src/main/java/com/otakeeesen/byebyemoneylist/ui/components/ShoppingListCard.kt`
- In the expanded view, under progress row, show two price rows:
  - Row 1: `Estimated: €%.2f` using `shoppingList.estimatedTotal`
  - Row 2: `Final: €%.2f` if `shoppingList.finalTotal != null`, else show "Not finalized" or hide
- Style: estimated in normal color, final in semi-bold or different color
- Also update the summary row (collapsed) to show both if space permits, or keep as is

#### 6.4 Create FinishAndPayDialog (NEW)
**File:** `app/src/main/java/com/otakeeesen/byebyemoneylist/ui/components/FinishAndPayDialog.kt`
- Dialog shown when Finish & Pay button clicked
- Parameters: `shoppingList: ShoppingList`, `onConfirm: (Double) -> Unit`, `onDismiss: () -> Unit`
- Content:
  - Show calculated total as suggestion/hint
  - Text field for actual total (pre-filled with calculated total)
  - Validation: must be number
- On confirm: call `onConfirm(enteredTotal)`

#### 6.5 Update ShoppingListsScreen
**File:** `app/src/main/java/com/otakeeesen/byebyemoneylist/ui/components/ShoppingListsScreen.kt`
- Add state for `FinishAndPayDialog`: `showFinishAndPayDialog: Boolean`, `selectedList: ShoppingList?`
- When `onFinishAndPay` from card is called, set `selectedList` and show dialog
- Pass dialog to `ShoppingListCard` if needed, or handle at screen level
- In `ShoppingListCard`'s `onFinishAndPay` callback, trigger screen to show dialog

### 7. Strings Resources

**File:** `app/src/main/res/values/strings.xml`
Add:
- `<string name="estimated_total">Estimated: €%.2f</string>`
- `<string name="final_total">Final: €%.2f</string>`
- `<string name="enter_price">Enter price (optional)</string>`
- `<string name="price_hint">e.g., 2.99</string>`
- `<string name="not_finalized">Not finalized</string>`
- `<string name="finish_and_pay_title">Finish & Pay</string>`
- `<string name="actual_total">Actual total</string>`
- `<string name="keep_default_price">Leave empty to use default price</string>`

### 8. Dependency Injection Updates

**File:** `app/src/main/java/com/otakeeesen/byebyemoneylist/ByeByeMoneyApplication.kt` (if exists)
- Ensure `PriceRepository` is created and accessible
- Provide getter for `priceRepository`

If using Hilt/Dagger, update modules accordingly. If manual, add to Application class.

### 9. Testing Considerations

- Test adding product with custom price
- Test adding product without price (should use latest)
- Test that estimated total sums correctly with mixed prices
- Test Finish & Pay flow updates finalTotal
- Test database migration from version 5 to 6 (existing users)

## Files to Modify

1. `app/src/main/java/com/otakeeesen/byebyemoneylist/data/local/entity/ShoppingListItemEntity.kt`
2. `app/src/main/java/com/otakeeesen/byebyemoneylist/data/local/AppDatabase.kt`
3. `app/src/main/java/com/otakeeesen/byebyemoneylist/data/PurchaseItem.kt`
4. `app/src/main/java/com/otakeeesen/byebyemoneylist/data/ShoppingList.kt`
5. `app/src/main/java/com/otakeeesen/byebyemoneylist/data/local/dao/ShoppingListDao.kt`
6. `app/src/main/java/com/otakeeesen/byebyemoneylist/data/local/dao/PriceDao.kt`
7. `app/src/main/java/com/otakeeesen/byebyemoneylist/data/local/repository/PriceRepository.kt` (new)
8. `app/src/main/java/com/otakeeesen/byebyemoneylist/data/local/repository/ShoppingListRepository.kt`
9. `app/src/main/java/com/otakeeesen/byebyemoneylist/ui/viewmodel/ShoppingListViewModel.kt`
10. `app/src/main/java/com/otakeeesen/byebyemoneylist/ui/viewmodel/AddProductViewModel.kt`
11. `app/src/main/java/com/otakeeesen/byebyemoneylist/ui/components/PriceInputDialog.kt` (new)
12. `app/src/main/java/com/otakeeesen/byebyemoneylist/ui/components/FinishAndPayDialog.kt` (new)
13. `app/src/main/java/com/otakeeesen/byebyemoneylist/ui/components/AddProductScreen.kt`
14. `app/src/main/java/com/otakeeesen/byebyemoneylist/ui/components/ShoppingListCard.kt`
15. `app/src/main/java/com/otakeeesen/byebyemoneylist/ui/components/ShoppingListsScreen.kt`
16. `app/src/main/res/values/strings.xml`
17. `app/src/main/java/com/otakeeesen/byebyemoneylist/ByeByeMoneyApplication.kt` (if needed)

## Notes

- **Store-specific pricing**: Use the shopping list's storeId to filter price lookups. If no store assigned, use latest price regardless of store.
- **Price editing**: Only during item addition. For future iteration, consider long-press edit.
- **Finish & Pay**: Dialog pre-fills with calculated total; user can adjust.
- Migration must handle existing data gracefully (price will be null for old items, fallback to product price lookup).
- Ensure `getAllItemsWithProduct()` query correctly joins and handles null prices.

## Verification

1. Run app, create shopping list with store
2. Add products with custom prices (some with, some without)
3. Verify estimated total sums correctly (using custom prices where set, latest prices otherwise)
4. Click Finish & Pay, enter actual total (different from estimated), confirm
5. Verify final total displays on card
6. Test migration: install old version, create lists, upgrade, verify no crashes and prices still work