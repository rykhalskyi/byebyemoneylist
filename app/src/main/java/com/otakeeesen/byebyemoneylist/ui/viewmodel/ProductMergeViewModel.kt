package com.otakeeesen.byebyemoneylist.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.ProductRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProductMergeUiState(
    val productA: ProductEntity? = null,
    val productB: ProductEntity? = null,
    val selectedName: String = "",
    val selectedBarcode: String = "",
    val selectedCategoryId: Long? = null,
    val selectedPicturePath: String? = null,
    val isMerging: Boolean = false,
    val mergeComplete: Boolean = false,
    val allCategories: List<CategoryEntity> = emptyList()
)

class ProductMergeViewModel(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
    private val productAId: Long,
    private val productBId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductMergeUiState())
    val uiState: StateFlow<ProductMergeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val a = withContext(Dispatchers.IO) { productRepository.getProductById(productAId) }
            val b = withContext(Dispatchers.IO) { productRepository.getProductById(productBId) }
            val categories = withContext(Dispatchers.IO) { categoryRepository.getAllCategoriesOnce() }
            
            _uiState.update { 
                it.copy(
                    productA = a,
                    productB = b,
                    selectedName = a?.name ?: "",
                    selectedBarcode = a?.barcode ?: "",
                    selectedCategoryId = a?.categoryId,
                    selectedPicturePath = a?.picturePath,
                    allCategories = categories
                ) 
            }
        }
    }

    fun selectName(name: String) { _uiState.update { it.copy(selectedName = name) } }
    fun selectBarcode(barcode: String) { _uiState.update { it.copy(selectedBarcode = barcode) } }
    fun selectCategory(categoryId: Long?) { _uiState.update { it.copy(selectedCategoryId = categoryId) } }
    fun selectPicturePath(path: String?) { _uiState.update { it.copy(selectedPicturePath = path) } }

    fun updateName(name: String) { _uiState.update { it.copy(selectedName = name) } }
    fun updateBarcode(barcode: String) { _uiState.update { it.copy(selectedBarcode = barcode) } }
    fun updateCategory(categoryId: Long?) { _uiState.update { it.copy(selectedCategoryId = categoryId) } }

    fun performMerge() {
        val state = _uiState.value
        val a = state.productA ?: return
        val b = state.productB ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isMerging = true) }
            
            val resultProduct = a.copy(
                name = state.selectedName,
                barcode = state.selectedBarcode,
                categoryId = state.selectedCategoryId,
                picturePath = state.selectedPicturePath,
                status = "reviewed",
                changedAt = System.currentTimeMillis()
            )

            withContext(Dispatchers.IO) {
                productRepository.mergeProducts(a, b, resultProduct)
            }
            
            _uiState.update { it.copy(isMerging = false, mergeComplete = true) }
        }
    }

    companion object {
        fun createFactory(productAId: Long, productBId: Long): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as ByeByeMoneyApplication
                return ProductMergeViewModel(application.productRepository, application.categoryRepository, productAId, productBId) as T
            }
        }
    }
}
