package com.otakeeesen.byebyemoneylist.ui.viewmodel

import com.otakeeesen.byebyemoneylist.data.ListKind
import com.otakeeesen.byebyemoneylist.data.ShoppingList
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
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

    private suspend fun setupRepos(needToBuy: Boolean) {
        productRepository = mock()
        shoppingListRepository = mock()
        categoryRepository = mock { on { allCategories } doReturn flowOf(emptyList()) }
        priceRepository = mock()
        storeRepository = mock { on { allStores } doReturn flowOf(emptyList()) }
        whenever(productRepository.getProducts(isSubscription = null, isIncome = null, isNormal = true))
            .thenReturn(flowOf(emptyList()))
        val list = ShoppingListEntity(
            id = listId,
            name = if (needToBuy) "To Buy" else "Normal List",
            createDate = 0L,
            purchaseDate = null,
            storeId = null,
            isFinished = !needToBuy,
            kind = if (needToBuy) ListKind.NEED_TO_BUY.name else ListKind.PURCHASE.name,
        )
        whenever(shoppingListRepository.getShoppingListById(listId)).thenReturn(list)
    }

    private fun createViewModel() = AddProductViewModel(
        listId, productRepository, shoppingListRepository,
        categoryRepository, priceRepository, storeRepository,
        ioDispatcher = testDispatcher
    )

    @Test
    fun `uiState flags To Buy list`() = runTest(testDispatcher) {
        setupRepos(needToBuy = true)
        viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect { } }

        advanceUntilIdle()
        advanceTimeBy(400)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isNeedToBuyList)
    }

    @Test
    fun `addToBuyItem stores plain text only`() = runTest(testDispatcher) {
        setupRepos(needToBuy = true)
        viewModel = createViewModel()
        advanceUntilIdle()

        var completed = false
        viewModel.addToBuyItem("Milk") { completed = true }
        advanceUntilIdle()

        verify(shoppingListRepository).addToBuyItem(listId, "Milk")
        verify(shoppingListRepository, never()).insertShoppingListItem(any())
        assertTrue(completed)
    }

    @Test
    fun `barcode scan on To Buy list stores product name as plain text`() = runTest(testDispatcher) {
        setupRepos(needToBuy = true)
        whenever(productRepository.getProductByBarcode("123"))
            .thenReturn(ProductEntity(id = 5, name = "Milk", barcode = "123", picturePath = null))
        viewModel = createViewModel()
        advanceUntilIdle()

        var completed = false
        viewModel.onBarcodeScanned("123") { completed = true }
        advanceUntilIdle()

        verify(shoppingListRepository).addToBuyItem(listId, "Milk")
        verify(shoppingListRepository, never()).insertShoppingListItem(any())
        assertTrue(completed)
    }

    @Test
    fun `barcode scan on normal list links catalog product`() = runTest(testDispatcher) {
        setupRepos(needToBuy = false)
        whenever(productRepository.getProductByBarcode("123"))
            .thenReturn(ProductEntity(id = 5, name = "Milk", barcode = "123", picturePath = null))
        whenever(shoppingListRepository.getMaxPositionForList(listId)).thenReturn(-1)
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onBarcodeScanned("123") {}
        advanceUntilIdle()

        verify(shoppingListRepository).insertShoppingListItem(any())
        verify(shoppingListRepository, never()).addToBuyItem(any(), any())
    }
}
