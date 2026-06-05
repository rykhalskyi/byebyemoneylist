package com.otakeeesen.byebyemoneylist.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductAliasEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreCategoryCrossRef
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ProductRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.StoreRepository
import com.otakeeesen.byebyemoneylist.util.ImageStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val filteredSubscriptionProducts: List<ProductEntity> = emptyList(),
    val storeCategories: Map<Long, List<CategoryEntity>> = emptyMap(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val categoryDialogVisible: Boolean = false,
    val productDialogVisible: Boolean = false,
    val editingCategory: CategoryEntity? = null,
    val isCreatingStore: Boolean = false,
    val editingStore: StoreEntity? = null,
    val editingStoreCategories: List<CategoryEntity> = emptyList(),
    val editingProduct: ProductEntity? = null,
    val editingProductAliases: List<ProductAliasEntity> = emptyList(),
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
            combine(
                storeRepository.allStores,
                categoryRepository.allCategories,
                storeRepository.getAllStoreCategoryCrossRefs()
            ) { stores, categories, crossRefs ->
                val categoryMap = categories.associateBy { it.id }
                val storeCategoriesMap = crossRefs.groupBy { it.storeId }
                    .mapValues { entry -> entry.value.mapNotNull { categoryMap[it.categoryId] } }
                
                _uiState.update { 
                    it.copy(
                        stores = stores, 
                        categories = categories,
                        storeCategories = storeCategoriesMap
                    ) 
                }
                applySearchFilter()
            }.collect { }
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
        _searchQuery.value = ""
        _uiState.update { it.copy(selectedTab = index, searchQuery = "") }
        applySearchFilter()
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
                filteredProducts = if (query.isBlank()) state.products.filter { !it.isSubscription }
                    else state.products.filter { p -> !p.isSubscription && p.name.lowercase().contains(query) },
                filteredSubscriptionProducts = if (query.isBlank()) state.products.filter { it.isSubscription }
                    else state.products.filter { p -> p.isSubscription && p.name.lowercase().contains(query) },
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

    fun saveCategory(name: String, color: String, parentId: Long?) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val editing = _uiState.value.editingCategory
                if (editing != null) {
                    categoryRepository.updateCategory(editing.copy(name = name, color = color, parentId = parentId))
                } else {
                    val id = System.currentTimeMillis()
                    categoryRepository.insertCategory(CategoryEntity(id = id, name = name, color = color, parentId = parentId))
                }
            }
            dismissCategoryDialog()
        }
    }

    fun showCreateStore() {
        _uiState.update { it.copy(isCreatingStore = true, editingStore = null, editingStoreCategories = emptyList()) }
    }

    fun showEditStore(store: StoreEntity) {
        viewModelScope.launch {
            val selected = categoryRepository.getCategoriesByStoreIdOnce(store.id)
            _uiState.update { 
                it.copy(
                    isCreatingStore = false,
                    editingStore = store,
                    editingStoreCategories = selected
                ) 
            }
        }
    }

    fun clearEditingStore() {
        _uiState.update { it.copy(isCreatingStore = false, editingStore = null, editingStoreCategories = emptyList()) }
    }

    fun saveStore(storeId: Long?, name: String, logoPath: String, categoryIds: List<Long>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val existing = if (storeId != null) storeRepository.getAllStoresOnce().find { it.id == storeId } else null
                val finalId = storeId ?: System.currentTimeMillis()

                if (existing != null) {
                    if (existing.logoPath != null && existing.logoPath != logoPath) {
                        ImageStorageManager.deleteImage(existing.logoPath)
                    }
                    storeRepository.updateStore(
                        existing.copy(name = name, logoPath = logoPath.ifBlank { null }),
                        categoryIds
                    )
                } else {
                    storeRepository.insertStore(
                        StoreEntity(id = finalId, name = name, logoPath = logoPath.ifBlank { null }),
                        categoryIds
                    )
                }
            }
            clearEditingStore()
        }
    }

    fun showCreateProductDialog() {
        _uiState.update { it.copy(productDialogVisible = true, editingProduct = null) }
    }

    fun showEditProductDialog(product: ProductEntity) {
        viewModelScope.launch {
            val aliases = withContext(Dispatchers.IO) {
                productRepository.getAliasesByProductId(product.id)
            }
            _uiState.update { it.copy(productDialogVisible = true, editingProduct = product, editingProductAliases = aliases) }
        }
    }

    fun dismissProductDialog() {
        _uiState.update { it.copy(productDialogVisible = false, editingProduct = null) }
    }

    fun saveProduct(productId: Long?, name: String, barcode: String, picturePath: String, category: String, aliasNames: List<String>, isSubscription: Boolean = false) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val existing = if (productId != null) productRepository.getProductById(productId) else null
                val finalId = productId ?: System.currentTimeMillis()

                if (existing != null) {
                    if (existing.picturePath != null && existing.picturePath != picturePath) {
                        ImageStorageManager.deleteImage(existing.picturePath)
                    }
                    productRepository.updateProduct(
                        existing.copy(
                            name = name,
                            barcode = barcode,
                            picturePath = picturePath.ifBlank { null },
                            category = category,
                            isSubscription = isSubscription
                        )
                    )
                } else {
                    productRepository.insertProduct(
                        ProductEntity(
                            id = finalId,
                            name = name,
                            barcode = barcode,
                            picturePath = picturePath.ifBlank { null },
                            category = category,
                            isSubscription = isSubscription
                        )
                    )
                }
                // Manage aliases
                val existingAliases = productRepository.getAliasesByProductId(finalId)
                // Remove old aliases not in new list
                existingAliases.filter { it.aliasName !in aliasNames }.forEach {
                    productRepository.deleteAlias(it)
                }
                // Insert new aliases
                aliasNames.filter { alias -> existingAliases.none { it.aliasName == alias } }.forEach {
                    productRepository.insertAlias(ProductAliasEntity(id = System.currentTimeMillis() + aliasNames.indexOf(it), productId = finalId, aliasName = it))
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
            ImageStorageManager.deleteImage(store.logoPath)
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
            ImageStorageManager.deleteImage(product.picturePath)
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
