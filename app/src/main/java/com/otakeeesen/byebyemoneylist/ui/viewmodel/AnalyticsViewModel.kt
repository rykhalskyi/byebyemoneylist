package com.otakeeesen.byebyemoneylist.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.PriceRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ProductRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId

enum class TimeRange {
    MONTH, YEAR, ALL_TIME
}

data class AnalyticsUiState(
    val timeRange: TimeRange = TimeRange.MONTH,
    val currentRootCategoryId: Long? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val categorySpending: Map<Long, Double> = emptyMap(),
    val monthlyTrend: Map<String, Double> = emptyMap(),
    val topItems: List<Pair<String, Double>> = emptyList(),
    val totalSpent: Double = 0.0
)

class AnalyticsViewModel(
    private val shoppingListRepository: ShoppingListRepository,
    private val categoryRepository: CategoryRepository,
    private val productRepository: ProductRepository,
    private val priceRepository: PriceRepository,
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
                ) as T
            }
        }
    }

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadAnalyticsData()
    }

    fun setTimeRange(timeRange: TimeRange) {
        _uiState.update { it.copy(timeRange = timeRange) }
        loadAnalyticsData()
    }

    fun setRootCategory(categoryId: Long?) {
        _uiState.update { it.copy(currentRootCategoryId = categoryId) }
    }

    private fun loadAnalyticsData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val data = withContext(Dispatchers.IO) {
                    val timeRange = _uiState.value.timeRange
                    val now = LocalDateTime.now()
                    val startTime = when (timeRange) {
                        TimeRange.MONTH -> now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        TimeRange.YEAR -> now.withDayOfYear(1).withHour(0).withMinute(0).withSecond(0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        TimeRange.ALL_TIME -> 0L
                    }
                    val endTime = System.currentTimeMillis()

                    val lists = shoppingListRepository.getFinishedListsInTimeRange(startTime, endTime)
                    
                    // Fetch items for all lists in range
                    val allItems = lists.flatMap { list ->
                        shoppingListRepository.getItemsForListSync(list.id)
                    }
                    
                    // Fetch products and categories
                    val products = productRepository.getAllProductsOnce()
                    val categories = categoryRepository.getAllCategoriesOnce()
                    
                    // Placeholder aggregation
                    
                    Triple(emptyMap<Long, Double>(), emptyMap<String, Double>(), emptyList<Pair<String, Double>>())
                }
                
                _uiState.update { it.copy(
                    isLoading = false,
                    categorySpending = data.first,
                    monthlyTrend = data.second,
                    topItems = data.third
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
