package com.otakeeesen.byebyemoneylist.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.compose.ui.graphics.Color
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductAliasEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ProductRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.StoreRepository
import com.otakeeesen.byebyemoneylist.ui.model.CategoryUiModel
import com.otakeeesen.byebyemoneylist.util.ImageStorageManager
import com.otakeeesen.byebyemoneylist.util.toHexString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val categories: List<CategoryUiModel> = emptyList(),
    val stores: List<StoreEntity> = emptyList(),
    val products: List<ProductEntity> = emptyList(),
    val filteredCategories: List<CategoryUiModel> = emptyList(),
    val filteredStores: List<StoreEntity> = emptyList(),
    val filteredProducts: List<ProductEntity> = emptyList(),
    val filteredSubscriptionProducts: List<ProductEntity> = emptyList(),
    val storeCategories: Map<Long, List<CategoryUiModel>> = emptyMap(),

    val searchQuery: String = "",
    val showSearchPanel: Boolean = false,
    val showFilterPanel: Boolean = false,
    val isSortAscending: Boolean = true,
    val selectedCategoryIds: Set<Long> = emptySet(),
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
                val uiCategories = categories.map {
                    CategoryUiModel(
                        id = it.id,
                        name = it.name,
                        color = try { Color(android.graphics.Color.parseColor(it.color)) } catch (e: Exception) { Color.Gray },
                        parentId = it.parentId
                    )
                }
                val uiCategoryMap = uiCategories.associateBy { it.id }
                val storeCategoriesMap = crossRefs.groupBy { it.storeId }
                    .mapValues { entry -> entry.value.mapNotNull { uiCategoryMap[it.categoryId] } }

                _uiState.update { 
                    it.copy(
                        stores = stores, 
                        categories = uiCategories,
                        storeCategories = storeCategoriesMap
                    ) 
                }
                applyFiltersAndSort()
            }.collect { }
        }
        
        viewModelScope.launch {
            productRepository.getProducts().collect { products ->
                _uiState.update { it.copy(products = products) }
                applyFiltersAndSort()
            }
        }

        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { applyFiltersAndSort() }
        }
    }

    fun selectTab(index: Int) {
        _searchQuery.value = ""
        _uiState.update { it.copy(selectedTab = index, searchQuery = "") }
        applyFiltersAndSort()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleSearchPanel() {
        _uiState.update { it.copy(showSearchPanel = !it.showSearchPanel, showFilterPanel = if (!it.showSearchPanel) false else it.showFilterPanel) }
    }

    fun toggleFilterPanel() {
        _uiState.update { it.copy(showFilterPanel = !it.showFilterPanel, showSearchPanel = if (!it.showFilterPanel) false else it.showSearchPanel) }
    }

    fun toggleSortOrder() {
        _uiState.update { it.copy(isSortAscending = !it.isSortAscending) }
        applyFiltersAndSort()
    }

    fun toggleCategoryFilter(categoryId: Long) {
        _uiState.update {
            val newSet = it.selectedCategoryIds.toMutableSet()
            if (newSet.contains(categoryId)) newSet.remove(categoryId)
            else newSet.add(categoryId)
            it.copy(selectedCategoryIds = newSet)
        }
        applyFiltersAndSort()
    }

    fun clearFilters() {
        _uiState.update { it.copy(selectedCategoryIds = emptySet()) }
        applyFiltersAndSort()
    }

    private fun applyFiltersAndSort() {
        val state = _uiState.value
        val query = state.searchQuery.lowercase().trim()
        val categoryMap = state.categories.associateBy { it.id }

        // Pre-calculate matched categories
        val activeCategoryIds = mutableSetOf<Long>()
        state.selectedCategoryIds.forEach { id ->
            var current: CategoryUiModel? = categoryMap[id]
            while (current != null) {
                activeCategoryIds.add(current.id)
                current = current.parentId?.let { categoryMap[it] }
            }
        }

        // Helper to filter and sort generic lists by name
        fun <T> List<T>.filterAndSort(
            nameExtractor: (T) -> String,
            isFiltered: (T) -> Boolean = { true }
        ): List<T> {
            val filtered = if (query.isBlank()) this.filter(isFiltered)
            else this.filter { isFiltered(it) && nameExtractor(it).lowercase().contains(query) }
            
            return if (state.isSortAscending) filtered.sortedBy { nameExtractor(it) }
            else filtered.sortedByDescending { nameExtractor(it) }
        }

        _uiState.update {
            it.copy(
                filteredCategories = state.categories.filterAndSort({ c -> c.name }),
                filteredStores = state.stores.filterAndSort({ s -> s.name }, { s ->
                    state.selectedCategoryIds.isEmpty() ||
                    (state.storeCategories[s.id]?.any { it.id in activeCategoryIds } ?: false)
                }),
                filteredProducts = state.products.filterAndSort({ p -> p.name }, { p ->
                    !p.isSubscription && (state.selectedCategoryIds.isEmpty() || run {
                        val productCategory = state.categories.find { it.name.equals(p.category, ignoreCase = true) }
                        productCategory?.id in activeCategoryIds
                    })
                }),
                filteredSubscriptionProducts = state.products.filterAndSort({ p -> p.name }, { p ->
                    p.isSubscription && (state.selectedCategoryIds.isEmpty() || run {
                        val productCategory = state.categories.find { it.name.equals(p.category, ignoreCase = true) }
                        productCategory?.id in activeCategoryIds
                    })
                }),
            )
        }
    }

    fun showCreateCategoryDialog() {
        _uiState.update { it.copy(categoryDialogVisible = true, editingCategory = null) }
    }

    fun showEditCategoryDialog(category: CategoryUiModel) {
        val entity = CategoryEntity(id = category.id, name = category.name, color = toHexString(category.color), parentId = category.parentId)
        _uiState.update { it.copy(categoryDialogVisible = true, editingCategory = entity) }
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

    fun requestDeleteCategory(category: CategoryUiModel) {
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

    private fun performDeleteCategory(category: CategoryUiModel) {
        viewModelScope.launch {
            val entity = CategoryEntity(id = category.id, name = category.name, color = toHexString(category.color), parentId = category.parentId)
            withContext(Dispatchers.IO) { categoryRepository.deleteCategory(entity) }
            _events.value = CatalogEvent.CategoryDeleted(category.name)
            setupUndo { performUndeleteCategory(category) }
        }
    }

    private fun performUndeleteCategory(category: CategoryUiModel) {
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
