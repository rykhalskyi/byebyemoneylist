package com.otakeeesen.byebyemoneylist.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.data.DashboardWidget
import com.otakeeesen.byebyemoneylist.data.DashboardWidgetConfig
import com.otakeeesen.byebyemoneylist.data.DashboardWidgetType
import com.otakeeesen.byebyemoneylist.data.WidgetData
import com.otakeeesen.byebyemoneylist.data.createDashboardWidget
import com.otakeeesen.byebyemoneylist.data.local.DashboardRepository
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.PriceRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ProductRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import com.otakeeesen.byebyemoneylist.ui.components.scanner.ScannedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

data class DashboardUiState(
    val widgets: List<DashboardWidget> = emptyList(),
    val widgetDataMap: Map<String, WidgetData> = emptyMap(),
    val categories: List<CategoryEntity> = emptyList(),
    val isLoading: Boolean = false,
    val widgetToRemove: DashboardWidgetConfig? = null
)

class DashboardViewModel(
    private val dashboardRepository: DashboardRepository,
    private val categoryRepository: CategoryRepository,
    private val shoppingListRepository: ShoppingListRepository,
    private val productRepository: ProductRepository,
    private val priceRepository: PriceRepository,
    private val preferencesManager: PreferencesManager,
) : ViewModel() {

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras,
            ): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as ByeByeMoneyApplication
                return DashboardViewModel(
                    dashboardRepository = application.dashboardRepository,
                    categoryRepository = application.categoryRepository,
                    shoppingListRepository = application.shoppingListRepository,
                    productRepository = application.productRepository,
                    priceRepository = application.priceRepository,
                    preferencesManager = application.preferencesManager
                ) as T
            }
        }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
        loadWidgets()
        observeDatabaseChanges()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            categoryRepository.allCategories.collect { categories ->
                _uiState.update { it.copy(categories = categories) }
            }
        }
    }

    private fun observeDatabaseChanges() {
        viewModelScope.launch {
            shoppingListRepository.allShoppingLists.collect {
                refreshWidgetData()
            }
        }
    }

    fun loadWidgets() {
        viewModelScope.launch {
            val savedJson = preferencesManager.loadDashboardWidgets()
            val configs: List<DashboardWidgetConfig> = if (!savedJson.isNullOrBlank()) {
                try {
                    json.decodeFromString(savedJson)
                } catch (e: Exception) {
                    getDefaultWidgetConfigs()
                }
            } else {
                val defaults = getDefaultWidgetConfigs()
                saveWidgetConfigs(defaults)
                defaults
            }

            val widgets = configs.sortedBy { it.order }.map { createDashboardWidget(it) }
            _uiState.update { it.copy(widgets = widgets) }
            refreshWidgetData()
        }
    }

    private fun getDefaultWidgetConfigs(): List<DashboardWidgetConfig> {
        return listOf(
            DashboardWidgetConfig(
                id = UUID.randomUUID().toString(),
                type = DashboardWidgetType.SPENT_TODAY,
                order = 0
            ),
            DashboardWidgetConfig(
                id = UUID.randomUUID().toString(),
                type = DashboardWidgetType.THIS_MONTH,
                order = 1
            )
        )
    }

    private fun saveWidgetConfigs(configs: List<DashboardWidgetConfig>) {
        val jsonString = json.encodeToString(configs)
        preferencesManager.saveDashboardWidgets(jsonString)
    }

    fun addWidget(type: DashboardWidgetType, categoryId: Long? = null) {
        viewModelScope.launch {
            val currentConfigs = _uiState.value.widgets.map { it.config }
            val newConfig = DashboardWidgetConfig(
                id = UUID.randomUUID().toString(),
                type = type,
                order = currentConfigs.size,
                categoryId = categoryId
            )
            val updatedConfigs = currentConfigs + newConfig
            saveWidgetConfigs(updatedConfigs)
            val widgets = updatedConfigs.sortedBy { it.order }.map { createDashboardWidget(it) }
            _uiState.update { it.copy(widgets = widgets) }
            refreshWidgetData()
        }
    }

    fun requestRemoveWidget(config: DashboardWidgetConfig) {
        _uiState.update { it.copy(widgetToRemove = config) }
    }

    fun cancelRemoveWidget() {
        _uiState.update { it.copy(widgetToRemove = null) }
    }

    fun confirmRemoveWidget() {
        val configToRemove = _uiState.value.widgetToRemove ?: return
        viewModelScope.launch {
            val currentConfigs = _uiState.value.widgets.map { it.config }
            val updatedConfigs = currentConfigs
                .filter { it.id != configToRemove.id }
                .mapIndexed { index, config -> config.copy(order = index) }
            saveWidgetConfigs(updatedConfigs)
            val widgets = updatedConfigs.sortedBy { it.order }.map { createDashboardWidget(it) }
            _uiState.update { it.copy(widgets = widgets, widgetToRemove = null) }
            refreshWidgetData()
        }
    }

    fun refreshWidgetData() {
        viewModelScope.launch {
            val widgets = _uiState.value.widgets
            if (widgets.isEmpty()) return@launch

            val dataMap = mutableMapOf<String, WidgetData>()

            widgets.forEach { widget ->
                val data: WidgetData = when (widget.config.type) {
                    DashboardWidgetType.CATEGORY_SPENDING -> {
                        val catId = widget.config.categoryId
                        if (catId != null) {
                            val currentMonth = YearMonth.now()
                            val monthStart = currentMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            val monthEnd = currentMonth.atEndOfMonth().atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                            val catData = dashboardRepository.getCategorySpending(catId, monthStart, monthEnd)
                            WidgetData.CategorySpending(
                                monthTotal = catData.monthTotal,
                                overallTotal = catData.overallTotal,
                                categoryName = catData.categoryName,
                                categoryColor = catData.categoryColor
                            )
                        } else {
                            WidgetData.CategorySpending(0.0, 0.0, "Unknown", 0xFFFF6B6BL)
                        }
                    }
                    DashboardWidgetType.SPENT_TODAY -> {
                        val todaySpent = dashboardRepository.getSpentToday()
                        WidgetData.SpentToday(total = todaySpent)
                    }
                    DashboardWidgetType.QUICK_PURCHASE -> {
                        WidgetData.QuickPurchase
                    }
                    DashboardWidgetType.THIS_MONTH -> {
                        val thisMonthSpent = dashboardRepository.getThisMonthSpending()
                        val lastMonthSpent = dashboardRepository.getLastMonthSpending()
                        val trendPercent = if (lastMonthSpent > 0.0) {
                            (((thisMonthSpent - lastMonthSpent) / lastMonthSpent) * 100.0).toFloat()
                        } else {
                            0f
                        }
                        WidgetData.ThisMonth(
                            total = thisMonthSpent,
                            lastMonthTotal = lastMonthSpent,
                            trendPercent = trendPercent
                        )
                    }
                }
                dataMap[widget.config.id] = data
            }

            _uiState.update { it.copy(widgetDataMap = dataMap) }
        }
    }

    fun processPurchase(
        listId: Long?,
        listName: String?,
        storeName: String,
        price: Double,
        items: List<ScannedItem> = emptyList(),
        storeAddress: String? = null
    ) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                shoppingListRepository.processPurchase(
                    listId = listId,
                    listName = listName,
                    storeName = storeName,
                    price = price,
                    items = items,
                    productRepository = productRepository,
                    priceRepository = priceRepository,
                    categoryRepository = categoryRepository,
                    isChecked = true,
                    storeAddress = storeAddress
                )
            }
            refreshWidgetData()
        }
    }
}
