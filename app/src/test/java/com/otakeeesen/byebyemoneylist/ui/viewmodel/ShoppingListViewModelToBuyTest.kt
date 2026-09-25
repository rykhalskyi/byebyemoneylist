package com.otakeeesen.byebyemoneylist.ui.viewmodel

import com.otakeeesen.byebyemoneylist.data.PurchaseItem
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListItemEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.PriceRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ProductRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import com.otakeeesen.byebyemoneylist.data.sync.SyncFolderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingListViewModelToBuyTest {

    private val listId = 1L
    private val testScheduler = TestCoroutineScheduler()
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var repository: ShoppingListRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var priceRepository: PriceRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var syncFolderRepository: SyncFolderRepository

    @Before
    fun setup() {
        testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        repository = mock()
        categoryRepository = mock()
        priceRepository = mock()
        productRepository = mock()
        preferencesManager = mock()
        syncFolderRepository = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun stubFlows() {
        whenever(repository.allShoppingLists).thenReturn(flowOf(emptyList()))
        whenever(repository.allStores).thenReturn(flowOf(emptyList()))
        whenever(repository.getAllItemsWithProduct()).thenReturn(flowOf(emptyList()))
        whenever(repository.getAllShoppingListCategoryCrossRefs()).thenReturn(flowOf(emptyList()))
        whenever(repository.getAllStoresOnce()).thenReturn(emptyList())
        whenever(repository.checkAndForwardRecurringLists()).thenReturn(Unit)
        whenever(categoryRepository.allCategories).thenReturn(flowOf(emptyList()))
        whenever(categoryRepository.getAllCategoriesOnce()).thenReturn(emptyList())
        whenever(productRepository.getProducts()).thenReturn(flowOf(emptyList()))
        whenever(productRepository.getAllAliases()).thenReturn(flowOf(emptyList()))
    }

    private fun createViewModel() = ShoppingListViewModel(
        repository,
        categoryRepository,
        priceRepository,
        productRepository,
        preferencesManager,
        syncFolderRepository,
        null,
        ioDispatcher = testDispatcher,
    )

    private fun toBuyItem() = PurchaseItem(
        id = 5L,
        productId = 0L,
        name = "Milk",
        price = null,
        quantity = 1.0,
        imageUrl = "",
        checked = false,
        customName = "Milk",
    )

    @Test
    fun `createToBuyList forwards the new list id`() = runTest(testDispatcher) {
        stubFlows()
        whenever(repository.createToBuyList()).thenReturn(77L)
        val viewModel = createViewModel()

        var received: Long? = null
        viewModel.createToBuyList { received = it }
        advanceUntilIdle()

        assertEquals(77L, received)
    }

    @Test
    fun `to buy item editor state toggles`() = runTest(testDispatcher) {
        stubFlows()
        val viewModel = createViewModel()
        val item = toBuyItem()

        viewModel.startEditingToBuyItem(item)
        assertNotNull(viewModel.uiState.value.editingToBuyItem)

        viewModel.stopEditingToBuyItem()
        assertNull(viewModel.uiState.value.editingToBuyItem)
    }

    @Test
    fun `updateToBuyItemName trims and persists plain text`() = runTest(testDispatcher) {
        stubFlows()
        whenever(repository.getShoppingListItemById(5L)).thenReturn(
            ShoppingListItemEntity(
                id = 5L,
                shoppingListId = listId,
                productId = 0L,
                quantity = 1.0,
                isChecked = false,
                customName = "Milk",
            )
        )
        val viewModel = createViewModel()
        val item = toBuyItem()
        viewModel.startEditingToBuyItem(item)

        viewModel.updateToBuyItemName(item, "  Oat milk  ")
        advanceUntilIdle()

        val captor = argumentCaptor<ShoppingListItemEntity>()
        verify(repository).updateShoppingListItem(captor.capture())
        assertEquals("Oat milk", captor.firstValue.customName)
        assertNull(viewModel.uiState.value.editingToBuyItem)
    }

    @Test
    fun `status filter toggles back to all`() = runTest(testDispatcher) {
        stubFlows()
        val viewModel = createViewModel()
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        assertEquals(ShoppingListViewModel.ListStatusFilter.ALL, viewModel.uiState.value.filterStatus)

        viewModel.updateStatusFilter(ShoppingListViewModel.ListStatusFilter.TO_BUY)
        advanceUntilIdle()
        assertEquals(ShoppingListViewModel.ListStatusFilter.TO_BUY, viewModel.uiState.value.filterStatus)

        viewModel.updateStatusFilter(ShoppingListViewModel.ListStatusFilter.TO_BUY)
        advanceUntilIdle()
        assertEquals(ShoppingListViewModel.ListStatusFilter.ALL, viewModel.uiState.value.filterStatus)
    }
}
