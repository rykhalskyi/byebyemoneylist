package com.otakeeesen.byebyemoneylist.ui.viewmodel

import com.otakeeesen.byebyemoneylist.data.ShoppingList
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.PriceRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ProductRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.StoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AddProductViewModelTest {

    private val listId = 1L
    private val testScheduler = TestCoroutineScheduler()
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var productRepository: ProductRepository
    private lateinit var shoppingListRepository: ShoppingListRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var priceRepository: PriceRepository
    private lateinit var storeRepository: StoreRepository
    private lateinit var viewModel: AddProductViewModel

    @Before
    fun setup() {
        testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState correctly filters by isIncome`() = runTest(testDispatcher) {
        productRepository = mock()
        shoppingListRepository = mock()
        categoryRepository = mock { on { allCategories } doReturn flowOf(emptyList()) }
        priceRepository = mock()
        storeRepository = mock { on { allStores } doReturn flowOf(emptyList()) }

        val shoppingList = com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity(
            id = listId,
            name = "Income List",
            createDate = 0L,
            purchaseDate = null,
            storeId = null,
            isIncome = true
        )
        whenever(shoppingListRepository.getShoppingListById(listId)).thenReturn(shoppingList)

        val incomeProducts = listOf(ProductEntity(id = 1, name = "Income Product", barcode = "", picturePath = null, isIncome = true))
        whenever(productRepository.getProducts(isSubscription = false, isIncome = true)).thenReturn(flowOf(incomeProducts))

        viewModel = AddProductViewModel(
            listId, productRepository, shoppingListRepository,
            categoryRepository, priceRepository, storeRepository,
            ioDispatcher = testDispatcher
        )

        backgroundScope.launch { viewModel.uiState.collect { } }

        advanceUntilIdle()
        advanceTimeBy(300)
        advanceUntilIdle()

        println("Actual isIncomeList: " + viewModel.uiState.value.isIncomeList)
        assertEquals("Expected isIncomeList to be true", true, viewModel.uiState.value.isIncomeList)
        assertEquals("Expected searchResults to match", incomeProducts, viewModel.uiState.value.searchResults)
    }
}
