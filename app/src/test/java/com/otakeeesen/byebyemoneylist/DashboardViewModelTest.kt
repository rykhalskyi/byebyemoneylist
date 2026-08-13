package com.otakeeesen.byebyemoneylist

import com.otakeeesen.byebyemoneylist.data.DashboardWidgetConfig
import com.otakeeesen.byebyemoneylist.data.DashboardWidgetType
import com.otakeeesen.byebyemoneylist.data.WidgetData
import com.otakeeesen.byebyemoneylist.data.local.CategorySpendingData
import com.otakeeesen.byebyemoneylist.data.local.DashboardRepository
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.PriceRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ProductRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import com.otakeeesen.byebyemoneylist.ui.viewmodel.DashboardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var dashboardRepository: DashboardRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var shoppingListRepository: ShoppingListRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var priceRepository: PriceRepository
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var viewModel: DashboardViewModel

    private val categoryFlow = MutableStateFlow<List<CategoryEntity>>(emptyList())
    private val shoppingListFlow = MutableStateFlow<List<Any>>(emptyList())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        dashboardRepository = mock()
        categoryRepository = mock()
        shoppingListRepository = mock()
        productRepository = mock()
        priceRepository = mock()
        preferencesManager = mock()

        whenever(categoryRepository.allCategories).doReturn(categoryFlow)
        whenever(shoppingListRepository.allShoppingLists).doReturn(emptyFlow())
        whenever(preferencesManager.loadDashboardWidgets()).doReturn(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(): DashboardViewModel {
        return DashboardViewModel(
            dashboardRepository = dashboardRepository,
            categoryRepository = categoryRepository,
            shoppingListRepository = shoppingListRepository,
            productRepository = productRepository,
            priceRepository = priceRepository,
            preferencesManager = preferencesManager,
        )
    }

    // ── Default widgets ────────────────────────────────────────────────────────

    @Test
    fun `when no saved widgets, defaults are SPENT_TODAY and THIS_MONTH`() = runTest {
        whenever(preferencesManager.loadDashboardWidgets()).doReturn(null)
        whenever(dashboardRepository.getSpentToday()).doReturn(0.0)
        whenever(dashboardRepository.getThisMonthSpending()).doReturn(0.0)
        whenever(dashboardRepository.getLastMonthSpending()).doReturn(0.0)

        viewModel = buildViewModel()
        advanceUntilIdle()

        val widgets = viewModel.uiState.value.widgets
        assertEquals(2, widgets.size)
        assertEquals(DashboardWidgetType.SPENT_TODAY, widgets[0].config.type)
        assertEquals(DashboardWidgetType.THIS_MONTH, widgets[1].config.type)
    }

    @Test
    fun `default widgets are persisted to PreferencesManager`() = runTest {
        whenever(preferencesManager.loadDashboardWidgets()).doReturn(null)
        whenever(dashboardRepository.getSpentToday()).doReturn(0.0)
        whenever(dashboardRepository.getThisMonthSpending()).doReturn(0.0)
        whenever(dashboardRepository.getLastMonthSpending()).doReturn(0.0)

        viewModel = buildViewModel()
        advanceUntilIdle()

        verify(preferencesManager).saveDashboardWidgets(any())
    }

    // ── Loading from preferences ────────────────────────────────────────────────

    @Test
    fun `loadWidgets restores saved widget configs from JSON`() = runTest {
        val configs = listOf(
            DashboardWidgetConfig(id = "w1", type = DashboardWidgetType.QUICK_PURCHASE, order = 0),
            DashboardWidgetConfig(id = "w2", type = DashboardWidgetType.SPENT_TODAY, order = 1),
        )
        val savedJson = json.encodeToString(configs)
        whenever(preferencesManager.loadDashboardWidgets()).doReturn(savedJson)
        whenever(dashboardRepository.getSpentToday()).doReturn(0.0)

        viewModel = buildViewModel()
        advanceUntilIdle()

        val widgets = viewModel.uiState.value.widgets
        assertEquals(2, widgets.size)
        assertEquals(DashboardWidgetType.QUICK_PURCHASE, widgets[0].config.type)
        assertEquals(DashboardWidgetType.SPENT_TODAY, widgets[1].config.type)
    }

    @Test
    fun `loadWidgets falls back to defaults on malformed JSON`() = runTest {
        whenever(preferencesManager.loadDashboardWidgets()).doReturn("not-valid-json{{{")
        whenever(dashboardRepository.getSpentToday()).doReturn(0.0)
        whenever(dashboardRepository.getThisMonthSpending()).doReturn(0.0)
        whenever(dashboardRepository.getLastMonthSpending()).doReturn(0.0)

        viewModel = buildViewModel()
        advanceUntilIdle()

        val widgets = viewModel.uiState.value.widgets
        assertEquals(2, widgets.size)
        assertEquals(DashboardWidgetType.SPENT_TODAY, widgets[0].config.type)
        assertEquals(DashboardWidgetType.THIS_MONTH, widgets[1].config.type)
    }

    @Test
    fun `widgets are sorted by order when loaded`() = runTest {
        // Intentionally out of order in the JSON
        val configs = listOf(
            DashboardWidgetConfig(id = "last", type = DashboardWidgetType.THIS_MONTH, order = 2),
            DashboardWidgetConfig(id = "first", type = DashboardWidgetType.SPENT_TODAY, order = 0),
            DashboardWidgetConfig(id = "mid", type = DashboardWidgetType.QUICK_PURCHASE, order = 1),
        )
        val savedJson = json.encodeToString(configs)
        whenever(preferencesManager.loadDashboardWidgets()).doReturn(savedJson)
        whenever(dashboardRepository.getSpentToday()).doReturn(0.0)
        whenever(dashboardRepository.getThisMonthSpending()).doReturn(0.0)
        whenever(dashboardRepository.getLastMonthSpending()).doReturn(0.0)

        viewModel = buildViewModel()
        advanceUntilIdle()

        val widgets = viewModel.uiState.value.widgets
        assertEquals("first", widgets[0].config.id)
        assertEquals("mid", widgets[1].config.id)
        assertEquals("last", widgets[2].config.id)
    }

    // ── addWidget ──────────────────────────────────────────────────────────────

    @Test
    fun `addWidget adds a new widget to the list`() = runTest {
        whenever(preferencesManager.loadDashboardWidgets()).doReturn(null)
        whenever(dashboardRepository.getSpentToday()).doReturn(0.0)
        whenever(dashboardRepository.getThisMonthSpending()).doReturn(0.0)
        whenever(dashboardRepository.getLastMonthSpending()).doReturn(0.0)

        viewModel = buildViewModel()
        advanceUntilIdle()

        val sizeBefore = viewModel.uiState.value.widgets.size
        viewModel.addWidget(DashboardWidgetType.QUICK_PURCHASE)
        advanceUntilIdle()

        assertEquals(sizeBefore + 1, viewModel.uiState.value.widgets.size)
        assertTrue(viewModel.uiState.value.widgets.any { it.config.type == DashboardWidgetType.QUICK_PURCHASE })
    }

    @Test
    fun `addWidget with categoryId creates CATEGORY_SPENDING widget with correct categoryId`() = runTest {
        whenever(preferencesManager.loadDashboardWidgets()).doReturn(null)
        whenever(dashboardRepository.getSpentToday()).doReturn(0.0)
        whenever(dashboardRepository.getThisMonthSpending()).doReturn(0.0)
        whenever(dashboardRepository.getLastMonthSpending()).doReturn(0.0)
        whenever(dashboardRepository.getCategorySpending(any(), any(), any())).doReturn(
            CategorySpendingData(50.0, 200.0, "Food", 0xFFFF6B6BL, "🍎")
        )

        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.addWidget(DashboardWidgetType.CATEGORY_SPENDING, categoryId = 55L)
        advanceUntilIdle()

        val categoryWidget = viewModel.uiState.value.widgets
            .find { it.config.type == DashboardWidgetType.CATEGORY_SPENDING }
        assertNotNull(categoryWidget)
        assertEquals(55L, categoryWidget!!.config.categoryId)
    }

    @Test
    fun `addWidget persists updated configs to PreferencesManager`() = runTest {
        whenever(preferencesManager.loadDashboardWidgets()).doReturn(null)
        whenever(dashboardRepository.getSpentToday()).doReturn(0.0)
        whenever(dashboardRepository.getThisMonthSpending()).doReturn(0.0)
        whenever(dashboardRepository.getLastMonthSpending()).doReturn(0.0)

        viewModel = buildViewModel()
        advanceUntilIdle()

        viewModel.addWidget(DashboardWidgetType.QUICK_PURCHASE)
        advanceUntilIdle()

        // Called once for defaults, once after addWidget
        verify(preferencesManager, org.mockito.kotlin.atLeast(2)).saveDashboardWidgets(any())
    }

    // ── removeWidget ───────────────────────────────────────────────────────────

    @Test
    fun `reorderWidgets re-indexes order and persists new order`() = runTest {
        val configs = listOf(
            DashboardWidgetConfig(id = "w0", type = DashboardWidgetType.SPENT_TODAY, order = 0),
            DashboardWidgetConfig(id = "w1", type = DashboardWidgetType.QUICK_PURCHASE, order = 1),
            DashboardWidgetConfig(id = "w2", type = DashboardWidgetType.THIS_MONTH, order = 2),
        )
        whenever(preferencesManager.loadDashboardWidgets()).doReturn(json.encodeToString(configs))
        whenever(dashboardRepository.getSpentToday()).doReturn(0.0)
        whenever(dashboardRepository.getThisMonthSpending()).doReturn(0.0)
        whenever(dashboardRepository.getLastMonthSpending()).doReturn(0.0)

        viewModel = buildViewModel()
        advanceUntilIdle()

        val reversed = viewModel.uiState.value.widgets.reversed()
        viewModel.reorderWidgets(reversed)
        advanceUntilIdle()

        val captor = argumentCaptor<String>()
        verify(preferencesManager).saveDashboardWidgets(captor.capture())
        val savedConfigs = json.decodeFromString<List<DashboardWidgetConfig>>(captor.lastValue)
        assertEquals(listOf("w2", "w1", "w0"), savedConfigs.map { it.id })
        assertEquals(listOf(0, 1, 2), savedConfigs.map { it.order })

        assertEquals(
            listOf("w2", "w1", "w0"),
            viewModel.uiState.value.widgets.map { it.config.id }
        )
    }

    @Test
    fun `requestRemoveWidget sets widgetToRemove in state`() = runTest {
        whenever(preferencesManager.loadDashboardWidgets()).doReturn(null)
        whenever(dashboardRepository.getSpentToday()).doReturn(0.0)
        whenever(dashboardRepository.getThisMonthSpending()).doReturn(0.0)
        whenever(dashboardRepository.getLastMonthSpending()).doReturn(0.0)

        viewModel = buildViewModel()
        advanceUntilIdle()

        val targetConfig = viewModel.uiState.value.widgets.first().config
        viewModel.requestRemoveWidget(targetConfig)

        assertEquals(targetConfig, viewModel.uiState.value.widgetToRemove)
    }

    @Test
    fun `cancelRemoveWidget clears widgetToRemove`() = runTest {
        whenever(preferencesManager.loadDashboardWidgets()).doReturn(null)
        whenever(dashboardRepository.getSpentToday()).doReturn(0.0)
        whenever(dashboardRepository.getThisMonthSpending()).doReturn(0.0)
        whenever(dashboardRepository.getLastMonthSpending()).doReturn(0.0)

        viewModel = buildViewModel()
        advanceUntilIdle()

        val targetConfig = viewModel.uiState.value.widgets.first().config
        viewModel.requestRemoveWidget(targetConfig)
        viewModel.cancelRemoveWidget()

        assertNull(viewModel.uiState.value.widgetToRemove)
    }

    @Test
    fun `confirmRemoveWidget removes the widget from the list`() = runTest {
        whenever(preferencesManager.loadDashboardWidgets()).doReturn(null)
        whenever(dashboardRepository.getSpentToday()).doReturn(0.0)
        whenever(dashboardRepository.getThisMonthSpending()).doReturn(0.0)
        whenever(dashboardRepository.getLastMonthSpending()).doReturn(0.0)

        viewModel = buildViewModel()
        advanceUntilIdle()

        val initialCount = viewModel.uiState.value.widgets.size
        val targetConfig = viewModel.uiState.value.widgets.first().config

        viewModel.requestRemoveWidget(targetConfig)
        viewModel.confirmRemoveWidget()
        advanceUntilIdle()

        assertEquals(initialCount - 1, viewModel.uiState.value.widgets.size)
        assertTrue(viewModel.uiState.value.widgets.none { it.config.id == targetConfig.id })
    }

    @Test
    fun `confirmRemoveWidget re-indexes order of remaining widgets`() = runTest {
        val configs = listOf(
            DashboardWidgetConfig(id = "w0", type = DashboardWidgetType.SPENT_TODAY, order = 0),
            DashboardWidgetConfig(id = "w1", type = DashboardWidgetType.QUICK_PURCHASE, order = 1),
            DashboardWidgetConfig(id = "w2", type = DashboardWidgetType.THIS_MONTH, order = 2),
        )
        whenever(preferencesManager.loadDashboardWidgets()).doReturn(json.encodeToString(configs))
        whenever(dashboardRepository.getSpentToday()).doReturn(0.0)
        whenever(dashboardRepository.getThisMonthSpending()).doReturn(0.0)
        whenever(dashboardRepository.getLastMonthSpending()).doReturn(0.0)

        viewModel = buildViewModel()
        advanceUntilIdle()

        // Remove middle widget
        val toRemove = viewModel.uiState.value.widgets.find { it.config.id == "w1" }!!.config
        viewModel.requestRemoveWidget(toRemove)
        viewModel.confirmRemoveWidget()
        advanceUntilIdle()

        val remaining = viewModel.uiState.value.widgets
        assertEquals(2, remaining.size)
        assertEquals(0, remaining[0].config.order)
        assertEquals(1, remaining[1].config.order)
    }

    @Test
    fun `confirmRemoveWidget clears widgetToRemove in state`() = runTest {
        whenever(preferencesManager.loadDashboardWidgets()).doReturn(null)
        whenever(dashboardRepository.getSpentToday()).doReturn(0.0)
        whenever(dashboardRepository.getThisMonthSpending()).doReturn(0.0)
        whenever(dashboardRepository.getLastMonthSpending()).doReturn(0.0)

        viewModel = buildViewModel()
        advanceUntilIdle()

        val targetConfig = viewModel.uiState.value.widgets.first().config
        viewModel.requestRemoveWidget(targetConfig)
        viewModel.confirmRemoveWidget()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.widgetToRemove)
    }

    // ── refreshWidgetData ──────────────────────────────────────────────────────

    @Test
    fun `refreshWidgetData populates SpentToday widget data`() = runTest {
        val configs = listOf(
            DashboardWidgetConfig(id = "today", type = DashboardWidgetType.SPENT_TODAY, order = 0)
        )
        whenever(preferencesManager.loadDashboardWidgets()).doReturn(json.encodeToString(configs))
        whenever(dashboardRepository.getSpentToday()).doReturn(75.5)

        viewModel = buildViewModel()
        advanceUntilIdle()

        val data = viewModel.uiState.value.widgetDataMap["today"]
        assertNotNull(data)
        assertTrue(data is WidgetData.SpentToday)
        assertEquals(75.5, (data as WidgetData.SpentToday).total, 0.001)
    }

    @Test
    fun `refreshWidgetData populates ThisMonth widget data with trend`() = runTest {
        val configs = listOf(
            DashboardWidgetConfig(id = "month", type = DashboardWidgetType.THIS_MONTH, order = 0)
        )
        whenever(preferencesManager.loadDashboardWidgets()).doReturn(json.encodeToString(configs))
        whenever(dashboardRepository.getThisMonthSpending()).doReturn(120.0)
        whenever(dashboardRepository.getLastMonthSpending()).doReturn(100.0)

        viewModel = buildViewModel()
        advanceUntilIdle()

        val data = viewModel.uiState.value.widgetDataMap["month"]
        assertNotNull(data)
        assertTrue(data is WidgetData.ThisMonth)
        val thisMonth = data as WidgetData.ThisMonth
        assertEquals(120.0, thisMonth.total, 0.001)
        assertEquals(100.0, thisMonth.lastMonthTotal, 0.001)
        assertEquals(20f, thisMonth.trendPercent, 0.01f)
    }

    @Test
    fun `refreshWidgetData populates QuickPurchase widget data`() = runTest {
        val configs = listOf(
            DashboardWidgetConfig(id = "qp", type = DashboardWidgetType.QUICK_PURCHASE, order = 0)
        )
        whenever(preferencesManager.loadDashboardWidgets()).doReturn(json.encodeToString(configs))

        viewModel = buildViewModel()
        advanceUntilIdle()

        val data = viewModel.uiState.value.widgetDataMap["qp"]
        assertNotNull(data)
        assertTrue(data is WidgetData.QuickPurchase)
    }

    @Test
    fun `refreshWidgetData populates CategorySpending widget data`() = runTest {
        val configs = listOf(
            DashboardWidgetConfig(id = "cat", type = DashboardWidgetType.CATEGORY_SPENDING, order = 0, categoryId = 10L)
        )
        whenever(preferencesManager.loadDashboardWidgets()).doReturn(json.encodeToString(configs))
        whenever(dashboardRepository.getCategorySpending(any(), any(), any())).doReturn(
            CategorySpendingData(
                monthTotal = 200.0,
                overallTotal = 800.0,
                categoryName = "Groceries",
                categoryColor = 0xFF4CAF50L,
                categoryEmoji = "🛒"
            )
        )

        viewModel = buildViewModel()
        advanceUntilIdle()

        val data = viewModel.uiState.value.widgetDataMap["cat"]
        assertNotNull(data)
        assertTrue(data is WidgetData.CategorySpending)
        val catData = data as WidgetData.CategorySpending
        assertEquals(200.0, catData.monthTotal, 0.001)
        assertEquals(800.0, catData.overallTotal, 0.001)
        assertEquals("Groceries", catData.categoryName)
    }

    @Test
    fun `CategorySpending widget with null categoryId results in zero data`() = runTest {
        val configs = listOf(
            DashboardWidgetConfig(id = "cat-null", type = DashboardWidgetType.CATEGORY_SPENDING, order = 0, categoryId = null)
        )
        whenever(preferencesManager.loadDashboardWidgets()).doReturn(json.encodeToString(configs))

        viewModel = buildViewModel()
        advanceUntilIdle()

        val data = viewModel.uiState.value.widgetDataMap["cat-null"]
        assertNotNull(data)
        assertTrue(data is WidgetData.CategorySpending)
        val catData = data as WidgetData.CategorySpending
        assertEquals(0.0, catData.monthTotal, 0.001)
        assertEquals("Unknown", catData.categoryName)
    }

    // ── categories ─────────────────────────────────────────────────────────────

    @Test
    fun `categories are updated when categoryRepository emits new list`() = runTest {
        whenever(preferencesManager.loadDashboardWidgets()).doReturn(null)
        whenever(dashboardRepository.getSpentToday()).doReturn(0.0)
        whenever(dashboardRepository.getThisMonthSpending()).doReturn(0.0)
        whenever(dashboardRepository.getLastMonthSpending()).doReturn(0.0)

        viewModel = buildViewModel()
        advanceUntilIdle()

        val cats = listOf(
            CategoryEntity(id = 1L, name = "Food", color = "#FF6B6B"),
            CategoryEntity(id = 2L, name = "Transport", color = "#4CAF50"),
        )
        categoryFlow.emit(cats)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.categories.size)
        assertEquals("Food", viewModel.uiState.value.categories[0].name)
    }
}
