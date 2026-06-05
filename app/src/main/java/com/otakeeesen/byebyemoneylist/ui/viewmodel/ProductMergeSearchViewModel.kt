package com.otakeeesen.byebyemoneylist.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.ProductRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ProductMergeSearchUiState(
    val products: List<ProductEntity> = emptyList(),
    val filteredProducts: List<ProductEntity> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false
)

class ProductMergeSearchViewModel(
    private val productRepository: ProductRepository,
    private val productAId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductMergeSearchUiState())
    val uiState: StateFlow<ProductMergeSearchUiState> = _uiState.asStateFlow()

    init {
        loadProducts()
    }

    private fun loadProducts() {
        viewModelScope.launch {
            productRepository.getProducts().collect { products ->
                val otherProducts = products.filter { it.id != productAId }
                _uiState.update { 
                    it.copy(
                        products = otherProducts,
                        filteredProducts = applyFilter(otherProducts, it.searchQuery)
                    ) 
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { 
            it.copy(
                searchQuery = query,
                filteredProducts = applyFilter(it.products, query)
            ) 
        }
    }

    private fun applyFilter(products: List<ProductEntity>, query: String): List<ProductEntity> {
        if (query.isBlank()) return products
        val lowerQuery = query.lowercase().trim()
        return products.filter { it.name.lowercase().contains(lowerQuery) }
    }

    companion object {
        fun createFactory(productAId: Long): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as ByeByeMoneyApplication
                return ProductMergeSearchViewModel(application.productRepository, productAId) as T
            }
        }
    }
}
