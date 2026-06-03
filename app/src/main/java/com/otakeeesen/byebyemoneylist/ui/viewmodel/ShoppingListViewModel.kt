package com.otakeeesen.byebyemoneylist.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.BuildConfig
import com.otakeeesen.byebyemoneylist.data.PurchaseItem
import com.otakeeesen.byebyemoneylist.data.ShoppingList
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryColors
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListItemEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductAliasEntity
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.PriceRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ProductRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import com.otakeeesen.byebyemoneylist.util.ImageStorageManager
import com.otakeeesen.byebyemoneylist.ui.components.scanner.ScannedReceipt
import com.otakeeesen.byebyemoneylist.ui.components.scanner.ScannedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ShoppingListUiState(
    val shoppingLists: List<ShoppingList> = emptyList(),
    val displayItems: List<ShoppingListItem> = emptyList(),
    val expandedYears: Set<Int> = emptySet(),
    val expandedMonths: Set<String> = emptySet(),
    val expandedCards: Set<Long> = emptySet(),
    val inStoreListIds: Set<Long> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showInStoreDialog: Boolean = false,
    val editingItem: PurchaseItem? = null,
    val editingList: ShoppingList? = null,
    val showReviewDialog: Boolean = false,
    val selectedReviewList: ShoppingList? = null,
    val selectedReviewListId: Long? = null,
    val showWelcomeDialog: Boolean = false,
    val hideCheckedItems: Boolean = false,
    val isSortAscending: Boolean = false,
    val filterQuery: String = "",
    val selectedCategoryIds: Set<Long> = emptySet(),
    val filterRecurring: Boolean? = null, // null = all, true = recurring, false = regular
    val showFilterPanel: Boolean = false,
    val showSearchPanel: Boolean = false,
)

sealed class ShoppingListItem {
    data class YearHeader(val year: Int, val isExpanded: Boolean, val totalPrice: Double) : ShoppingListItem()
    data class MonthHeader(val yearMonth: String, val monthName: String, val isExpanded: Boolean, val totalPrice: Double) : ShoppingListItem()
    data class ListContent(val shoppingList: ShoppingList, val yearMonth: String) : ShoppingListItem()
}

data class CreateListDialogState(
    val categories: List<CategoryEntity> = emptyList(),
    val stores: List<StoreEntity> = emptyList(),
    val products: List<ProductEntity> = emptyList(),
    val aliases: List<ProductAliasEntity> = emptyList(),
)

sealed class UiEvent {
    data class ItemDeleted(val item: PurchaseItem) : UiEvent()
}

class ShoppingListViewModel(
    private val repository: ShoppingListRepository,
    private val categoryRepository: CategoryRepository,
    private val priceRepository: PriceRepository,
    private val productRepository: ProductRepository,
    private val preferencesManager: PreferencesManager,
) : ViewModel() {

     companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras,
            ): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as ByeByeMoneyApplication
                return ShoppingListViewModel(
                    application.shoppingListRepository,
                    application.categoryRepository,
                    application.priceRepository,
                    application.productRepository,
                    application.preferencesManager,
                ) as T
            }
        }
    }

    private val _uiState = MutableStateFlow(ShoppingListUiState())
    val uiState: StateFlow<ShoppingListUiState> = _uiState.asStateFlow()

    private val _dialogState = MutableStateFlow(CreateListDialogState())
    val dialogState: StateFlow<CreateListDialogState> = _dialogState.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    private val _expandedYears = MutableStateFlow<Set<Int>>(setOf(java.time.LocalDate.now().year))
    private val _expandedMonths = MutableStateFlow<Set<String>>(setOf(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM", Locale.getDefault()))))
    private val _expandedCards = MutableStateFlow<Set<Long>>(emptySet())
    private val _isSortAscending = MutableStateFlow(false)
    private val _filterQuery = MutableStateFlow("")
    private val _selectedCategoryIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _filterRecurring = MutableStateFlow<Boolean?>(null)
    private val _showFilterPanel = MutableStateFlow(false)
    private val _showSearchPanel = MutableStateFlow(false)

    private var undoableItem: ShoppingListItemEntity? = null
    private var undoJob: Job? = null

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.checkAndForwardRecurringLists()
            }
        }

        viewModelScope.launch {
            repository.allShoppingLists.collect { shoppingLists ->
                val shouldShowWelcome = shoppingLists.isEmpty()
                _uiState.update { it.copy(showWelcomeDialog = shouldShowWelcome) }
            }
        }

        _uiState.update { it.copy(hideCheckedItems = preferencesManager.getHideCheckedItems()) }

        viewModelScope.launch {
            val listFlow = combine(
                repository.allShoppingLists,
                repository.getAllItemsWithProduct(),
                repository.getAllShoppingListCategoryCrossRefs()
            ) { lists, items, crossRefs ->
                Triple(lists, items, crossRefs)
            }

            val filterFlow = combine(
                _isSortAscending,
                _filterQuery,
                _selectedCategoryIds,
                _filterRecurring,
                _showFilterPanel,
                _showSearchPanel
            ) { args ->
                 FilterState(
                     isSortAscending = args[0] as Boolean,
                     filterQuery = args[1] as String,
                     selectedCategoryIds = args[2] as Set<Long>,
                     filterRecurring = args[3] as Boolean?,
                     showFilterPanel = args[4] as Boolean,
                     showSearchPanel = args[5] as Boolean
                 )
            }

            combine(
                listFlow,
                _expandedYears,
                _expandedMonths,
                _expandedCards,
                filterFlow
            ) { (entities, itemsWithProduct, categoryCrossRefs), 
                expandedYears, expandedMonths, expandedCards,
                filters ->
                
                val storeList = withContext(Dispatchers.IO) { repository.getAllStoresOnce() }
                val categoryList = withContext(Dispatchers.IO) { categoryRepository.getAllCategoriesOnce() }
                val storeMap = storeList.associateBy { it.id }
                val categoryMap = categoryList.associateBy { it.id }
                val crossRefsByListId = categoryCrossRefs.groupBy { it.shoppingListId }
                val itemsByListId = itemsWithProduct.groupBy { it.shoppingListId }

                val shoppingLists = entities.map { entity ->
                    val store = entity.storeId?.let { storeMap[it] }
                    val listCategories = crossRefsByListId[entity.id]?.mapNotNull { categoryMap[it.categoryId] } ?: emptyList()
                    val items = (itemsByListId[entity.id]?.map { item ->
                        PurchaseItem(
                            id = item.id,
                            productId = item.productId,
                            name = item.productName ?: "Unknown",
                            price = item.itemPrice ?: item.price,
                            imageUrl = item.productPicturePath ?: "",
                            checked = item.isChecked,
                            position = item.position,
                            productStatus = item.productStatus,
                        )
                    } ?: emptyList()).sortedBy { it.position }

                    entity.toDomain(
                        items = items,
                        storeName = store?.name,
                        categories = listCategories,
                        position = entity.position,
                    )
                }

                val filteredLists = shoppingLists.filter { list ->
                    val matchesQuery = filters.filterQuery.isBlank() || 
                            list.title.contains(filters.filterQuery, ignoreCase = true) ||
                            (list.storeName?.contains(filters.filterQuery, ignoreCase = true) == true)
                    
                    val matchesCategories = if (filters.selectedCategoryIds.isEmpty()) true else {
                        list.categories.any { cat ->
                            var current: CategoryEntity? = cat
                            var matched = false
                            while (current != null) {
                                if (current.id in filters.selectedCategoryIds) {
                                    matched = true
                                    break
                                }
                                current = current.parentId?.let { categoryMap[it] }
                            }
                            matched
                        }
                    }
                    
                    val matchesRecurring = filters.filterRecurring == null || list.isRecurring == filters.filterRecurring

                    matchesQuery && matchesCategories && matchesRecurring
                }

                val displayItems = buildDisplayItems(filteredLists, expandedYears, expandedMonths, filters.isSortAscending)

                _uiState.update { state ->
                    val updatedReviewList = state.selectedReviewListId?.let { id ->
                        shoppingLists.find { it.id == id }
                    }
                    state.copy(
                        shoppingLists = shoppingLists,
                        displayItems = displayItems,
                        expandedYears = expandedYears,
                        expandedMonths = expandedMonths,
                        expandedCards = expandedCards,
                        selectedReviewList = updatedReviewList ?: state.selectedReviewList,
                        isSortAscending = filters.isSortAscending,
                        filterQuery = filters.filterQuery,
                        selectedCategoryIds = filters.selectedCategoryIds,
                        filterRecurring = filters.filterRecurring,
                        showFilterPanel = filters.showFilterPanel,
                        showSearchPanel = filters.showSearchPanel,
                    )
                }
            }.collect { }
        }

        viewModelScope.launch {
            repository.allStores.collect { stores ->
                _dialogState.update { it.copy(stores = stores) }
            }
        }

        viewModelScope.launch {
            categoryRepository.allCategories.collect { categories ->
                val sortedCategories = categories.sortedWith(
                    compareBy<CategoryEntity> { it.parentId != null }.thenBy { it.name }
                )
                _dialogState.update { it.copy(categories = sortedCategories) }
            }
        }

        viewModelScope.launch {
            productRepository.getProducts().collect { products ->
                _dialogState.update { it.copy(products = products) }
            }
        }

        viewModelScope.launch {
            productRepository.getAllAliases().collect { aliases ->
                _dialogState.update { it.copy(aliases = aliases) }
            }
        }
    }

    private data class FilterState(
        val isSortAscending: Boolean,
        val filterQuery: String,
        val selectedCategoryIds: Set<Long>,
        val filterRecurring: Boolean?,
        val showFilterPanel: Boolean,
        val showSearchPanel: Boolean,
    )

    private fun buildDisplayItems(
        shoppingLists: List<ShoppingList>,
        expandedYears: Set<Int>,
        expandedMonths: Set<String>,
        isSortAscending: Boolean,
    ): List<ShoppingListItem> {
        val yearMonthFormatter = DateTimeFormatter.ofPattern("yyyy-MM", Locale.getDefault())
        val groupedByYear = shoppingLists.filter { it.createDate > 0 }.groupBy { list ->
            java.time.Instant.ofEpochMilli(list.createDate).atZone(java.time.ZoneId.systemDefault()).toLocalDate().year
        }
        val items = mutableListOf<ShoppingListItem>()
        groupedByYear.keys.sortedDescending().forEach { year ->
            val isYearExpanded = expandedYears.contains(year)
            val yearLists = groupedByYear[year] ?: emptyList()
            val yearTotal = yearLists.sumOf { it.actualPrice }
            items.add(ShoppingListItem.YearHeader(year, isYearExpanded, yearTotal))
            if (isYearExpanded) {
                val groupedByMonth = yearLists.groupBy { it.createDate.let { d -> java.time.Instant.ofEpochMilli(d).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(yearMonthFormatter) } }
                groupedByMonth.keys.sortedDescending().forEach { yearMonth ->
                    val isMonthExpanded = expandedMonths.contains(yearMonth)
                    val monthLists = groupedByMonth[yearMonth] ?: emptyList()
                    val monthTotal = monthLists.sumOf { it.actualPrice }
                    val monthName = java.time.YearMonth.parse(yearMonth).month.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }
                    items.add(ShoppingListItem.MonthHeader(yearMonth, monthName, isMonthExpanded, monthTotal))
                    if (isMonthExpanded) {
                        val comparator = if (isSortAscending) {
                            compareBy<ShoppingList> { it.sortDate }
                        } else {
                            compareByDescending<ShoppingList> { it.sortDate }
                        }
                        monthLists.sortedWith(comparator).forEach { items.add(ShoppingListItem.ListContent(it, yearMonth)) }
                    }
                }
            }
        }
        return items
    }

    fun toggleSortOrder() {
        _isSortAscending.update { !it }
    }

    fun updateFilterQuery(query: String) {
        _filterQuery.value = query
    }

    fun toggleCategoryFilter(categoryId: Long) {
        _selectedCategoryIds.update { 
            if (it.contains(categoryId)) it - categoryId else it + categoryId
        }
    }

    fun updateRecurringFilter(recurring: Boolean?) {
        _filterRecurring.value = recurring
    }

    fun toggleFilterPanel() {
        _showFilterPanel.update { !it }
    }

    fun toggleSearchPanel() {
        _showSearchPanel.update { !it }
    }

    fun clearFilters() {
        _filterQuery.value = ""
        _selectedCategoryIds.value = emptySet()
        _filterRecurring.value = null
    }

    fun createStore(name: String, onResult: (Long) -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val existing = repository.getStoreByName(name)
                if (existing != null) onResult(existing.id)
                else {
                    val id = generateId()
                    repository.insertStore(StoreEntity(id = id, name = name, logoPath = null), emptyList())
                    onResult(id)
                }
            }
        }
    }

    fun createShoppingList(name: String, storeId: Long, onResult: (Long) -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val id = generateId()
                repository.insertShoppingList(ShoppingListEntity(id = id, name = name, createDate = System.currentTimeMillis(), purchaseDate = null, storeId = storeId, position = repository.getMaxListPosition() + 1), emptyList())
                onResult(id)
            }
        }
    }

    fun dismissWelcomeDialog() {
        preferencesManager.setLastShownVersion(BuildConfig.VERSION_NAME)
        _uiState.update { it.copy(showWelcomeDialog = false) }
    }

    fun toggleYearExpansion(year: Int) { _expandedYears.update { if (it.contains(year)) it - year else it + year } }
    fun toggleMonthExpansion(yearMonth: String) { _expandedMonths.update { if (it.contains(yearMonth)) it - yearMonth else it + yearMonth } }
    fun toggleCardExpansion(listId: Long) { _expandedCards.update { if (it.contains(listId)) it - listId else it + listId } }
    fun toggleInStoreMode(listId: Long) { _uiState.update { s -> s.copy(inStoreListIds = if (s.inStoreListIds.contains(listId)) s.inStoreListIds - listId else s.inStoreListIds + listId) } }
    fun inStore() { _uiState.update { it.copy(showInStoreDialog = true) } }
    fun dismissInStoreDialog() { _uiState.update { it.copy(showInStoreDialog = false) } }
    fun enterStoreMode(listId: Long) {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { repository.getShoppingListById(listId) }
            if (list != null && !list.isFinished) _uiState.update { it.copy(inStoreListIds = it.inStoreListIds + listId, showInStoreDialog = false) }
        }
    }

    fun createList(name: String, categoryIds: List<Long>, storeName: String, isRecurring: Boolean = false, recurringPeriod: String = "MONTH", isForwardEmpty: Boolean = true) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val storeId = if (storeName.isNotBlank()) {
                    val ex = repository.getStoreByName(storeName)
                    if (ex != null) ex.id else { val id = generateId(); repository.insertStore(StoreEntity(id = id, name = storeName, logoPath = null), emptyList()); id }
                } else null
                repository.insertShoppingList(ShoppingListEntity(id = generateId(), name = name, createDate = System.currentTimeMillis(), purchaseDate = null, storeId = storeId, isFinished = false, finalTotal = null, position = repository.getMaxListPosition() + 1, isRecurring = isRecurring, recurringPeriod = recurringPeriod, isForwardEmpty = isForwardEmpty), categoryIds)
            }
        }
    }

    fun processPurchase(listId: Long?, listName: String?, storeName: String, price: Double, items: List<ScannedItem> = emptyList()) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.processPurchase(
                    listId = listId,
                    listName = listName,
                    storeName = storeName,
                    price = price,
                    items = items,
                    productRepository = productRepository,
                    priceRepository = priceRepository,
                    isChecked = true
                )
            }
        }
    }

    fun startReview(list: ShoppingList) {
        _uiState.update { it.copy(showReviewDialog = true, selectedReviewListId = list.id, selectedReviewList = list) }
    }

    fun stopReview() {
        _uiState.update { it.copy(showReviewDialog = false, selectedReviewListId = null, selectedReviewList = null) }
    }

    fun updateReviewedItem(item: PurchaseItem, newName: String, newPrice: Double?, newBarcode: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val ent = repository.getShoppingListItemById(item.id)
                if (ent != null) {
                    val sid = repository.getShoppingListById(ent.shoppingListId)?.storeId
                    if (newPrice != null) {
                        priceRepository.upsertPriceForProduct(ent.productId, sid, newPrice)
                    }

                    val p = productRepository.getProductById(ent.productId)
                    if (p != null) {
                        val finalBarcode = if (newBarcode.isNotBlank()) newBarcode else p.barcode
                        val status = if (finalBarcode.isNotBlank()) "barcode" else "reviewed"
                        productRepository.updateProduct(p.copy(
                            name = if (newName.isNotBlank()) newName else p.name,
                            barcode = finalBarcode,
                            status = status,
                            changedAt = System.currentTimeMillis()
                        ))
                    }
                }
            }
        }
    }

    fun mapToExistingProduct(item: PurchaseItem, existingProduct: ProductEntity, newName: String, newPrice: Double?, newBarcode: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val ent = repository.getShoppingListItemById(item.id)
                if (ent != null) {
                    val list = repository.getShoppingListById(ent.shoppingListId)
                    val sid = list?.storeId

                    // 1. Create alias for the existing product using the scanned/edited name
                    val aliasName = if (newName.isNotBlank()) newName else item.name
                    productRepository.insertAlias(ProductAliasEntity(
                        id = generateId(),
                        productId = existingProduct.id,
                        aliasName = aliasName,
                        storeId = sid
                    ))

                    // 2. Update list item to point to the existing product
                    repository.updateShoppingListItem(ent.copy(productId = existingProduct.id))

                    // 3. Delete the temporary product
                    val tempProduct = productRepository.getProductById(item.productId)
                    if (tempProduct != null && tempProduct.status == "added") {
                        ImageStorageManager.deleteImage(tempProduct.picturePath)
                        productRepository.deleteProduct(tempProduct)
                    }

                    // 4. Update price for existing product
                    val finalPrice = newPrice ?: item.price
                    if (finalPrice != null) {
                        priceRepository.upsertPriceForProduct(existingProduct.id, sid, finalPrice)
                    }

                    // 5. Update barcode if existing is blank and new is provided
                    if (existingProduct.barcode.isBlank() && newBarcode.isNotBlank()) {
                        productRepository.updateProduct(existingProduct.copy(
                            barcode = newBarcode,
                            status = "barcode",
                            changedAt = System.currentTimeMillis()
                        ))
                    }
                }
            }
        }
    }

    @Deprecated("Use processPurchase instead", ReplaceWith("processPurchase(null, null, receipt.storeName ?: \"\", receipt.totalSum ?: 0.0, receipt.items)"))
    fun processScannedReceipt(receipt: ScannedReceipt) {
        processPurchase(null, "Receipt from ${receipt.storeName ?: "Unknown"}", receipt.storeName ?: "Unknown", receipt.totalSum ?: 0.0, receipt.items)
    }

    fun startEditingItem(item: PurchaseItem) { _uiState.update { it.copy(editingItem = item) } }
    fun stopEditingItem() { _uiState.update { it.copy(editingItem = null) } }
    fun startEditingList(list: ShoppingList) { _uiState.update { it.copy(editingList = list) } }
    fun stopEditingList() { _uiState.update { it.copy(editingList = null) } }
    fun updatePurchaseItem(item: PurchaseItem, newName: String, newPrice: Double?, newImageUrl: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val ent = repository.getShoppingListItemById(item.id)
                if (ent != null) {
                    repository.updateShoppingListItem(ent.copy(price = newPrice))
                    val p = productRepository.getProductById(ent.productId)
                    if (p != null) {
                        productRepository.updateProduct(p.copy(
                            name = if (newName.isNotBlank()) newName else p.name,
                            picturePath = if (newImageUrl.isNotBlank()) newImageUrl else p.picturePath
                        ))
                    }
                }
            }
            stopEditingItem()
        }
    }

    fun updateList(list: ShoppingList, name: String, categoryIds: List<Long>, storeName: String, isRecurring: Boolean = false, recurringPeriod: String = "MONTH", isForwardEmpty: Boolean = true) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val sid = if (storeName.isNotBlank()) {
                    val existingStore = repository.getStoreByName(storeName)
                    if (existingStore != null) existingStore.id else { val id = generateId(); repository.insertStore(StoreEntity(id = id, name = storeName, logoPath = null), emptyList()); id }
                } else null
                repository.updateShoppingList(list.toEntity().copy(name = name, storeId = sid, isRecurring = isRecurring, recurringPeriod = recurringPeriod, isForwardEmpty = isForwardEmpty), categoryIds)
            }
        }
    }

    fun deleteShoppingList(list: ShoppingList) { viewModelScope.launch { withContext(Dispatchers.IO) { repository.deleteShoppingList(list.toEntity()) } } }
    fun deleteItem(item: PurchaseItem) {
        viewModelScope.launch {
            undoJob?.cancel()
            val deleted = withContext(Dispatchers.IO) { repository.deleteShoppingListItemAndReturn(item.id) }
            if (deleted != null) {
                undoableItem = deleted
                _events.send(UiEvent.ItemDeleted(item))
                undoJob = viewModelScope.launch { delay(4_000L); undoableItem = null }
            }
        }
    }
    fun undoDelete() {
        val item = undoableItem ?: return
        undoJob?.cancel(); undoableItem = null
        viewModelScope.launch { withContext(Dispatchers.IO) { repository.insertShoppingListItem(item) } }
    }
    fun reorderItems(listId: Long, items: List<PurchaseItem>) { viewModelScope.launch { withContext(Dispatchers.IO) { items.forEachIndexed { i, item -> repository.updateItemPosition(item.id, i) } } } }
    fun reorderLists(lists: List<ShoppingList>) { viewModelScope.launch { withContext(Dispatchers.IO) { lists.forEachIndexed { i, list -> repository.updateListPosition(list.id, i) } } } }
    fun toggleItemChecked(item: PurchaseItem, checked: Boolean) { viewModelScope.launch { withContext(Dispatchers.IO) { repository.updateItemChecked(item.id, checked) } } }

    private fun ShoppingListEntity.toDomain(items: List<PurchaseItem>, storeName: String?, categories: List<CategoryEntity>, position: Int): ShoppingList {
        return ShoppingList(id, name, items, isFinished, finalTotal, storeName, createDate, categories, position, storeId, purchaseDate, isRecurring, recurringPeriod, isForwardEmpty)
    }
    private fun ShoppingList.toEntity(): ShoppingListEntity {
        return ShoppingListEntity(id, title, createDate, purchaseDate, storeId, isFinished, finalTotal, position, isRecurring, recurringPeriod, isForwardEmpty)
    }
    private fun generateId(): Long = System.currentTimeMillis()
}
