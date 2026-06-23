package com.otakeeesen.byebyemoneylist

import com.otakeeesen.byebyemoneylist.data.agent.AgentAction
import com.otakeeesen.byebyemoneylist.data.agent.AgentQuery
import com.otakeeesen.byebyemoneylist.data.agent.AgentQueryExecutor
import com.otakeeesen.byebyemoneylist.data.agent.AgentResult
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import com.otakeeesen.byebyemoneylist.data.local.dao.ShoppingListItemWithProduct
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.PriceRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ProductRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.StoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AgentQueryExecutorTest {

    private val testDispatcher = StandardTestDispatcher()

    private val shoppingListRepository: ShoppingListRepository = mock()
    private val categoryRepository: CategoryRepository = mock()
    private val productRepository: ProductRepository = mock()
    private val priceRepository: PriceRepository = mock()
    private val storeRepository: StoreRepository = mock()
    private val preferencesManager: PreferencesManager = mock()

    private lateinit var executor: AgentQueryExecutor

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        runBlocking {
            whenever(categoryRepository.getAllCategoriesOnce()).doReturn(emptyList())
            whenever(storeRepository.getAllStoresOnce()).doReturn(emptyList())
            whenever(productRepository.getAllProductsOnce()).doReturn(emptyList())
            whenever(productRepository.findProductNamesByAlias(any())).doReturn(emptyList())
            whenever(shoppingListRepository.getFinishedListsInTimeRange(any(), any())).doReturn(emptyList())
            whenever(preferencesManager.getCurrencySymbol()).doReturn("$")
            whenever(preferencesManager.getActualPriceRule()).doReturn("PURCHASE_PRICE")
        }
        executor = AgentQueryExecutor(
            shoppingListRepository,
            categoryRepository,
            productRepository,
            priceRepository,
            storeRepository,
            preferencesManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ============================================================
    // Helper: create a minimal expense list with one item
    // ============================================================
    private fun setupSingleExpenseList(
        listId: Long = 1L,
        itemId: Long = 1L,
        productId: Long = 1L,
        productName: String = "Milk",
        quantity: Double = 1.0,
        price: Double = 5.0,
        storeId: Long? = null,
        storeName: String? = null,
        categoryId: Long? = null,
        isFinished: Boolean = true,
        isIncome: Boolean = false
    ) {
        val list = ShoppingListEntity(
            id = listId,
            name = "Test List",
            createDate = 1_000_000L,
            purchaseDate = 1_000_000L,
            storeId = storeId,
            isFinished = isFinished,
            finalTotal = price * quantity,
            isIncome = isIncome
        )
        val item = ShoppingListItemWithProduct(
            id = itemId,
            shoppingListId = listId,
            productId = productId,
            quantity = quantity,
            isChecked = true,
            position = 0,
            productName = productName,
            productPicturePath = null,
            productStatus = "reviewed",
            productIsSubscription = false,
            productIsFavorite = false,
            itemPrice = null,
            price = price,
            discount = null,
            customName = null,
            productCategoryId = categoryId
        )
        runBlocking {
            whenever(shoppingListRepository.getFinishedListsInTimeRange(any(), any())).doReturn(listOf(list))
            whenever(shoppingListRepository.getItemsWithProductForListsSync(any())).doReturn(listOf(item))
            if (storeId != null && storeName != null) {
                val store = StoreEntity(id = storeId, name = storeName, logoPath = null)
                whenever(storeRepository.getAllStoresOnce()).doReturn(listOf(store))
            }
        }
    }

    // ============================================================
    // No Data / Empty
    // ============================================================

    @Test
    fun `empty data returns zero for GET_TOTAL_SPENT`() = runTest {
        val result = executor.execute(AgentQuery(action = AgentAction.GET_TOTAL_SPENT))
        assertTrue(result is AgentResult.TotalAmount)
        result as AgentResult.TotalAmount
        assertEquals(0.0, result.amount, 0.001)
        assertEquals("spending", result.type)
        assertEquals(0.0, result.totalQuantity, 0.001)
    }

    @Test
    fun `empty data returns zero for GET_TOTAL_INCOME`() = runTest {
        val result = executor.execute(AgentQuery(action = AgentAction.GET_TOTAL_INCOME))
        assertTrue(result is AgentResult.TotalAmount)
        result as AgentResult.TotalAmount
        assertEquals(0.0, result.amount, 0.001)
        assertEquals("income", result.type)
    }

    @Test
    fun `empty data returns empty PurchaseList`() = runTest {
        val result = executor.execute(AgentQuery(action = AgentAction.LIST_PURCHASES))
        assertTrue(result is AgentResult.PurchaseList)
        result as AgentResult.PurchaseList
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun `empty data returns empty TopItems for categories`() = runTest {
        val result = executor.execute(AgentQuery(action = AgentAction.GET_TOP_CATEGORIES))
        assertTrue(result is AgentResult.TopItems)
        result as AgentResult.TopItems
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun `empty data returns empty NamedList for categories`() = runTest {
        val result = executor.execute(AgentQuery(action = AgentAction.GET_CATEGORIES))
        assertTrue(result is AgentResult.NamedList)
        result as AgentResult.NamedList
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun `empty data returns empty NamedList for products`() = runTest {
        val result = executor.execute(AgentQuery(action = AgentAction.GET_PRODUCTS))
        assertTrue(result is AgentResult.NamedList)
        result as AgentResult.NamedList
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun `empty data returns empty NamedList for stores`() = runTest {
        val result = executor.execute(AgentQuery(action = AgentAction.GET_STORES))
        assertTrue(result is AgentResult.NamedList)
        result as AgentResult.NamedList
        assertTrue(result.items.isEmpty())
    }

    // ============================================================
    // GET_TOTAL_SPENT
    // ============================================================

    @Test
    fun `GET_TOTAL_SPENT sums single expense`() = runTest {
        setupSingleExpenseList(price = 12.5, quantity = 2.0)
        val result = executor.execute(AgentQuery(action = AgentAction.GET_TOTAL_SPENT))
        assertTrue(result is AgentResult.TotalAmount)
        result as AgentResult.TotalAmount
        assertEquals(25.0, result.amount, 0.001)
        assertEquals(2.0, result.totalQuantity, 0.001)
    }

    @Test
    fun `GET_TOTAL_SPENT excludes income lists`() = runTest {
        setupSingleExpenseList(isIncome = true, price = 100.0)
        val result = executor.execute(AgentQuery(action = AgentAction.GET_TOTAL_SPENT))
        assertTrue(result is AgentResult.TotalAmount)
        result as AgentResult.TotalAmount
        assertEquals(0.0, result.amount, 0.001)
    }

    // ============================================================
    // GET_TOTAL_INCOME
    // ============================================================

    @Test
    fun `GET_TOTAL_INCOME sums income only`() = runTest {
        setupSingleExpenseList(isIncome = true, price = 5000.0)
        val result = executor.execute(AgentQuery(action = AgentAction.GET_TOTAL_INCOME))
        assertTrue(result is AgentResult.TotalAmount)
        result as AgentResult.TotalAmount
        assertEquals(5000.0, result.amount, 0.001)
        assertEquals("income", result.type)
    }

    @Test
    fun `GET_TOTAL_INCOME excludes expenses`() = runTest {
        setupSingleExpenseList(isIncome = false, price = 50.0)
        val result = executor.execute(AgentQuery(action = AgentAction.GET_TOTAL_INCOME))
        assertTrue(result is AgentResult.TotalAmount)
        result as AgentResult.TotalAmount
        assertEquals(0.0, result.amount, 0.001)
    }

    // ============================================================
    // LIST_PURCHASES
    // ============================================================

    @Test
    fun `LIST_PURCHASES returns purchase items sorted descending by date`() = runTest {
        val list1 = ShoppingListEntity(id = 1L, name = "Shop 1", createDate = 1_000_000L, purchaseDate = 1_000_000L, storeId = null, isFinished = true, finalTotal = 10.0)
        val list2 = ShoppingListEntity(id = 2L,             name = "Shop 2", createDate = 2_000_000L, purchaseDate = 2_000_000L, storeId = null, isFinished = true, finalTotal = 20.0)

        val item1 = ShoppingListItemWithProduct(id = 1L, shoppingListId = 1L, productId = 1L, quantity = 1.0, isChecked = true, position = 0, productName = "Item A", productPicturePath = null, productStatus = "reviewed", productIsSubscription = false, productIsFavorite = false, itemPrice = null, price = 10.0, discount = null, customName = null, productCategoryId = null)
        val item2 = ShoppingListItemWithProduct(id = 2L, shoppingListId = 2L, productId = 2L, quantity = 2.0, isChecked = true, position = 0, productName = "Item B", productPicturePath = null, productStatus = "reviewed", productIsSubscription = false, productIsFavorite = false, itemPrice = null, price = 10.0, discount = null, customName = null, productCategoryId = null)

        runBlocking {
            whenever(shoppingListRepository.getFinishedListsInTimeRange(any(), any())).doReturn(listOf(list1, list2))
            whenever(shoppingListRepository.getItemsWithProductForListsSync(any())).doReturn(listOf(item1, item2))
        }

        val result = executor.execute(AgentQuery(action = AgentAction.LIST_PURCHASES))
        assertTrue(result is AgentResult.PurchaseList)
        result as AgentResult.PurchaseList
        assertEquals(2, result.items.size)
        assertEquals("Item B", result.items[0].productName) // newest first
        assertEquals("Item A", result.items[1].productName)
    }

    @Test
    fun `LIST_PURCHASES respects limit parameter`() = runTest {
        setupSingleExpenseList()
        val result = executor.execute(AgentQuery(action = AgentAction.LIST_PURCHASES, limit = 1))
        assertTrue(result is AgentResult.PurchaseList)
        result as AgentResult.PurchaseList
        assertTrue(result.items.size <= 1)
    }

    // ============================================================
    // GET_TOP_CATEGORIES
    // ============================================================

    @Test
    fun `GET_TOP_CATEGORIES groups by root category`() = runTest {
        val food = CategoryEntity(id = 1L, name = "Food", parentId = null)
        val dairy = CategoryEntity(id = 2L, name = "Dairy", parentId = 1L)
        runBlocking {
            whenever(categoryRepository.getAllCategoriesOnce()).doReturn(listOf(food, dairy))
        }

        val list = ShoppingListEntity(id = 1L, name = "Groceries", createDate = 1_000_000L, purchaseDate = 1_000_000L, storeId = null, isFinished = true, finalTotal = 15.0)
        val item = ShoppingListItemWithProduct(id = 1L, shoppingListId = 1L, productId = 1L, quantity = 1.0, isChecked = true, position = 0, productName = "Milk", productPicturePath = null, productStatus = "reviewed", productIsSubscription = false, productIsFavorite = false, itemPrice = null, price = 15.0, discount = null, customName = null, productCategoryId = 2L)

        runBlocking {
            whenever(shoppingListRepository.getFinishedListsInTimeRange(any(), any())).doReturn(listOf(list))
            whenever(shoppingListRepository.getItemsWithProductForListsSync(any())).doReturn(listOf(item))
        }

        val result = executor.execute(AgentQuery(action = AgentAction.GET_TOP_CATEGORIES))
        assertTrue(result is AgentResult.TopItems)
        result as AgentResult.TopItems
        assertEquals(1, result.items.size)
        assertEquals("Food", result.items[0].name) // resolved to root
        assertEquals("category", result.groupType)
    }

    // ============================================================
    // GET_TOP_STORES
    // ============================================================

    @Test
    fun `GET_TOP_STORES groups by store name`() = runTest {
        setupSingleExpenseList(storeId = 10L, storeName = "SuperMart", price = 50.0)

        val result = executor.execute(AgentQuery(action = AgentAction.GET_TOP_STORES))
        assertTrue(result is AgentResult.TopItems)
        result as AgentResult.TopItems
        assertEquals(1, result.items.size)
        assertEquals("SuperMart", result.items[0].name)
        assertEquals("store", result.groupType)
    }

    // ============================================================
    // GET_TOP_PRODUCTS
    // ============================================================

    @Test
    fun `GET_TOP_PRODUCTS groups by product name`() = runTest {
        setupSingleExpenseList(productName = "Banana", price = 3.0, quantity = 5.0)

        val result = executor.execute(AgentQuery(action = AgentAction.GET_TOP_PRODUCTS))
        assertTrue(result is AgentResult.TopItems)
        result as AgentResult.TopItems
        assertEquals(1, result.items.size)
        assertEquals("Banana", result.items[0].name)
        assertEquals(15.0, result.items[0].totalSpent, 0.001)
        assertEquals(5.0, result.items[0].quantity, 0.001)
    }

    // ============================================================
    // GET_PRODUCT_PRICE_HISTORY
    // ============================================================

    @Test
    fun `GET_PRODUCT_PRICE_HISTORY returns price points for matching product`() = runTest {
        setupSingleExpenseList(productName = "Eggs", price = 4.0)

        val result = executor.execute(AgentQuery(action = AgentAction.GET_PRODUCT_PRICE_HISTORY, productName = "Eggs"))
        assertTrue(result is AgentResult.PriceHistory)
        result as AgentResult.PriceHistory
        assertEquals("Eggs", result.productName)
        assertEquals(1, result.items.size)
        assertEquals(4.0, result.items[0].price, 0.001)
    }

    @Test
    fun `GET_PRODUCT_PRICE_HISTORY with no match returns empty`() = runTest {
        setupSingleExpenseList(productName = "Eggs")

        val result = executor.execute(AgentQuery(action = AgentAction.GET_PRODUCT_PRICE_HISTORY, productName = "Nonexistent"))
        assertTrue(result is AgentResult.PriceHistory)
        result as AgentResult.PriceHistory
        assertTrue(result.items.isEmpty())
    }

    // ============================================================
    // GET_CATEGORIES / GET_PRODUCTS / GET_STORES
    // ============================================================

    @Test
    fun `GET_CATEGORIES returns NamedList`() = runTest {
        val cat = CategoryEntity(id = 1L, name = "Food", parentId = null)
        runBlocking {
            whenever(categoryRepository.getAllCategoriesOnce()).doReturn(listOf(cat))
        }
        setupSingleExpenseList()

        val result = executor.execute(AgentQuery(action = AgentAction.GET_CATEGORIES))
        assertTrue(result is AgentResult.NamedList)
        result as AgentResult.NamedList
        assertEquals(1, result.items.size)
        assertEquals("Food", result.items[0].name)
        assertEquals("category", result.listType)
    }

    @Test
    fun `GET_PRODUCTS returns NamedList`() = runTest {
        val prod = ProductEntity(id = 1L, name = "Milk", barcode = "", picturePath = null)
        runBlocking { whenever(productRepository.getAllProductsOnce()).doReturn(listOf(prod)) }
        setupSingleExpenseList()

        val result = executor.execute(AgentQuery(action = AgentAction.GET_PRODUCTS))
        assertTrue(result is AgentResult.NamedList)
        result as AgentResult.NamedList
        assertEquals(1, result.items.size)
        assertEquals("Milk", result.items[0].name)
        assertEquals("product", result.listType)
    }

    @Test
    fun `GET_STORES returns NamedList`() = runTest {
        val store = StoreEntity(id = 1L, name = "Walmart", logoPath = null)
        runBlocking { whenever(storeRepository.getAllStoresOnce()).doReturn(listOf(store)) }
        setupSingleExpenseList()

        val result = executor.execute(AgentQuery(action = AgentAction.GET_STORES))
        assertTrue(result is AgentResult.NamedList)
        result as AgentResult.NamedList
        assertEquals(1, result.items.size)
        assertEquals("Walmart", result.items[0].name)
        assertEquals("store", result.listType)
    }

    // ============================================================
    // GET_SPENT_BY_PRODUCT / GET_SPENT_BY_CATEGORY
    // ============================================================

    @Test
    fun `GET_SPENT_BY_PRODUCT groups spending by product name`() = runTest {
        setupSingleExpenseList(productName = "Chocolate", quantity = 3.0, price = 2.0)

        val result = executor.execute(AgentQuery(action = AgentAction.GET_SPENT_BY_PRODUCT))
        assertTrue(result is AgentResult.TopItems)
        result as AgentResult.TopItems
        assertEquals(1, result.items.size)
        assertEquals("Chocolate", result.items[0].name)
        assertEquals(6.0, result.items[0].totalSpent, 0.001)
        assertEquals(1, result.items[0].items.size)
        assertEquals("Chocolate", result.items[0].items[0].productName)
        assertEquals(3.0, result.items[0].items[0].quantity, 0.001)
        assertEquals(2.0, result.items[0].items[0].price, 0.001)
    }

    @Test
    fun `GET_SPENT_BY_CATEGORY groups by category name`() = runTest {
        val cat = CategoryEntity(id = 5L, name = "Drinks", parentId = null)
        runBlocking { whenever(categoryRepository.getAllCategoriesOnce()).doReturn(listOf(cat)) }
        setupSingleExpenseList(productName = "Cola", categoryId = 5L, price = 3.0)

        val result = executor.execute(AgentQuery(action = AgentAction.GET_SPENT_BY_CATEGORY))
        assertTrue(result is AgentResult.TopItems)
        result as AgentResult.TopItems
        assertEquals(1, result.items.size)
        assertEquals("Drinks", result.items[0].name)
        assertEquals(1, result.items[0].items.size)
        assertEquals("Cola", result.items[0].items[0].productName)
        assertEquals(3.0, result.items[0].items[0].price, 0.001)
    }

    // ============================================================
    // REJECT_NOT_RELEVANT
    // ============================================================

    @Test
    fun `REJECT_NOT_RELEVANT returns Error`() = runTest {
        val result = executor.execute(AgentQuery(action = AgentAction.REJECT_NOT_RELEVANT))
        assertTrue(result is AgentResult.Error)
        result as AgentResult.Error
        assertEquals("Not relevant", result.message)
    }

    // ============================================================
    // Date range filtering
    // ============================================================

    @Test
    fun `startDate and endDate do not throw on valid date strings`() = runTest {
        setupSingleExpenseList()
        val result = executor.execute(
            AgentQuery(
                action = AgentAction.GET_TOTAL_SPENT,
                startDate = "2024-01-15",
                endDate = "2024-06-15"
            )
        )
        assertTrue(result is AgentResult.TotalAmount)
    }

    // ============================================================
    // Category filtering
    // ============================================================

    @Test
    fun `filters by category name including descendants`() = runTest {
        val food = CategoryEntity(id = 1L, name = "Food", parentId = null)
        val dairy = CategoryEntity(id = 2L, name = "Dairy", parentId = 1L)
        val electronics = CategoryEntity(id = 3L, name = "Electronics", parentId = null)
        runBlocking {
            whenever(categoryRepository.getAllCategoriesOnce()).doReturn(listOf(food, dairy, electronics))
        }

        val list = ShoppingListEntity(id = 1L, name = "Shopping", createDate = 1_000_000L, purchaseDate = 1_000_000L, storeId = null, isFinished = true, finalTotal = 20.0)
        val item1 = ShoppingListItemWithProduct(id = 1L, shoppingListId = 1L, productId = 1L, quantity = 1.0, isChecked = true, position = 0, productName = "Milk", productPicturePath = null, productStatus = "reviewed", productIsSubscription = false, productIsFavorite = false, itemPrice = null, price = 10.0, discount = null, customName = null, productCategoryId = 2L)
        val item2 = ShoppingListItemWithProduct(id = 2L, shoppingListId = 1L, productId = 2L, quantity = 1.0, isChecked = true, position = 1, productName = "TV", productPicturePath = null, productStatus = "reviewed", productIsSubscription = false, productIsFavorite = false, itemPrice = null, price = 10.0, discount = null, customName = null, productCategoryId = 3L)

        runBlocking {
            whenever(shoppingListRepository.getFinishedListsInTimeRange(any(), any())).doReturn(listOf(list))
            whenever(shoppingListRepository.getItemsWithProductForListsSync(any())).doReturn(listOf(item1, item2))
        }

        val result = executor.execute(
            AgentQuery(action = AgentAction.GET_TOTAL_SPENT, categoryName = "Food")
        )
        assertTrue(result is AgentResult.TotalAmount)
        result as AgentResult.TotalAmount
        assertEquals(10.0, result.amount, 0.001) // only dairy (child of food)
    }
}
