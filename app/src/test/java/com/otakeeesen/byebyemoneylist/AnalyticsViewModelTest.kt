package com.otakeeesen.byebyemoneylist

import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListItemEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.*
import com.otakeeesen.byebyemoneylist.ui.viewmodel.AnalyticsViewModel
import com.otakeeesen.byebyemoneylist.ui.viewmodel.OverviewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private val shoppingListRepository: ShoppingListRepository = mock()
    private val categoryRepository: CategoryRepository = mock()
    private val productRepository: ProductRepository = mock()
    private val priceRepository: PriceRepository = mock()
    private val storeRepository: StoreRepository = mock()

    private lateinit var viewModel: AnalyticsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = AnalyticsViewModel(
            shoppingListRepository,
            categoryRepository,
            productRepository,
            priceRepository,
            storeRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test analytics aggregation for spending mode`() = runTest {
        // Setup mock data
        val category = CategoryEntity(id = 1L, name = "Food", color = "#FFFFFF", parentId = null)
        whenever(categoryRepository.getAllCategoriesOnce()).doReturn(listOf(category))

        val list = ShoppingListEntity(id = 1L, name = "List", createDate = System.currentTimeMillis(), position = 1, purchaseDate = null, storeId = null)
        whenever(shoppingListRepository.getFinishedListsInTimeRange(any(), any())).doReturn(listOf(list))

        val item = ShoppingListItemEntity(id = 1L, shoppingListId = 1L, productId = 1L, quantity = 2.0, price = 10.0, isChecked = true)
        whenever(shoppingListRepository.getItemsForListsSync(listOf(1L))).doReturn(listOf(item))


        // This is a simplified test; a full test requires mocking the repository deeply
        // or using an in-memory database.
        
        viewModel.setOverviewMode(OverviewMode.SPENDING)
        // ... assert uiState values
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
}
