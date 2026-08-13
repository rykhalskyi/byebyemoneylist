package com.otakeeesen.byebyemoneylist.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.data.PurchaseListNameGenerator
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.PriceRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ProductRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.StoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class QuickPurchaseUiState(
    val price: String = "",
    val storeText: String = "",
    val stores: List<StoreEntity> = emptyList(),
    val expenseCategories: List<CategoryEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    val purchaseComplete: Boolean = false,
    val error: String? = null,
)

class QuickPurchaseViewModel(
    private val storeRepository: StoreRepository,
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
                return QuickPurchaseViewModel(
                    storeRepository = application.storeRepository,
                    shoppingListRepository = application.shoppingListRepository,
                    categoryRepository = application.categoryRepository,
                    productRepository = application.productRepository,
                    priceRepository = application.priceRepository,
                ) as T
            }
        }
    }

    private val _uiState = MutableStateFlow(QuickPurchaseUiState())
    val uiState: StateFlow<QuickPurchaseUiState> = _uiState.asStateFlow()

    init {
        loadStores()
        loadCategories()
    }

    private fun loadStores() {
        viewModelScope.launch {
            storeRepository.allStores.collect { stores ->
                _uiState.update { it.copy(stores = stores) }
            }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            categoryRepository.allCategories.collect { categories ->
                _uiState.update { it.copy(expenseCategories = categories.filter { !it.isIncome }) }
            }
        }
    }

    fun updatePrice(text: String) {
        val filtered = text.filter { it.isDigit() || it == '.' || it == ',' }
        _uiState.update { it.copy(price = filtered, error = null) }
    }

    fun updateStore(text: String) {
        _uiState.update { it.copy(storeText = text, error = null) }
    }

    fun selectCategory(category: CategoryEntity) {
        val state = _uiState.value
        val storeName = state.storeText.trim()
        if (storeName.isBlank()) {
            _uiState.update { it.copy(error = "Store name is required") }
            return
        }
        val price = state.price.trim().replace(',', '.').toDoubleOrNull()
        if (price == null || price <= 0.0) {
            _uiState.update { it.copy(error = "Invalid price") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true, error = null) }
            try {
                val generator = PurchaseListNameGenerator(shoppingListRepository)
                val listName = generator.generate(storeName)

                withContext(Dispatchers.IO) {
                    shoppingListRepository.processPurchase(
                        listId = null,
                        listName = listName,
                        storeName = storeName,
                        price = price,
                        items = emptyList(),
                        productRepository = productRepository,
                        priceRepository = priceRepository,
                        categoryRepository = categoryRepository,
                        categoryId = category.id,
                    )
                }
                _uiState.update { it.copy(isCreating = false, purchaseComplete = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isCreating = false, error = e.message ?: "Purchase failed") }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
