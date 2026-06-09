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

data class AnalyticsUiState(
    val selectedMonth: YearMonth = YearMonth.now(),
    val currentRootCategoryId: Long? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val rootCategorySpending: Map<Long, Double> = emptyMap(),
    val subCategorySpending: Map<Long, Double> = emptyMap(),
    val storeSpending: Map<Long, Double> = emptyMap(),
    val productStats: List<ProductStat> = emptyList(),
    val categoryNames: Map<Long, String> = emptyMap(),
    val storeNames: Map<Long, String> = emptyMap(),
    val previousMonthTotal: Double = 0.0,
    val totalSpent: Double = 0.0,
    val productSearchQuery: String = "",
    val statsSelectedCategoryId: Long? = null,
    val showStatsFilterPanel: Boolean = false,
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

    fun toggleStatsFilterPanel() {
        _uiState.update { it.copy(showStatsFilterPanel = !it.showStatsFilterPanel) }
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

                    val prevTotal = prevLists.sumOf { it.finalTotal ?: 0.0 }

                    val allCategories = categoryRepository.getAllCategoriesOnce()
                    val categoryNameMap = allCategories.associate { it.id to it.name }
                    val categoryIdMap = allCategories.associateBy { it.id }

                    val allStores = storeRepository.getAllStoresOnce()
                    val storeNameMap = allStores.associate { it.id to it.name }

                    val rootSpending = mutableMapOf<Long, Double>()
                    val subSpending = mutableMapOf<Long, Double>()
                    val storeSpendingMap = mutableMapOf<Long, Double>()
                    val productStatMap = mutableMapOf<Long, ProductStat>()
                    var currentTotal = 0.0

                    // BULK FETCH: Get all items for all lists in one go
                    val listIds = lists.map { it.id }
                    val allItems = shoppingListRepository.getItemsForListsSync(listIds)

                    // BULK FETCH: Get all unique products for these items
                    val productIds = allItems.map { it.productId }.distinct()
                    val products = productRepository.getProductsByIds(productIds).associateBy { it.id }

                    lists.forEach { list ->
                        val items = allItems.filter { it.shoppingListId == list.id }
                        list.storeId?.let { sid ->
                            val listTotal = list.finalTotal ?: items.sumOf { (it.price ?: 0.0) * it.quantity }
                            storeSpendingMap[sid] = (storeSpendingMap[sid] ?: 0.0) + listTotal
                        }

                        items.forEach { item ->
                            val product = products[item.productId]
                            val itemTotal = (item.price ?: 0.0) * item.quantity
                            currentTotal += itemTotal

                            if (product != null) {
                                val cat = product.categoryId?.let { categoryIdMap[it] }
                                if (cat != null) {
                                    var currentRoot: CategoryEntity = cat
                                    while (currentRoot.parentId != null) {
                                        val parent = categoryIdMap[currentRoot.parentId] ?: break
                                        currentRoot = parent
                                    }
                                    rootSpending[currentRoot.id] = (rootSpending[currentRoot.id] ?: 0.0) + itemTotal
                                }

                                val existing = productStatMap[product.id]
                                if (existing == null) {
                                    productStatMap[product.id] = ProductStat(
                                        productId = product.id,
                                        name = product.name,
                                        quantity = item.quantity,
                                        totalSpent = itemTotal,
                                        categoryId = cat?.id
                                    )
                                } else {
                                    productStatMap[product.id] = existing.copy(
                                        quantity = existing.quantity + item.quantity,
                                        totalSpent = existing.totalSpent + itemTotal
                                    )
                                }
                            }
                        }
                    }

                    if (currentState.currentRootCategoryId != null) {
                        val directChildrenIds = allCategories.filter { it.parentId == currentState.currentRootCategoryId }.map { it.id }.toSet()

                        allItems.forEach { item ->
                            val product = products[item.productId]
                            val itemTotal = (item.price ?: 0.0) * item.quantity
                            if (product != null) {
                                val cat = product.categoryId?.let { categoryIdMap[it] }
                                if (cat != null) {
                                    if (directChildrenIds.contains(cat.id)) {
                                        subSpending[cat.id] = (subSpending[cat.id] ?: 0.0) + itemTotal
                                    } else {
                                        var p: CategoryEntity? = cat
                                        while (p?.parentId != null && p.parentId != currentState.currentRootCategoryId) {
                                            p = categoryIdMap[p.parentId]
                                        }
                                        if (p?.parentId == currentState.currentRootCategoryId) {
                                            subSpending[p.id] = (subSpending[p.id] ?: 0.0) + itemTotal
                                        }
                                    }
                                }
                            }
                        }
                    }
                    DataResult(
                        rootSpending = rootSpending,
                        subSpending = subSpending,
                        storeSpending = storeSpendingMap,
                        productStats = productStatMap.values.toList(),
                        categoryNames = categoryNameMap,
                        storeNames = storeNameMap,
                        prevTotal = prevTotal,
                        currentTotal = currentTotal,
                        allCategories = allCategories
                    )
                }

                _uiState.update { it.copy(
                    isLoading = false,
                    rootCategorySpending = data.rootSpending,
                    subCategorySpending = data.subSpending,
                    storeSpending = data.storeSpending,
                    productStats = data.productStats,
                    categoryNames = data.categoryNames,
                    storeNames = data.storeNames,
                    previousMonthTotal = data.prevTotal,
                    totalSpent = data.currentTotal,
                    allCategories = data.allCategories
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private data class DataResult(
        val rootSpending: Map<Long, Double>,
        val subSpending: Map<Long, Double>,
        val storeSpending: Map<Long, Double>,
        val productStats: List<ProductStat>,
        val categoryNames: Map<Long, String>,
        val storeNames: Map<Long, String>,
        val prevTotal: Double,
        val currentTotal: Double,
        val allCategories: List<CategoryEntity>
    )
}
