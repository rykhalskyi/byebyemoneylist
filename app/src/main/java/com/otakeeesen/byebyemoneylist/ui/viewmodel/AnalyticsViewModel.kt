package com.otakeeesen.byebyemoneylist.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.PriceRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ProductRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.StoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.time.ZoneId

data class ProductStat(
    val productId: Long,
    val name: String,
    val quantity: Double,
    val totalSpent: Double,
    val categoryId: Long?
)

enum class OverviewMode {
    SPENDING, QUANTITY
}

data class AnalyticsUiState(
    val selectedMonth: YearMonth = YearMonth.now(),
    val currentRootCategoryId: Long? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val overviewMode: OverviewMode = OverviewMode.SPENDING,
    val rootCategorySpending: Map<Long, Double> = emptyMap(),
    val rootCategoryIncome: Map<Long, Double> = emptyMap(),
    val rootCategoryQuantity: Map<Long, Double> = emptyMap(),
    val subCategorySpending: Map<Long, Double> = emptyMap(),
    val subCategoryIncome: Map<Long, Double> = emptyMap(),
    val subCategoryQuantity: Map<Long, Double> = emptyMap(),
    val storeSpending: Map<Long, Double> = emptyMap(),
    val storeQuantity: Map<Long, Double> = emptyMap(),
    val listSpending: Map<Long, Double> = emptyMap(),
    val listQuantity: Map<Long, Double> = emptyMap(),
    val productStats: List<ProductStat> = emptyList(),
    val categoryNames: Map<Long, String> = emptyMap(),
    val storeNames: Map<Long, String> = emptyMap(),
    val listNames: Map<Long, String> = emptyMap(),
    val previousMonthTotal: Double = 0.0,
    val previousMonthIncome: Double = 0.0,
    val totalSpent: Double = 0.0,
    val totalIncome: Double = 0.0,
    val productSearchQuery: String = "",
    val statsSelectedCategoryId: Long? = null,
    val showStatsFilterPanel: Boolean = false,
    val showSearchPanel: Boolean = false,
    val allCategories: List<CategoryEntity> = emptyList()
)

class AnalyticsViewModel(
    private val shoppingListRepository: ShoppingListRepository,
    private val categoryRepository: CategoryRepository,
    private val productRepository: ProductRepository,
    private val priceRepository: PriceRepository,
    private val storeRepository: StoreRepository,
) : ViewModel() {

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras,
            ): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as ByeByeMoneyApplication
                return AnalyticsViewModel(
                    application.shoppingListRepository,
                    application.categoryRepository,
                    application.productRepository,
                    application.priceRepository,
                    application.storeRepository,
                ) as T
            }
        }
    }

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadAnalyticsData()
    }

    fun nextMonth() {
        _uiState.update { it.copy(selectedMonth = it.selectedMonth.plusMonths(1)) }
        loadAnalyticsData()
    }

    fun previousMonth() {
        _uiState.update { it.copy(selectedMonth = it.selectedMonth.minusMonths(1)) }
        loadAnalyticsData()
    }

    fun setRootCategory(categoryId: Long?) {
        _uiState.update { it.copy(currentRootCategoryId = categoryId) }
        loadAnalyticsData()
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(productSearchQuery = query) }
    }

    fun setStatsCategory(categoryId: Long?) {
        _uiState.update {
            val newCategory = if (it.statsSelectedCategoryId == categoryId) null else categoryId
            it.copy(statsSelectedCategoryId = newCategory)
        }
    }

    fun toggleSearchPanel() {
        _uiState.update { it.copy(showSearchPanel = !it.showSearchPanel, showStatsFilterPanel = if (!it.showSearchPanel) false else it.showStatsFilterPanel) }
    }

    fun toggleStatsFilterPanel() {
        _uiState.update { it.copy(showStatsFilterPanel = !it.showStatsFilterPanel, showSearchPanel = if (!it.showStatsFilterPanel) false else it.showSearchPanel) }
    }

    fun setOverviewMode(mode: OverviewMode) {
        _uiState.update { it.copy(overviewMode = mode) }
    }

    fun refresh() {
        loadAnalyticsData()
    }

    fun getPriceHistory(productId: Long): kotlinx.coroutines.flow.Flow<List<com.otakeeesen.byebyemoneylist.data.local.entity.PriceEntity>> {
        return priceRepository.getPricesForProduct(productId)
    }

    private fun loadAnalyticsData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val currentState = _uiState.value
                val data = withContext(Dispatchers.IO) {
                    val startOfMonth = currentState.selectedMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val endOfMonth = currentState.selectedMonth.atEndOfMonth().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                    val prevMonth = currentState.selectedMonth.minusMonths(1)
                    val startOfPrevMonth = prevMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val endOfPrevMonth = prevMonth.atEndOfMonth().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                    val lists = shoppingListRepository.getFinishedListsInTimeRange(startOfMonth, endOfMonth)
                    val prevLists = shoppingListRepository.getFinishedListsInTimeRange(startOfPrevMonth, endOfPrevMonth)

                    val prevTotal = prevLists.filter { !it.isIncome }.sumOf { it.finalTotal ?: 0.0 }
                    val prevIncome = prevLists.filter { it.isIncome }.sumOf { it.finalTotal ?: 0.0 }

                    val allCategories = categoryRepository.getAllCategoriesOnce()
                    val categoryNameMap = allCategories.associate { it.id to it.name }.toMutableMap().apply {
                        put(-1L, "Uncategorized")
                    }
                    val categoryIdMap = allCategories.associateBy { it.id }

                    val allStores = storeRepository.getAllStoresOnce()
                    val storeNameMap = allStores.associate { it.id to it.name }

                    val listNameMap = lists.associate { it.id to it.name }

                    val rootSpending = mutableMapOf<Long, Double>()
                    val rootIncome = mutableMapOf<Long, Double>()
                    val rootQuantity = mutableMapOf<Long, Double>()
                    val subSpending = mutableMapOf<Long, Double>()
                    val subIncome = mutableMapOf<Long, Double>()
                    val subQuantity = mutableMapOf<Long, Double>()
                    val storeSpendingMap = mutableMapOf<Long, Double>()
                    val storeQuantityMap = mutableMapOf<Long, Double>()
                    val listSpendingMap = mutableMapOf<Long, Double>()
                    val listQuantityMap = mutableMapOf<Long, Double>()
                    val productStatMap = mutableMapOf<Long, ProductStat>()
                    var currentTotal = 0.0
                    var currentIncome = 0.0

                    val listIds = lists.map { it.id }
                    val allItems = shoppingListRepository.getItemsWithProductForListsSync(listIds)
                    val products = allItems.associateBy { it.productId }

                    val directChildrenIds = if (currentState.currentRootCategoryId != null) {
                        allCategories.filter { it.parentId == currentState.currentRootCategoryId }.map { it.id }.toSet()
                    } else emptySet()

                    lists.forEach { list ->
                        val items = allItems.filter { it.shoppingListId == list.id }
                        val listPriceTotal = list.finalTotal ?: items.sumOf { (it.itemPrice ?: it.price) * it.quantity }
                        val listCountTotal = items.sumOf { it.quantity }

                        listSpendingMap[list.id] = listPriceTotal
                        listQuantityMap[list.id] = listCountTotal

                        if (list.isIncome) {
                            currentIncome += listPriceTotal
                        } else {
                            currentTotal += listPriceTotal
                            list.storeId?.let { sid ->
                                storeSpendingMap[sid] = (storeSpendingMap[sid] ?: 0.0) + listPriceTotal
                                storeQuantityMap[sid] = (storeQuantityMap[sid] ?: 0.0) + listCountTotal
                            }
                        }

                        items.forEach { item ->
                            val itemTotal = (item.itemPrice ?: item.price) * item.quantity

                            val cat = item.productCategoryId?.let { categoryIdMap[it] }
                            
                            // Get Root Category ID or use a special ID for uncategorized (-1L)
                            val rootId = if (cat != null) {
                                var root: CategoryEntity = cat
                                while (root.parentId != null) {
                                    val parent = categoryIdMap[root.parentId] ?: break
                                    root = parent
                                }
                                root.id
                            } else -1L

                            if (list.isIncome) {
                                rootIncome[rootId] = (rootIncome[rootId] ?: 0.0) + itemTotal
                            } else {
                                rootSpending[rootId] = (rootSpending[rootId] ?: 0.0) + itemTotal
                            }
                            rootQuantity[rootId] = (rootQuantity[rootId] ?: 0.0) + item.quantity

                            // Sub Category Logic (for split view)
                            if (currentState.currentRootCategoryId != null) {
                                val subId = if (cat != null) {
                                    if (directChildrenIds.contains(cat.id)) {
                                        cat.id
                                    } else {
                                        var p: CategoryEntity? = cat
                                        while (p?.parentId != null && p.parentId != currentState.currentRootCategoryId) {
                                            p = categoryIdMap[p.parentId]
                                        }
                                        if (p?.parentId == currentState.currentRootCategoryId) p.id else -1L
                                    }
                                } else if (currentState.currentRootCategoryId == -1L) -1L else null

                                if (subId != null) {
                                    if (list.isIncome) {
                                        subIncome[subId] = (subIncome[subId] ?: 0.0) + itemTotal
                                    } else {
                                        subSpending[subId] = (subSpending[subId] ?: 0.0) + itemTotal
                                    }
                                    subQuantity[subId] = (subQuantity[subId] ?: 0.0) + item.quantity
                                }
                            }

                            val existing = productStatMap[item.productId]
                            if (existing == null) {
                                productStatMap[item.productId] = ProductStat(
                                    productId = item.productId,
                                    name = item.productName ?: "Unknown",
                                    quantity = item.quantity,
                                    totalSpent = if (list.isIncome) -itemTotal else itemTotal,
                                    categoryId = cat?.id
                                )
                            } else {
                                productStatMap[item.productId] = existing.copy(
                                    quantity = existing.quantity + item.quantity,
                                    totalSpent = existing.totalSpent + (if (list.isIncome) -itemTotal else itemTotal)
                                )
                            }
                        }
                    }

                    DataResult(
                        rootSpending = rootSpending,
                        rootIncome = rootIncome,
                        rootQuantity = rootQuantity,
                        subSpending = subSpending,
                        subIncome = subIncome,
                        subQuantity = subQuantity,
                        storeSpending = storeSpendingMap,
                        storeQuantity = storeQuantityMap,
                        listSpending = listSpendingMap,
                        listQuantity = listQuantityMap,
                        productStats = productStatMap.values.toList(),
                        categoryNames = categoryNameMap,
                        storeNames = storeNameMap,
                        listNames = listNameMap,
                        prevTotal = prevTotal,
                        prevIncome = prevIncome,
                        currentTotal = currentTotal,
                        currentIncome = currentIncome,
                        allCategories = allCategories
                    )
                }

                _uiState.update { it.copy(
                    isLoading = false,
                    rootCategorySpending = data.rootSpending,
                    rootCategoryIncome = data.rootIncome,
                    rootCategoryQuantity = data.rootQuantity,
                    subCategorySpending = data.subSpending,
                    subCategoryIncome = data.subIncome,
                    subCategoryQuantity = data.subQuantity,
                    storeSpending = data.storeSpending,
                    storeQuantity = data.storeQuantity,
                    listSpending = data.listSpending,
                    listQuantity = data.listQuantity,
                    productStats = data.productStats,
                    categoryNames = data.categoryNames,
                    storeNames = data.storeNames,
                    listNames = data.listNames,
                    previousMonthTotal = data.prevTotal,
                    previousMonthIncome = data.prevIncome,
                    totalSpent = data.currentTotal,
                    totalIncome = data.currentIncome,
                    allCategories = data.allCategories
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private data class DataResult(
        val rootSpending: Map<Long, Double>,
        val rootIncome: Map<Long, Double>,
        val rootQuantity: Map<Long, Double>,
        val subSpending: Map<Long, Double>,
        val subIncome: Map<Long, Double>,
        val subQuantity: Map<Long, Double>,
        val storeSpending: Map<Long, Double>,
        val storeQuantity: Map<Long, Double>,
        val listSpending: Map<Long, Double>,
        val listQuantity: Map<Long, Double>,
        val productStats: List<ProductStat>,
        val categoryNames: Map<Long, String>,
        val storeNames: Map<Long, String>,
        val listNames: Map<Long, String>,
        val prevTotal: Double,
        val prevIncome: Double,
        val currentTotal: Double,
        val currentIncome: Double,
        val allCategories: List<CategoryEntity>
    )
}
