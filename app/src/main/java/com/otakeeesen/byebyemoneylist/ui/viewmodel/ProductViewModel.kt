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
import com.otakeeesen.byebyemoneylist.util.ImageStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProductUiState(
    val isLoading: Boolean = true,
    val name: String = "",
    val barcode: String = "",
    val picturePath: String = "",
    val categoryId: Long? = null,
    val aliases: List<String> = emptyList(),
    val isSubscription: Boolean = false,
    val isIncome: Boolean = false,
    val isFavorite: Boolean = false,
    val product: ProductEntity? = null,
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
            
            if (productId != null) {
                val product = withContext(Dispatchers.IO) { productRepository.getProductById(productId) }
                val aliases = withContext(Dispatchers.IO) { productRepository.getAliasesByProductId(productId) }
                
                _uiState.update { 
                    it.copy(
                        product = product,
                        name = product?.name ?: "",
                        barcode = product?.barcode ?: "",
                        picturePath = product?.picturePath ?: "",
                        categoryId = product?.categoryId,
                        isSubscription = product?.isSubscription ?: false,
                        isIncome = product?.isIncome ?: false,
                        isFavorite = product?.isFavorite ?: false,
                        aliases = aliases.map { a -> a.aliasName },
                        categories = categories,
                        isLoading = false
                    )
                }
                
                // Launch price collection in a separate coroutine to not block initialization
                viewModelScope.launch {
                    priceRepository.getPricesForProduct(productId).collect { prices ->
                        _uiState.update { it.copy(prices = prices.sortedByDescending { p -> p.date }) }
                    }
                }
            } else {
                _uiState.update { it.copy(categories = categories, isLoading = false) }
            }
        }
    }

    fun updateName(name: String) { _uiState.update { it.copy(name = name) } }
    fun updateBarcode(barcode: String) { _uiState.update { it.copy(barcode = barcode) } }
    fun updatePicturePath(path: String) { _uiState.update { it.copy(picturePath = path) } }
    fun updateCategoryId(id: Long?) { 
        _uiState.update { 
            val isIncome = it.categories.find { c -> c.id == id }?.isIncome ?: it.isIncome
            it.copy(categoryId = id, isIncome = isIncome) 
        } 
    }
    fun updateAliases(aliases: List<String>) { _uiState.update { it.copy(aliases = aliases) } }
    fun updateSubscription(isSubscription: Boolean) { _uiState.update { it.copy(isSubscription = isSubscription) } }
    fun updateFavorite(isFavorite: Boolean) { _uiState.update { it.copy(isFavorite = isFavorite) } }

    fun saveProduct(onComplete: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val state = _uiState.value
                val existing = state.product
                val finalId = productId ?: System.currentTimeMillis()

                if (existing != null) {
                    if (existing.picturePath != null && existing.picturePath != state.picturePath) {
                        ImageStorageManager.deleteImage(existing.picturePath)
                    }
                    productRepository.updateProduct(
                        existing.copy(
                            name = state.name,
                            barcode = state.barcode,
                            picturePath = state.picturePath.ifBlank { null },
                            categoryId = state.categoryId,
                            isSubscription = state.isSubscription,
                            isFavorite = state.isFavorite,
                            isIncome = state.isIncome
                        )
                    )
                } else {
                    productRepository.insertProduct(
                        ProductEntity(
                            id = finalId,
                            name = state.name,
                            barcode = state.barcode,
                            picturePath = state.picturePath.ifBlank { null },
                            categoryId = state.categoryId,
                            isSubscription = state.isSubscription,
                            isFavorite = state.isFavorite,
                            isIncome = state.isIncome
                        )
                    )
                }
                // Manage aliases
                val existingAliases = productRepository.getAliasesByProductId(finalId)
                // Remove old aliases not in new list
                existingAliases.filter { it.aliasName !in state.aliases }.forEach {
                    productRepository.deleteAlias(it)
                }
                // Insert new aliases
                state.aliases.filter { alias -> existingAliases.none { it.aliasName == alias } }.forEach {
                    productRepository.insertAlias(ProductAliasEntity(id = System.currentTimeMillis() + state.aliases.indexOf(it), productId = finalId, aliasName = it))
                }
            }
            onComplete()
        }
    }
}
