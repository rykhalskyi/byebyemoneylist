package com.otakeeesen.byebyemoneylist.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.PriceEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductAliasEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ProductRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.PriceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProductUiState(
    val product: ProductEntity? = null,
    val aliases: List<ProductAliasEntity> = emptyList(),
    val prices: List<PriceEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
)

class ProductViewModel(
    private val productRepository: ProductRepository,
    private val priceRepository: PriceRepository,
    private val categoryRepository: CategoryRepository,
    private val productId: Long?
) : ViewModel() {

    companion object {
        fun createFactory(productId: Long?): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras,
            ): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as ByeByeMoneyApplication
                return ProductViewModel(
                    application.productRepository,
                    application.priceRepository,
                    application.categoryRepository,
                    productId
                ) as T
            }
        }
    }

    private val _uiState = MutableStateFlow(ProductUiState())
    val uiState: StateFlow<ProductUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val categories = withContext(Dispatchers.IO) { categoryRepository.getAllCategoriesOnce() }
            _uiState.update { it.copy(categories = categories) }
            
            productId?.let { id ->
                val product = withContext(Dispatchers.IO) { productRepository.getProductById(id) }
                val aliases = withContext(Dispatchers.IO) { productRepository.getAliasesByProductId(id) }
                
                _uiState.update { it.copy(product = product, aliases = aliases) }
                
                priceRepository.getPricesForProduct(id).collect { prices ->
                    _uiState.update { it.copy(prices = prices.sortedByDescending { p -> p.date }) }
                }
            }
        }
    }
}
