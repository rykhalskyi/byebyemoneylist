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
    fun `uiState correctly filters by isNormal`() = runTest(testDispatcher) {
        productRepository = mock()
        shoppingListRepository = mock()
        categoryRepository = mock { on { allCategories } doReturn flowOf(emptyList()) }
        priceRepository = mock()
        storeRepository = mock { on { allStores } doReturn flowOf(emptyList()) }

        val shoppingList = com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity(
            id = listId,
            name = "Normal List",
            createDate = 0L,
            purchaseDate = null,
            storeId = null,
            isIncome = false,
            isSubscription = false
        )
        whenever(shoppingListRepository.getShoppingListById(listId)).thenReturn(shoppingList)

        val normalProducts = listOf(ProductEntity(id = 1, name = "Normal Product", barcode = "", picturePath = null, isIncome = false, isSubscription = false))
        whenever(productRepository.getProducts(isSubscription = null, isIncome = null, isNormal = true)).thenReturn(flowOf(normalProducts))

        viewModel = AddProductViewModel(
            listId, productRepository, shoppingListRepository,
            categoryRepository, priceRepository, storeRepository,
            ioDispatcher = testDispatcher
        )

        backgroundScope.launch { viewModel.uiState.collect { } }

        advanceUntilIdle()
        advanceTimeBy(400)
        advanceUntilIdle()

        assertEquals("Expected isNormal to be detected", false, viewModel.uiState.value.isIncomeList)
        assertEquals("Expected isNormal to be detected", false, viewModel.uiState.value.isSubscriptionList)
        assertEquals("Expected searchResults to match", normalProducts, viewModel.uiState.value.searchResults)
    }

}
