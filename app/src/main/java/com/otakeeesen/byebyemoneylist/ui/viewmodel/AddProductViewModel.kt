package com.otakeeesen.byebyemoneylist.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListItemEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ProductRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AddProductUiState(
    val searchQuery: String = "",
    val searchResults: List<ProductEntity> = emptyList(),
    val isLoading: Boolean = false,
)

class AddProductViewModel(
    private val listId: Long,
    private val productRepository: ProductRepository,
    private val shoppingListRepository: ShoppingListRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    @OptIn(FlowPreview::class)
    val uiState: StateFlow<AddProductUiState> = _searchQuery
        .debounce(300L)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                MutableStateFlow(emptyList<ProductEntity>())
            } else {
                productRepository.searchProducts(query)
            }
        }
        .combine(_searchQuery) { results, query ->
            AddProductUiState(
                searchQuery = query,
                searchResults = results,
                isLoading = false
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AddProductUiState()
        )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addExistingProduct(productId: Long, onComplete: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                shoppingListRepository.insertShoppingListItem(
                    ShoppingListItemEntity(
                        id = generateId(),
                        shoppingListId = listId,
                        productId = productId,
                        quantity = 1,
                        isChecked = false
                    )
                )
            }
            onComplete()
        }
    }

    fun createAndAddProduct(name: String, categoryName: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val categoryId = if (categoryName.isNotBlank()) {
                    categoryRepository.getOrCreate(categoryName)
                } else null

                val productId = generateId()
                val product = ProductEntity(
                    id = productId,
                    name = name,
                    barcode = "",
                    picturePath = null,
                    category = categoryName
                )
                productRepository.insertProduct(product)

                shoppingListRepository.insertShoppingListItem(
                    ShoppingListItemEntity(
                        id = generateId(),
                        shoppingListId = listId,
                        productId = productId,
                        quantity = 1,
                        isChecked = false
                    )
                )
            }
            onComplete()
        }
    }

    private fun generateId(): Long = System.currentTimeMillis()

    companion object {
        fun provideFactory(listId: Long): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras,
            ): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as ByeByeMoneyApplication
                return AddProductViewModel(
                    listId,
                    application.productRepository,
                    application.shoppingListRepository,
                    application.categoryRepository,
                ) as T
            }
        }
    }
}
