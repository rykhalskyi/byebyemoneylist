package com.otakeeesen.byebyemoneylist

import com.otakeeesen.byebyemoneylist.data.AdjustedItem
import com.otakeeesen.byebyemoneylist.data.computeAdjustedItems
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import com.otakeeesen.byebyemoneylist.data.local.dao.ShoppingListItemWithProduct
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.StoreRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.math.abs

class SpendingCalculatorTest {

    private val shoppingListRepository: ShoppingListRepository = mock()
    private val categoryRepository: CategoryRepository = mock()
    private val storeRepository: StoreRepository = mock()
    private val preferencesManager: PreferencesManager = mock()

    private fun createListItem(
        id: Long,
        listId: Long,
        productId: Long,
        productName: String,
        quantity: Double,
        price: Double,
        isChecked: Boolean = true,
        discount: Double? = null,
        categoryId: Long? = null,
        itemPrice: Double? = null
    ) = ShoppingListItemWithProduct(
        id = id,
        shoppingListId = listId,
        productId = productId,
        quantity = quantity,
        isChecked = isChecked,
        position = 0,
        productName = productName,
        productPicturePath = null,
        productStatus = "reviewed",
        productIsSubscription = false,
        productIsFavorite = false,
        itemPrice = itemPrice,
        price = price,
        discount = discount,
        customName = null,
        productCategoryId = categoryId
    )

    private fun createListEntity(
        id: Long,
        name: String = "Test List",
        finalTotal: Double? = null,
        isIncome: Boolean = false,
        storeId: Long? = null,
        isSubscription: Boolean = false,
        purchaseDate: Long? = 1_000_000L
    ) = ShoppingListEntity(
        id = id,
        name = name,
        createDate = 1_000_000L,
        purchaseDate = purchaseDate,
        storeId = storeId,
        isFinished = true,
        finalTotal = finalTotal,
        position = 0,
        isIncome = isIncome,
        isSubscription = isSubscription
    )

    // ===================================================================
    // Case 1: Product Stats — itemTotal = price * quantity - discount
    // ===================================================================

    @Test
    fun `product stats — itemTotal equals price times quantity minus discount`() = runTest {
        val list = createListEntity(id = 1L, finalTotal = null, purchaseDate = 1_000_000L)
        val bread = createListItem(id = 1L, listId = 1L, productId = 10L, productName = "Bread", quantity = 2.0, price = 0.59, discount = 0.20)
        val milk = createListItem(id = 2L, listId = 1L, productId = 20L, productName = "Milk", quantity = 3.0, price = 1.50, discount = null)

        runBlocking {
            whenever(shoppingListRepository.getFinishedListsInTimeRange(any(), any())).doReturn(listOf(list))
            whenever(shoppingListRepository.getItemsWithProductForListsSync(any())).doReturn(listOf(bread, milk))
            whenever(categoryRepository.getAllCategoriesOnce()).doReturn(emptyList())
            whenever(storeRepository.getAllStoresOnce()).doReturn(emptyList())
            whenever(preferencesManager.getActualPriceRule()).doReturn("PURCHASE_PRICE")
        }

        val result = computeAdjustedItems(0L, 2_000_000L, shoppingListRepository, categoryRepository, storeRepository, preferencesManager)

        assertEquals(2, result.size)

        val breadResult = result.first { it.productId == 10L }
        assertEquals("Bread", breadResult.productName)
        assertEquals(2.0, breadResult.quantity, 0.001)
        assertEquals(0.59 * 2.0 - 0.20, breadResult.itemTotal, 0.001) // 0.98
        assertEquals(0.20, breadResult.discount ?: 0.0, 0.001)

        val milkResult = result.first { it.productId == 20L }
        assertEquals("Milk", milkResult.productName)
        assertEquals(3.0, milkResult.quantity, 0.001)
        assertEquals(1.50 * 3.0, milkResult.itemTotal, 0.001) // 4.50
        assertEquals(0.0, milkResult.discount ?: 0.0, 0.001)

        // Both items should have the same listPriceActual (from the list)
        assertEquals(breadResult.listPriceActual, milkResult.listPriceActual, 0.001)
    }

    // ===================================================================
    // Case 2: Monthly Total — sum of |listPriceActual| for non-income lists
    // ===================================================================

    @Test
    fun `monthly total — sum of abs listPriceActual for non-income lists`() = runTest {
        val list1 = createListEntity(id = 1L, name = "Groceries", finalTotal = 50.0, isIncome = false, purchaseDate = 1_500_000L)
        val list2 = createListEntity(id = 2L, name = "Electronics", finalTotal = 120.0, isIncome = false, purchaseDate = 1_600_000L)
        val incomeList = createListEntity(id = 3L, name = "Salary", finalTotal = 1000.0, isIncome = true, purchaseDate = 1_550_000L)

        val item1 = createListItem(id = 1L, listId = 1L, productId = 10L, productName = "Food", quantity = 1.0, price = 50.0)
        val item2 = createListItem(id = 2L, listId = 2L, productId = 20L, productName = "Gadget", quantity = 1.0, price = 120.0)
        val item3 = createListItem(id = 3L, listId = 3L, productId = 30L, productName = "Salary", quantity = 1.0, price = 1000.0)

        runBlocking {
            whenever(shoppingListRepository.getFinishedListsInTimeRange(any(), any())).doReturn(listOf(list1, list2, incomeList))
            whenever(shoppingListRepository.getItemsWithProductForListsSync(any())).doReturn(listOf(item1, item2, item3))
            whenever(categoryRepository.getAllCategoriesOnce()).doReturn(emptyList())
            whenever(storeRepository.getAllStoresOnce()).doReturn(emptyList())
            whenever(preferencesManager.getActualPriceRule()).doReturn("PURCHASE_PRICE")
        }

        val result = computeAdjustedItems(0L, 2_000_000L, shoppingListRepository, categoryRepository, storeRepository, preferencesManager)

        val expenseItems = result.filter { !it.isIncome }
        val incomeItems = result.filter { it.isIncome }

        // Monthly total = sum of |listPriceActual| for expense lists
        // list1: finalTotal=50, itemsTotal=50 → listPriceActual = -50
        // list2: finalTotal=120, itemsTotal=120 → listPriceActual = -120
        val expectedExpenses = abs(expenseItems.first { it.listId == 1L }.listPriceActual) +
            abs(expenseItems.first { it.listId == 2L }.listPriceActual)
        assertEquals(50.0 + 120.0, expectedExpenses, 0.001)

        // Income total = sum of listPriceActual for income lists
        val expectedIncome = incomeItems.first().listPriceActual
        assertEquals(1000.0, expectedIncome, 0.001)

        // Verify individual itemTotals are raw (no ratio applied)
        assertEquals(50.0, expenseItems.first { it.productId == 10L }.itemTotal, 0.001)
        assertEquals(120.0, expenseItems.first { it.productId == 20L }.itemTotal, 0.001)
        assertEquals(1000.0, incomeItems.first().itemTotal, 0.001)
    }

    // ===================================================================
    // Case 3: Store Total — sum per store
    // ===================================================================

    @Test
    fun `store total — sums listPriceActual per store`() = runTest {
        val store1 = StoreEntity(id = 1L, name = "SuperMart", logoPath = null)
        val store2 = StoreEntity(id = 2L, name = "TechShop", logoPath = null)

        val list1 = createListEntity(id = 1L, name = "Groceries", finalTotal = 30.0, storeId = 1L, purchaseDate = 1_500_000L)
        val list2 = createListEntity(id = 2L, name = "Snacks", finalTotal = 15.0, storeId = 1L, purchaseDate = 1_550_000L)
        val list3 = createListEntity(id = 3L, name = "Gadgets", finalTotal = 80.0, storeId = 2L, purchaseDate = 1_600_000L)

        val item1 = createListItem(id = 1L, listId = 1L, productId = 10L, productName = "Bread", quantity = 2.0, price = 15.0)
        val item2 = createListItem(id = 2L, listId = 2L, productId = 20L, productName = "Chips", quantity = 3.0, price = 5.0)
        val item3 = createListItem(id = 3L, listId = 3L, productId = 30L, productName = "Mouse", quantity = 1.0, price = 80.0)

        runBlocking {
            whenever(shoppingListRepository.getFinishedListsInTimeRange(any(), any())).doReturn(listOf(list1, list2, list3))
            whenever(shoppingListRepository.getItemsWithProductForListsSync(any())).doReturn(listOf(item1, item2, item3))
            whenever(categoryRepository.getAllCategoriesOnce()).doReturn(emptyList())
            whenever(storeRepository.getAllStoresOnce()).doReturn(listOf(store1, store2))
            whenever(preferencesManager.getActualPriceRule()).doReturn("PURCHASE_PRICE")
        }

        val result = computeAdjustedItems(0L, 2_000_000L, shoppingListRepository, categoryRepository, storeRepository, preferencesManager)

        // Group by store and sum |listPriceActual| for expense lists
        val storeTotals = result
            .filter { !it.isIncome }
            .groupBy { it.storeId }
            .mapValues { (_, items) ->
                items.groupBy { it.listId }.values.sumOf { abs(it.first().listPriceActual) }
            }

        // Store 1: lists 1 (30) + 2 (15) = 45
        assertEquals(45.0, storeTotals[1L] ?: 0.0, 0.001)
        // Store 2: list 3 (80) = 80
        assertEquals(80.0, storeTotals[2L] ?: 0.0, 0.001)

        // Verify store names are populated
        val store1Item = result.first { it.storeId == 1L }
        assertEquals("SuperMart", store1Item.storeName)
        val store2Item = result.first { it.storeId == 2L }
        assertEquals("TechShop", store2Item.storeName)
    }

    // ===================================================================
    // Case 4: Category Total — sum of raw itemTotal (no ratio)
    // ===================================================================

    @Test
    fun `category total — sum of raw itemTotal per category, no ratio`() = runTest {
        val food = CategoryEntity(id = 1L, name = "Food", color = "#FFF", parentId = null)
        val dairy = CategoryEntity(id = 2L, name = "Dairy", color = "#F00", parentId = 1L)

        val list = createListEntity(id = 1L, finalTotal = 50.0, purchaseDate = 1_500_000L)

        val milk = createListItem(id = 1L, listId = 1L, productId = 10L, productName = "Milk",
            quantity = 2.0, price = 3.0, categoryId = 2L)
        val bread = createListItem(id = 2L, listId = 1L, productId = 20L, productName = "Bread",
            quantity = 3.0, price = 2.0, categoryId = 1L)
        val unknown = createListItem(id = 3L, listId = 1L, productId = 30L, productName = "Unknown",
            quantity = 1.0, price = 5.0, categoryId = null)

        runBlocking {
            whenever(shoppingListRepository.getFinishedListsInTimeRange(any(), any())).doReturn(listOf(list))
            whenever(shoppingListRepository.getItemsWithProductForListsSync(any())).doReturn(listOf(milk, bread, unknown))
            whenever(categoryRepository.getAllCategoriesOnce()).doReturn(listOf(food, dairy))
            whenever(storeRepository.getAllStoresOnce()).doReturn(emptyList())
            whenever(preferencesManager.getActualPriceRule()).doReturn("PURCHASE_PRICE")
        }

        val result = computeAdjustedItems(0L, 2_000_000L, shoppingListRepository, categoryRepository, storeRepository, preferencesManager)

        // Category totals = sum of raw itemTotal (no ratio)
        // finalTotal=50, but items sum = 6+6+5=17, so old ratio would have scaled up
        // New behavior: no ratio

        val catTotals = result.groupBy { it.categoryId }
            .mapValues { (_, items) -> items.sumOf { it.itemTotal } }

        // Dairy (id=2): Milk 2*3 = 6.0
        assertEquals(6.0, catTotals[2L] ?: 0.0, 0.001)
        // Food (id=1): Bread 3*2 = 6.0
        assertEquals(6.0, catTotals[1L] ?: 0.0, 0.001)
        // Uncategorized (null): Unknown 1*5 = 5.0
        assertEquals(5.0, catTotals[null] ?: 0.0, 0.001)

        // Verify category names are populated
        assertEquals("Dairy", result.first { it.categoryId == 2L }.categoryName)
        assertEquals("Food", result.first { it.categoryId == 1L }.categoryName)
        assertEquals("Uncategorized", result.first { it.categoryId == null }.categoryName)

        // listPriceActual should reflect the list-level total (50.0, which is > 17.0 items sum)
        assertEquals(-50.0, result.first().listPriceActual, 0.001)

        // But itemTotal should NOT be scaled by ratio
        assertEquals(6.0, result.first { it.productId == 10L }.itemTotal, 0.001)
    }

    // ===================================================================
    // Case 5: Ratio removed — regression test for the original bug
    // ===================================================================

    @Test
    fun `ratio removed — itemTotal ignores finalTotal, listPriceActual uses it`() = runTest {
        // Scenario from the bug: price=0.59, qty=2, discount=0.20
        // Item total should be 0.98 regardless of finalTotal
        val list = createListEntity(id = 1L, finalTotal = 1.05, purchaseDate = 1_500_000L)

        val item = createListItem(id = 1L, listId = 1L, productId = 10L, productName = "Item",
            quantity = 2.0, price = 0.59, discount = 0.20)

        runBlocking {
            whenever(shoppingListRepository.getFinishedListsInTimeRange(any(), any())).doReturn(listOf(list))
            whenever(shoppingListRepository.getItemsWithProductForListsSync(any())).doReturn(listOf(item))
            whenever(categoryRepository.getAllCategoriesOnce()).doReturn(emptyList())
            whenever(storeRepository.getAllStoresOnce()).doReturn(emptyList())
            whenever(preferencesManager.getActualPriceRule()).doReturn("PURCHASE_PRICE")
        }

        val result = computeAdjustedItems(0L, 2_000_000L, shoppingListRepository, categoryRepository, storeRepository, preferencesManager)

        assertEquals(1, result.size)
        val adjusted = result.first()

        // itemTotal = price * quantity - discount (raw, no ratio)
        assertEquals(0.59 * 2.0 - 0.20, adjusted.itemTotal, 0.001) // 0.98

        // listPriceActual = -finalTotal = -1.05 (since purchasePrice=1.05, rule=PURCHASE_PRICE)
        assertEquals(-1.05, adjusted.listPriceActual, 0.001)

        // The old bug would have made itemTotal = rawItemTotal * (1.05 / 0.98) ≈ 1.05
        // New behavior: itemTotal stays 0.98
        assertTrue(adjusted.itemTotal < 1.0) // 0.98 < 1.0, proving ratio is gone
    }

    // ===================================================================
    // Case 6: Discount handling
    // ===================================================================

    @Test
    fun `discount handling — discount is subtracted from price times quantity`() = runTest {
        val list = createListEntity(id = 1L, finalTotal = null, purchaseDate = 1_500_000L)

        val itemWithDiscount = createListItem(id = 1L, listId = 1L, productId = 10L, productName = "Discounted",
            quantity = 5.0, price = 2.0, discount = 1.50)
        val itemWithoutDiscount = createListItem(id = 2L, listId = 1L, productId = 20L, productName = "Regular",
            quantity = 3.0, price = 4.0, discount = null)

        runBlocking {
            whenever(shoppingListRepository.getFinishedListsInTimeRange(any(), any())).doReturn(listOf(list))
            whenever(shoppingListRepository.getItemsWithProductForListsSync(any())).doReturn(listOf(itemWithDiscount, itemWithoutDiscount))
            whenever(categoryRepository.getAllCategoriesOnce()).doReturn(emptyList())
            whenever(storeRepository.getAllStoresOnce()).doReturn(emptyList())
            whenever(preferencesManager.getActualPriceRule()).doReturn("PURCHASE_PRICE")
        }

        val result = computeAdjustedItems(0L, 2_000_000L, shoppingListRepository, categoryRepository, storeRepository, preferencesManager)

        val discounted = result.first { it.productId == 10L }
        assertEquals(5.0, discounted.quantity, 0.001)
        assertEquals(2.0 * 5.0 - 1.50, discounted.itemTotal, 0.001) // 8.50
        assertEquals(1.50, discounted.discount ?: 0.0, 0.001)

        val regular = result.first { it.productId == 20L }
        assertEquals(3.0, regular.quantity, 0.001)
        assertEquals(4.0 * 3.0, regular.itemTotal, 0.001) // 12.0
        assertEquals(null, regular.discount)
    }

    // ===================================================================
    // Case 7: Multiple lists with overlapping categories
    // ===================================================================

    @Test
    fun `category aggregation across multiple lists uses raw itemTotal`() = runTest {
        val food = CategoryEntity(id = 1L, name = "Food", color = "#FFF", parentId = null)

        val list1 = createListEntity(id = 1L, finalTotal = null, purchaseDate = 1_500_000L)
        val list2 = createListEntity(id = 2L, finalTotal = null, purchaseDate = 1_600_000L)

        val apple1 = createListItem(id = 1L, listId = 1L, productId = 10L, productName = "Apple", quantity = 1.0, price = 2.0, categoryId = 1L)
        val apple2 = createListItem(id = 2L, listId = 2L, productId = 10L, productName = "Apple", quantity = 1.0, price = 2.5, categoryId = 1L)

        runBlocking {
            whenever(shoppingListRepository.getFinishedListsInTimeRange(any(), any())).doReturn(listOf(list1, list2))
            whenever(shoppingListRepository.getItemsWithProductForListsSync(any())).doReturn(listOf(apple1, apple2))
            whenever(categoryRepository.getAllCategoriesOnce()).doReturn(listOf(food))
            whenever(storeRepository.getAllStoresOnce()).doReturn(emptyList())
            whenever(preferencesManager.getActualPriceRule()).doReturn("PURCHASE_PRICE")
        }

        val result = computeAdjustedItems(0L, 2_000_000L, shoppingListRepository, categoryRepository, storeRepository, preferencesManager)

        assertEquals(2, result.size)

        // Category Food total = sum of raw itemTotals = 2.0 + 2.5 = 4.5
        val foodTotal = result.filter { it.categoryId == 1L }.sumOf { it.itemTotal }
        assertEquals(4.5, foodTotal, 0.001)

        // Product Apple aggregated across lists: sum of itemTotals = 2.0 + 2.5 = 4.5
        val appleTotal = result.filter { it.productId == 10L }.sumOf { it.itemTotal }
        assertEquals(4.5, appleTotal, 0.001)

        // Total list-level spending across both lists
        val totalListSpending = result.groupBy { it.listId }.values.sumOf { abs(it.first().listPriceActual) }
        // list1: itemsTotal=2.0, purchasePrice=0 → listPriceActual=-2.0 → |2.0|
        // list2: itemsTotal=2.5, purchasePrice=0 → listPriceActual=-2.5 → |2.5|
        assertEquals(4.5, totalListSpending, 0.001)
    }

    // ===================================================================
    // Case 8: BIGGER_VALUE rule vs PURCHASE_PRICE rule
    // ===================================================================

    @Test
    fun `BIGGER_VALUE rule picks max of itemsTotal and finalTotal`() = runTest {
        val list = createListEntity(id = 1L, finalTotal = 100.0, purchaseDate = 1_500_000L)
        val item = createListItem(id = 1L, listId = 1L, productId = 10L, productName = "Expensive", quantity = 1.0, price = 50.0)

        runBlocking {
            whenever(shoppingListRepository.getFinishedListsInTimeRange(any(), any())).doReturn(listOf(list))
            whenever(shoppingListRepository.getItemsWithProductForListsSync(any())).doReturn(listOf(item))
            whenever(categoryRepository.getAllCategoriesOnce()).doReturn(emptyList())
            whenever(storeRepository.getAllStoresOnce()).doReturn(emptyList())
            whenever(preferencesManager.getActualPriceRule()).doReturn("BIGGER_VALUE")
        }

        val result = computeAdjustedItems(0L, 2_000_000L, shoppingListRepository, categoryRepository, storeRepository, preferencesManager)

        val adjusted = result.first()
        // BIGGER_VALUE: maxOf(itemsTotal=50, purchasePrice=100) = 100, expense → -100
        assertEquals(-100.0, adjusted.listPriceActual, 0.001)
        // itemTotal stays raw, no ratio
        assertEquals(50.0, adjusted.itemTotal, 0.001)
    }

    // ===================================================================
    // Case 9: Empty data
    // ===================================================================

    @Test
    fun `empty data returns empty list`() = runTest {
        runBlocking {
            whenever(shoppingListRepository.getFinishedListsInTimeRange(any(), any())).doReturn(emptyList())
        }

        val result = computeAdjustedItems(0L, 2_000_000L, shoppingListRepository, categoryRepository, storeRepository, preferencesManager)
        assertTrue(result.isEmpty())
    }

    // ===================================================================
    // Case 10: Unchecked items are still included in list but don't affect listPriceActual
    // ===================================================================

    @Test
    fun `unchecked items contribute to itemTotal but not to itemsTotal in calculateActualPrice`() = runTest {
        val list = createListEntity(id = 1L, finalTotal = null, purchaseDate = 1_500_000L)
        val checked = createListItem(id = 1L, listId = 1L, productId = 10L, productName = "Checked",
            quantity = 1.0, price = 10.0, isChecked = true)
        val unchecked = createListItem(id = 2L, listId = 1L, productId = 20L, productName = "Unchecked",
            quantity = 1.0, price = 5.0, isChecked = false)

        runBlocking {
            whenever(shoppingListRepository.getFinishedListsInTimeRange(any(), any())).doReturn(listOf(list))
            whenever(shoppingListRepository.getItemsWithProductForListsSync(any())).doReturn(listOf(checked, unchecked))
            whenever(categoryRepository.getAllCategoriesOnce()).doReturn(emptyList())
            whenever(storeRepository.getAllStoresOnce()).doReturn(emptyList())
            whenever(preferencesManager.getActualPriceRule()).doReturn("PURCHASE_PRICE")
        }

        val result = computeAdjustedItems(0L, 2_000_000L, shoppingListRepository, categoryRepository, storeRepository, preferencesManager)

        // Both items should be in result
        assertEquals(2, result.size)

        // listPriceActual is based on itemsTotal (only checked items)
        // itemsTotal = 10.0 * 1 = 10.0
        // purchasePrice = 0, so listPriceActual = -10.0
        assertEquals(-10.0, result.first().listPriceActual, 0.001)

        // Unchecked item still gets a raw itemTotal
        val uncheckedResult = result.first { it.productId == 20L }
        assertEquals(5.0, uncheckedResult.itemTotal, 0.001)

        val checkedResult = result.first { it.productId == 10L }
        assertEquals(10.0, checkedResult.itemTotal, 0.001)
    }
}
