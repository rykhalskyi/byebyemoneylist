package com.otakeeesen.byebyemoneylist.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ProductRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.StoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CatalogUiState(
    val selectedTab: Int = 0,
    val categories: List<CategoryEntity> = emptyList(),
    val stores: List<StoreEntity> = emptyList(),
    val products: List<ProductEntity> = emptyList(),
    val filteredCategories: List<CategoryEntity> = emptyList(),
    val filteredStores: List<StoreEntity> = emptyList(),
    val filteredProducts: List<ProductEntity> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val categoryDialogVisible: Boolean = false,
    val storeDialogVisible: Boolean = false,
    val productDialogVisible: Boolean = false,
    val editingCategory: CategoryEntity? = null,
    val editingStore: StoreEntity? = null,
    val editingProduct: ProductEntity? = null,
    val deleteConfirmMessage: String? = null,
    val deleteAction: (() -> Unit)? = null,
)

sealed class CatalogEvent {
    data class CategoryDeleted(val name: String) : CatalogEvent()
    data class StoreDeleted(val name: String) : CatalogEvent()
    data class ProductDeleted(val name: String) : CatalogEvent()
}

class CatalogViewModel(
    private val categoryRepository: CategoryRepository,
    private val storeRepository: StoreRepository,
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
                return CatalogViewModel(
                    application.categoryRepository,
                    application.storeRepository,
                    application.productRepository,
                ) as T
            }
        }
    }

    private val _uiState = MutableStateFlow(CatalogUiState())
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<CatalogEvent?>(null)
    val events: StateFlow<CatalogEvent?> = _events.asStateFlow()

    private var undoDeleteJob: Job? = null
    private var undoableDelete: (() -> Unit)? = null

    private val _searchQuery = MutableStateFlow("")

    init {
        viewModelScope.launch {
            categoryRepository.allCategories.collect { categories ->
                _uiState.update { it.copy(categories = categories) }
                applySearchFilter()
            }
        }
        viewModelScope.launch {
            storeRepository.allStores.collect { stores ->
                _uiState.update { it.copy(stores = stores) }
                applySearchFilter()
            }
        }
        viewModelScope.launch {
            productRepository.getProducts().collect { products ->
                _uiState.update { it.copy(products = products) }
                applySearchFilter()
            }
        }

        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { applySearchFilter() }
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    private fun applySearchFilter() {
        val state = _uiState.value
        val query = state.searchQuery.lowercase().trim()
        _uiState.update {
            it.copy(
                filteredCategories = if (query.isBlank()) state.categories
                    else state.categories.filter { c -> c.name.lowercase().contains(query) },
                filteredStores = if (query.isBlank()) state.stores
                    else state.stores.filter { s -> s.name.lowercase().contains(query) },
                filteredProducts = if (query.isBlank()) state.products
                    else state.products.filter { p -> p.name.lowercase().contains(query) },
            )
        }
    }

    fun showCreateCategoryDialog() {
        _uiState.update { it.copy(categoryDialogVisible = true, editingCategory = null) }
    }

    fun showEditCategoryDialog(category: CategoryEntity) {
        _uiState.update { it.copy(categoryDialogVisible = true, editingCategory = category) }
    }

    fun dismissCategoryDialog() {
        _uiState.update { it.copy(categoryDialogVisible = false, editingCategory = null) }
    }

    fun saveCategory(name: String, color: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val editing = _uiState.value.editingCategory
                if (editing != null) {
                    categoryRepository.updateCategory(editing.copy(name = name, color = color))
                } else {
                    val id = System.currentTimeMillis()
                    categoryRepository.insertCategory(CategoryEntity(id = id, name = name, color = color))
                }
            }
            dismissCategoryDialog()
        }
    }

    fun showCreateStoreDialog() {
        _uiState.update { it.copy(storeDialogVisible = true, editingStore = null) }
    }

    fun showEditStoreDialog(store: StoreEntity) {
        _uiState.update { it.copy(storeDialogVisible = true, editingStore = store) }
    }

    fun dismissStoreDialog() {
        _uiState.update { it.copy(storeDialogVisible = false, editingStore = null) }
    }

    fun saveStore(name: String, logoPath: String, category: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val editing = _uiState.value.editingStore
                if (editing != null) {
                    storeRepository.updateStore(
                        editing.copy(name = name, logoPath = logoPath.ifBlank { null }, category = category)
                    )
                } else {
                    storeRepository.getOrCreate(name, category)
                }
            }
            dismissStoreDialog()
        }
    }

    fun showCreateProductDialog() {
        _uiState.update { it.copy(productDialogVisible = true, editingProduct = null) }
    }

    fun showEditProductDialog(product: ProductEntity) {
        _uiState.update { it.copy(productDialogVisible = true, editingProduct = product) }
    }

    fun dismissProductDialog() {
        _uiState.update { it.copy(productDialogVisible = false, editingProduct = null) }
    }

    fun saveProduct(name: String, barcode: String, picturePath: String, category: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val editing = _uiState.value.editingProduct
                if (editing != null) {
                    productRepository.updateProduct(
                        editing.copy(
                            name = name,
                            barcode = barcode,
                            picturePath = picturePath.ifBlank { null },
                            category = category,
                        )
                    )
                } else {
                    val id = System.currentTimeMillis()
                    productRepository.insertProduct(
                        ProductEntity(
                            id = id,
                            name = name,
                            barcode = barcode,
                            picturePath = picturePath.ifBlank { null },
                            category = category,
                        )
                    )
                }
            }
            dismissProductDialog()
        }
    }

    fun requestDeleteCategory(category: CategoryEntity) {
        _uiState.update {
            it.copy(
                deleteConfirmMessage = "Delete category \"${category.name}\"?",
                deleteAction = { performDeleteCategory(category) },
            )
        }
    }

    fun requestDeleteStore(store: StoreEntity) {
        _uiState.update {
            it.copy(
                deleteConfirmMessage = "Delete store \"${store.name}\"?",
                deleteAction = { performDeleteStore(store) },
            )
        }
    }

    fun requestDeleteProduct(product: ProductEntity) {
        _uiState.update {
            it.copy(
                deleteConfirmMessage = "Delete product \"${product.name}\"?",
                deleteAction = { performDeleteProduct(product) },
            )
        }
    }

    fun dismissDeleteConfirm() {
        _uiState.update { it.copy(deleteConfirmMessage = null, deleteAction = null) }
    }

    fun confirmDelete() {
        val action = _uiState.value.deleteAction
        dismissDeleteConfirm()
        action?.invoke()
    }

    private fun performDeleteCategory(category: CategoryEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { categoryRepository.deleteCategory(category) }
            _events.value = CatalogEvent.CategoryDeleted(category.name)
            setupUndo { performUndeleteCategory(category) }
        }
    }

    private fun performUndeleteCategory(category: CategoryEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { categoryRepository.getOrCreate(category.name) }
        }
    }

    private fun performDeleteStore(store: StoreEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { storeRepository.deleteStore(store.id) }
            _events.value = CatalogEvent.StoreDeleted(store.name)
            setupUndo { performUndeleteStore(store) }
        }
    }

    private fun performUndeleteStore(store: StoreEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { storeRepository.insertStore(store) }
        }
    }

    private fun performDeleteProduct(product: ProductEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { productRepository.deleteProduct(product) }
            _events.value = CatalogEvent.ProductDeleted(product.name)
            setupUndo { performUndeleteProduct(product) }
        }
    }

    private fun performUndeleteProduct(product: ProductEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { productRepository.insertProduct(product) }
        }
    }

    private fun setupUndo(restore: () -> Unit) {
        undoDeleteJob?.cancel()
        undoableDelete = restore
        undoDeleteJob = viewModelScope.launch {
            delay(4_000L)
            undoableDelete = null
        }
    }

    fun undoDelete() {
        undoDeleteJob?.cancel()
        undoableDelete?.invoke()
        undoableDelete = null
    }

    fun clearEvent() {
        _events.value = null
    }
}
