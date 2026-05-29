package com.otakeeesen.byebyemoneylist.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReviewListViewModel(
    private val productRepository: ProductRepository,
) : ViewModel() {

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras,
            ): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as ByeByeMoneyApplication
                return ReviewListViewModel(
                    application.productRepository,
                ) as T
            }
        }
    }

    private val _allProducts = MutableStateFlow<List<ProductEntity>>(emptyList())
    val allProducts: StateFlow<List<ProductEntity>> = _allProducts.asStateFlow()

    init {
        loadProducts()
    }

    private fun loadProducts() {
        viewModelScope.launch {
            _allProducts.value = productRepository.getAllProductsOnce()
        }
    }
}
