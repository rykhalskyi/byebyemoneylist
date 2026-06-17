package com.otakeeesen.byebyemoneylist

import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.dao.ShoppingListItemWithProduct
import com.otakeeesen.byebyemoneylist.data.local.repository.*
import com.otakeeesen.byebyemoneylist.ui.viewmodel.AnalyticsViewModel
import com.otakeeesen.byebyemoneylist.ui.viewmodel.OverviewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.Mock
import org.mockito.MockitoAnnotations

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private val shoppingListRepository: ShoppingListRepository = mock()
    private val categoryRepository: CategoryRepository = mock()
    private val productRepository: ProductRepository = mock()
    private val priceRepository: PriceRepository = mock()
    private val storeRepository: StoreRepository = mock()

    private lateinit var viewModel: AnalyticsViewModel

    @Mock
    lateinit var preferencesManager: PreferencesManager

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        runBlocking {
            whenever(categoryRepository.getAllCategoriesOnce()).doReturn(emptyList())
            whenever(storeRepository.getAllStoresOnce()).doReturn(emptyList())
            whenever(shoppingListRepository.getFinishedListsInTimeRange(any(), any())).doReturn(emptyList())
            whenever(shoppingListRepository.getItemsWithProductForListsSync(any())).doReturn(emptyList())
            whenever(preferencesManager.getActualPriceRule()).doReturn("PURCHASE_PRICE")
        }
        viewModel = AnalyticsViewModel(
            shoppingListRepository,
            categoryRepository,
            productRepository,
            priceRepository,
            storeRepository,
            preferencesManager,
            testDispatcher
        )
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test drilldown aggregates items to correct sub-categories with nested category resolution`() = runTest {
        val food = CategoryEntity(id = 1L, name = "Food", color = "#FFFFFF", parentId = null)
        val dairy = CategoryEntity(id = 2L, name = "Dairy", color = "#FF0000", parentId = 1L)
        val meat = CategoryEntity(id = 3L, name = "Meat", color = "#00FF00", parentId = 1L)
        val cheese = CategoryEntity(id = 4L, name = "Cheese", color = "#0000FF", parentId = 2L)
        whenever(categoryRepository.getAllCategoriesOnce()).doReturn(listOf(food, dairy, meat, cheese))

        val list = ShoppingListEntity(
            id = 1L, name = "Weekly Shop", createDate = System.currentTimeMillis(),
            position = 1, purchaseDate = null, storeId = null, finalTotal = 21.0
        )
        whenever(shoppingListRepository.getFinishedListsInTimeRange(any(), any())).doReturn(listOf(list))

        val milk = ShoppingListItemWithProduct(
            id = 1L, shoppingListId = 1L, productId = 1L, quantity = 1.0, isChecked = true,
            position = 0, productName = "Milk", productPicturePath = null, productStatus = "added",
            productIsSubscription = false, productIsFavorite = false, itemPrice = null,
            price = 5.0, discount = null, customName = null, productCategoryId = 2L
        )
        val cheddar = ShoppingListItemWithProduct(
            id = 2L, shoppingListId = 1L, productId = 2L, quantity = 2.0, isChecked = true,
            position = 1, productName = "Cheddar", productPicturePath = null, productStatus = "added",
            productIsSubscription = false, productIsFavorite = false, itemPrice = null,
            price = 3.0, discount = null, customName = null, productCategoryId = 4L
        )
        val chicken = ShoppingListItemWithProduct(
            id = 3L, shoppingListId = 1L, productId = 3L, quantity = 1.0, isChecked = true,
            position = 2, productName = "Chicken", productPicturePath = null, productStatus = "added",
            productIsSubscription = false, productIsFavorite = false, itemPrice = null,
            price = 10.0, discount = null, customName = null, productCategoryId = 3L
        )
        whenever(shoppingListRepository.getItemsWithProductForListsSync(any())).doReturn(listOf(milk, cheddar, chicken))

        viewModel.setRootCategory(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        with(viewModel.uiState.value) {
            assertEquals(1L, currentRootCategoryId)
            assertFalse(isLoading)
            println("Actual subCategorySpending: $subCategorySpending")
            assertEquals(mapOf(2L to 11.0, 3L to 10.0), subCategorySpending)
            assertEquals(mapOf(2L to 3.0, 3L to 1.0), subCategoryQuantity)
        }
    }

    @Test
    fun `test drilldown with income list separates into subCategoryIncome`() = runTest {
        val incomeRoot = CategoryEntity(id = 1L, name = "Income", color = "#FFFFFF", parentId = null)
        val salaryCat = CategoryEntity(id = 2L, name = "Salary", color = "#FF0000", parentId = 1L)
        whenever(categoryRepository.getAllCategoriesOnce()).doReturn(listOf(incomeRoot, salaryCat))

        val list = ShoppingListEntity(
            id = 1L, name = "Paycheck", createDate = System.currentTimeMillis(),
            position = 1, purchaseDate = null, storeId = null, finalTotal = 5000.0, isIncome = true
        )
        whenever(shoppingListRepository.getFinishedListsInTimeRange(any(), any())).doReturn(listOf(list))

        val item = ShoppingListItemWithProduct(
            id = 1L, shoppingListId = 1L, productId = 1L, quantity = 1.0, isChecked = true,
            position = 0, productName = "Salary", productPicturePath = null, productStatus = "added",
            productIsSubscription = false, productIsFavorite = false, itemPrice = null,
            price = 5000.0, discount = null, customName = null, productCategoryId = 2L
        )
        whenever(shoppingListRepository.getItemsWithProductForListsSync(any())).doReturn(listOf(item))

        viewModel.setRootCategory(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        with(viewModel.uiState.value) {
            assertEquals(mapOf(2L to 5000.0), subCategoryIncome)
            assertTrue(subCategorySpending.isEmpty())
            assertEquals(mapOf(2L to 1.0), subCategoryQuantity)
        }
    }

    @Test
    fun `test drilldown to uncategorized root ignores remainder`() = runTest {
        val list = ShoppingListEntity(
            id = 1L, name = "Misc", createDate = System.currentTimeMillis(),
            position = 1, purchaseDate = null, storeId = null, finalTotal = 15.0
        )
        whenever(shoppingListRepository.getFinishedListsInTimeRange(any(), any())).doReturn(listOf(list))

        val item = ShoppingListItemWithProduct(
            id = 1L, shoppingListId = 1L, productId = 1L, quantity = 1.0, isChecked = true,
            position = 0, productName = "Item", productPicturePath = null, productStatus = "added",
            productIsSubscription = false, productIsFavorite = false, itemPrice = null,
            price = 10.0, discount = null, customName = null, productCategoryId = null
        )
        whenever(shoppingListRepository.getItemsWithProductForListsSync(any())).doReturn(listOf(item))

        viewModel.setRootCategory(-1L)
        testDispatcher.scheduler.advanceUntilIdle()

        with(viewModel.uiState.value) {
            assertEquals(-1L, currentRootCategoryId)
            assertFalse(isLoading)
            // Expecting 15.0 because PURCHASE_PRICE rule uses finalTotal (15.0)
            assertEquals(15.0, totalSpent, 0.001)
            assertEquals(15.0, subCategorySpending[-1L] ?: 0.0, 0.001)
            assertEquals(1.0, subCategoryQuantity[-1L] ?: 0.0, 0.001)
        }

    }

    @Test
    fun `test toggle search panel`() = runTest {
        assertFalse(viewModel.uiState.value.showSearchPanel)
        assertFalse(viewModel.uiState.value.showStatsFilterPanel)

        // Toggle on search
        viewModel.toggleSearchPanel()
        assertTrue(viewModel.uiState.value.showSearchPanel)
        assertFalse(viewModel.uiState.value.showStatsFilterPanel)

        // Toggle on filter while search is open (should close search)
        viewModel.toggleStatsFilterPanel()
        assertFalse(viewModel.uiState.value.showSearchPanel)
        assertTrue(viewModel.uiState.value.showStatsFilterPanel)

        // Toggle off filter
        viewModel.toggleStatsFilterPanel()
        assertFalse(viewModel.uiState.value.showSearchPanel)
        assertFalse(viewModel.uiState.value.showStatsFilterPanel)
    }

    @Test
    fun `test income lists are excluded from store and list breakdowns`() = runTest {
        val storeId = 1L
        val incomeList = ShoppingListEntity(
            id = 1L, name = "Income", createDate = System.currentTimeMillis(),
            position = 1, purchaseDate = null, storeId = storeId, finalTotal = 1000.0, isIncome = true
        )
        val expenseList = ShoppingListEntity(
            id = 2L, name = "Expense", createDate = System.currentTimeMillis(),
            position = 2, purchaseDate = null, storeId = storeId, finalTotal = 50.0, isIncome = false
        )
        val item1 = ShoppingListItemWithProduct(
            id = 1L, shoppingListId = 1L, productId = 1L, quantity = 1.0, isChecked = true,
            position = 0, productName = "Salary", productPicturePath = null, productStatus = "added",
            productIsSubscription = false, productIsFavorite = false, itemPrice = null,
            price = 1000.0, discount = null, customName = null, productCategoryId = null
        )
        val item2 = ShoppingListItemWithProduct(
            id = 2L, shoppingListId = 2L, productId = 2L, quantity = 1.0, isChecked = true,
            position = 0, productName = "Bread", productPicturePath = null, productStatus = "added",
            productIsSubscription = false, productIsFavorite = false, itemPrice = null,
            price = 50.0, discount = null, customName = null, productCategoryId = null
        )
        whenever(shoppingListRepository.getFinishedListsInTimeRange(any(), any())).doReturn(listOf(incomeList, expenseList))
        whenever(shoppingListRepository.getItemsWithProductForListsSync(any())).doReturn(listOf(item1, item2))
        whenever(preferencesManager.getActualPriceRule()).doReturn("PURCHASE_PRICE")

        // Need to trigger loadAnalyticsData again or re-init the viewmodel
        viewModel = AnalyticsViewModel(
            shoppingListRepository,
            categoryRepository,
            productRepository,
            priceRepository,
            storeRepository,
            preferencesManager,
            testDispatcher
        )
        testDispatcher.scheduler.advanceUntilIdle()

        with(viewModel.uiState.value) {
            // Check that store breakdown only contains expense
            assertEquals(50.0, storeSpending[storeId] ?: 0.0, 0.001)
            // Check that list breakdown only contains expense
            assertTrue(listSpending.containsKey(2L))
            assertFalse(listSpending.containsKey(1L))
        }
    }
}
