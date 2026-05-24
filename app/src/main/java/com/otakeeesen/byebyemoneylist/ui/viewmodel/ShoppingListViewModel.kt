package com.otakeeesen.byebyemoneylist.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.data.PurchaseItem
import com.otakeeesen.byebyemoneylist.data.ShoppingList
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryColors
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListItemEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.PriceRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ProductRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
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
    val isLoading: Boolean = false,
    val error: String? = null,
    val showFinishAndPayDialog: Boolean = false,
    val selectedShoppingList: ShoppingList? = null,
    val editingItem: PurchaseItem? = null,
    val editingList: ShoppingList? = null,
    val showWelcomeDialog: Boolean = false,
)

sealed class ShoppingListItem {
    data class YearHeader(val year: Int, val isExpanded: Boolean, val totalPrice: Double) : ShoppingListItem()
    data class MonthHeader(val yearMonth: String, val monthName: String, val isExpanded: Boolean, val totalPrice: Double) : ShoppingListItem()
    data class ListContent(val shoppingList: ShoppingList, val yearMonth: String) : ShoppingListItem()
}

data class CreateListDialogState(
    val categories: List<CategoryEntity> = emptyList(),
    val stores: List<StoreEntity> = emptyList(),
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

    private var undoableItem: ShoppingListItemEntity? = null
    private var undoJob: Job? = null

    init {
        val currentVersion = "1.0.0.1-alpha"
        val lastShownVersion = preferencesManager.getLastShownWelcomeVersion()
        
        viewModelScope.launch {
            repository.allShoppingLists.collect { shoppingLists ->
                val shouldShowWelcome = lastShownVersion != currentVersion && shoppingLists.isEmpty()
                _uiState.update { it.copy(showWelcomeDialog = shouldShowWelcome) }
            }
        }

        viewModelScope.launch {
            combine(
                repository.allShoppingLists,
                repository.getAllItemsWithProduct(),
                _expandedYears,
                _expandedMonths,
                _expandedCards,
            ) { entities, itemsWithProduct, expandedYears, expandedMonths, expandedCards ->
                val storeList = withContext(Dispatchers.IO) { repository.getAllStoresOnce() }
                val categoryList = withContext(Dispatchers.IO) { categoryRepository.getAllCategoriesOnce() }
                val storeMap = storeList.associateBy { it.id }
                val categoryMap = categoryList.associateBy { it.id }
                val categoryColorMap = categoryList.associate { it.id to it.color }
                val itemsByListId = itemsWithProduct.groupBy { it.shoppingListId }

                val shoppingLists = entities.map { entity ->
                    val store = entity.storeId?.let { storeMap[it] }
                    val items = (itemsByListId[entity.id]?.map { item ->
                        // Use itemPrice if set, otherwise look up store-specific price, then global fallback
                        val price = item.itemPrice
                            ?: withContext(Dispatchers.IO) {
                                priceRepository.getLatestPrice(item.productId, entity.storeId)?.value
                            }
                            ?: item.price // global fallback from query
                            ?: 0.0

                        PurchaseItem(
                            id = item.id,
                            name = item.productName ?: "Unknown",
                            price = price,
                            imageUrl = item.productPicturePath ?: "",
                            checked = item.isChecked,
                            position = item.position,
                        )
                    } ?: emptyList()).sortedBy { it.position }

                    entity.toDomain(
                        items = items,
                        storeName = store?.name,
                        categoryName = categoryMap[entity.categoryId]?.name,
                        categoryColor = categoryColorMap[entity.categoryId],
                        position = entity.position,
                    )
                }

                val displayItems = buildDisplayItems(shoppingLists, expandedYears, expandedMonths)

                _uiState.update { state ->
                    state.copy(
                        shoppingLists = shoppingLists,
                        displayItems = displayItems,
                        expandedYears = expandedYears,
                        expandedMonths = expandedMonths,
                        expandedCards = expandedCards,
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
                _dialogState.update { it.copy(categories = categories) }
            }
        }
    }

    private fun buildDisplayItems(
        shoppingLists: List<ShoppingList>,
        expandedYears: Set<Int>,
        expandedMonths: Set<String>,
    ): List<ShoppingListItem> {
        val yearMonthFormatter = DateTimeFormatter.ofPattern("yyyy-MM", Locale.getDefault())
        val monthFormatter = DateTimeFormatter.ofPattern("LLLL", Locale.getDefault())

        // Filter out lists with invalid dates
        val validShoppingLists = shoppingLists.filter { it.createDate > 0 }

        val groupedByYear = validShoppingLists.groupBy { list ->
            java.time.Instant.ofEpochMilli(list.createDate).atZone(java.time.ZoneId.systemDefault()).toLocalDate().year
        }

        val items = mutableListOf<ShoppingListItem>()

        groupedByYear.keys.sortedDescending().forEach { year ->
            val isYearExpanded = expandedYears.contains(year)
            val yearLists = groupedByYear[year] ?: emptyList()
            val yearTotal = yearLists.sumOf { it.actualPrice }
            items.add(ShoppingListItem.YearHeader(year, isYearExpanded, yearTotal))

            if (isYearExpanded) {
                val groupedByMonth = yearLists.groupBy { list ->
                    java.time.Instant.ofEpochMilli(list.createDate).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(yearMonthFormatter)
                }

                groupedByMonth.keys.sortedDescending().forEach { yearMonth ->
                    val isMonthExpanded = expandedMonths.contains(yearMonth)
                    val monthLists = groupedByMonth[yearMonth] ?: emptyList()
                    val monthTotal = monthLists.sumOf { it.actualPrice }

                    val monthName = java.time.YearMonth.parse(yearMonth).month.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, Locale.getDefault())
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

                    items.add(ShoppingListItem.MonthHeader(yearMonth, monthName, isMonthExpanded, monthTotal))

                    if (isMonthExpanded) {
                        monthLists
                            .sortedByDescending { it.position }
                            .forEach { shoppingList ->
                                items.add(ShoppingListItem.ListContent(shoppingList, yearMonth))
                            }
                    }
                }            }
        }

        return items
    }

    fun dismissWelcomeDialog() {
        val currentVersion = "1.0.0.1-alpha"
        preferencesManager.setLastShownWelcomeVersion(currentVersion)
        _uiState.update { it.copy(showWelcomeDialog = false) }
    }

    fun toggleYearExpansion(year: Int) {
        _expandedYears.update { current ->
            if (current.contains(year)) current - year else current + year
        }
    }

    fun toggleMonthExpansion(yearMonth: String) {
        _expandedMonths.update { current ->
            if (current.contains(yearMonth)) current - yearMonth else current + yearMonth
        }
    }

    fun toggleCardExpansion(listId: Long) {
        _expandedCards.update { current ->
            if (current.contains(listId)) current - listId else current + listId
        }
    }

    fun createList(name: String, categoryName: String, storeName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val categoryId = if (categoryName.isNotBlank()) {
                    val categoryId = categoryRepository.getOrCreate(categoryName)
                    // Ensure the category has a color (default color if not set)
                    val category = categoryRepository.getAllCategoriesOnce().find { it.id == categoryId }
                    if (category != null && category.color == null) {
                        // This shouldn't happen with new categories but just in case
                        categoryRepository.updateCategory(category.copy(color = CategoryColors.DEFAULT_COLOR))
                    }
                    categoryId
                } else null

                val storeId = if (storeName.isNotBlank()) {
                    val existing = repository.getStoreByName(storeName)
                    if (existing != null) existing.id
                    else {
                        val id = generateId()
                        repository.insertStore(StoreEntity(id = id, name = storeName, logoPath = null, category = ""))
                        id
                    }
                } else null

                val nextPosition = repository.getMaxListPosition() + 1
                val list = ShoppingListEntity(
                    id = generateId(),
                    name = name,
                    createDate = System.currentTimeMillis(),
                    purchaseDate = null,
                    storeId = storeId,
                    categoryId = categoryId,
                    isFinished = false,
                    finalTotal = null,
                    position = nextPosition,
                )
                repository.insertShoppingList(list)
            }
        }
    }

    fun inStore() {
        // Stub: will be implemented in a separate ticket
    }

    fun directPurchase() {
        // Stub: will be implemented in a separate ticket
    }

    fun processDirectPurchase(listId: Long?, listName: String?, storeName: String, price: Double) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val targetListId = if (listId != null) listId
                else if (!listName.isNullOrBlank()) {
                    val newId = generateId()
                    val storeId = if (storeName.isNotBlank()) {
                        val existing = repository.getStoreByName(storeName)
                        if (existing != null) existing.id
                        else {
                            val id = generateId()
                            repository.insertStore(StoreEntity(id = id, name = storeName, logoPath = null, category = ""))
                            id
                        }
                    } else null
                    repository.insertShoppingList(ShoppingListEntity(
                        id = newId,
                        name = listName,
                        createDate = System.currentTimeMillis(),
                        purchaseDate = System.currentTimeMillis(),
                        storeId = storeId,
                        categoryId = null,
                        isFinished = true,
                        finalTotal = price
                    ))
                    newId
                } else null

                if (targetListId != null) {
                    repository.insertShoppingListItem(ShoppingListItemEntity(
                        id = generateId(),
                        shoppingListId = targetListId,
                        productId = 0L,
                        quantity = 1,
                        isChecked = true,
                        position = 0,
                    ))
                }
            }
        }
    }

    fun finishAndPay(shoppingList: ShoppingList) {
        _uiState.update { it.copy(showFinishAndPayDialog = true, selectedShoppingList = shoppingList) }
    }

    fun onFinishAndPayConfirm(total: Double) {
        val list = _uiState.value.selectedShoppingList ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.updateShoppingList(
                    list.toEntity().copy(
                        isFinished = true,
                        finalTotal = total
                    )
                )
            }
            _uiState.update { it.copy(showFinishAndPayDialog = false, selectedShoppingList = null) }
        }
    }

    fun dismissFinishAndPayDialog() {
        _uiState.update { it.copy(showFinishAndPayDialog = false, selectedShoppingList = null) }
    }

    fun startEditingItem(item: PurchaseItem) {
        _uiState.update { it.copy(editingItem = item) }
    }

    fun stopEditingItem() {
        _uiState.update { it.copy(editingItem = null) }
    }

    fun startEditingList(list: ShoppingList) {
        _uiState.update { it.copy(editingList = list) }
    }

    fun stopEditingList() {
        _uiState.update { it.copy(editingList = null) }
    }

    fun updatePurchaseItem(item: PurchaseItem, newName: String, newPrice: Double?, newImageUrl: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Update ShoppingListItemEntity (instance-specific price)
                val itemEntity = repository.getShoppingListItemById(item.id)
                if (itemEntity != null) {
                    repository.updateShoppingListItem(itemEntity.copy(price = newPrice))
                }

                // Update ProductEntity (global name/image)
                if (itemEntity != null) {
                    val product = productRepository.getProductById(itemEntity.productId)
                    if (product != null) {
                        productRepository.updateProduct(product.copy(
                            name = newName,
                            picturePath = newImageUrl.ifBlank { null }
                        ))
                    }
                }
            }
            stopEditingItem()
        }
    }

    fun updateList(list: ShoppingList, name: String, categoryName: String, storeName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val categoryId = if (categoryName.isNotBlank()) {
                    categoryRepository.getOrCreate(categoryName)
                } else null

                val storeId = if (storeName.isNotBlank()) {
                    val existing = repository.getStoreByName(storeName)
                    if (existing != null) existing.id
                    else {
                        val id = generateId()
                        repository.insertStore(StoreEntity(id = id, name = storeName, logoPath = null, category = ""))
                        id
                    }
                } else null

                repository.updateShoppingList(
                    list.toEntity().copy(
                        name = name,
                        categoryId = categoryId,
                        storeId = storeId
                    )
                )
            }
            stopEditingList()
        }
    }

    fun deleteShoppingList(shoppingList: ShoppingList) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.deleteShoppingList(shoppingList.toEntity())
            }
        }
    }

    fun deleteItem(item: PurchaseItem) {
        viewModelScope.launch {
            undoJob?.cancel()
            val deleted = withContext(Dispatchers.IO) {
                repository.deleteShoppingListItemAndReturn(item.id)
            }
            if (deleted != null) {
                undoableItem = deleted
                _events.send(UiEvent.ItemDeleted(item))
                undoJob = viewModelScope.launch {
                    delay(4_000L)
                    undoableItem = null
                }
            }
        }
    }

    fun undoDelete() {
        val item = undoableItem ?: return
        undoJob?.cancel()
        undoableItem = null
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.insertShoppingListItem(item)
            }
        }
    }

    fun reorderItems(shoppingListId: Long, items: List<PurchaseItem>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                items.forEachIndexed { index, item ->
                    repository.updateItemPosition(item.id, index)
                }
            }
        }
    }

    fun reorderLists(lists: List<ShoppingList>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                lists.forEachIndexed { index, list ->
                    repository.updateListPosition(list.id, lists.size - 1 - index)
                }
            }
        }
    }

    fun toggleItemChecked(purchaseItem: PurchaseItem, isChecked: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.updateItemChecked(purchaseItem.id, isChecked)
            }
        }
    }

    private fun ShoppingListEntity.toDomain(
        items: List<PurchaseItem>,
        storeName: String?,
        categoryName: String?,
        categoryColor: String?,
        position: Int,
    ): ShoppingList {
        return ShoppingList(
            id = id,
            title = name,
            items = items,
            isFinished = isFinished,
            finalTotal = finalTotal,
            storeName = storeName,
            createDate = createDate,
            categoryName = categoryName,
            categoryColor = categoryColor,
            position = position,
            storeId = storeId,
            categoryId = categoryId
        )
    }

    private fun ShoppingList.toEntity(): ShoppingListEntity {
        return ShoppingListEntity(
            id = id,
            name = title,
            createDate = createDate,
            purchaseDate = null,
            storeId = storeId,
            categoryId = categoryId,
            isFinished = isFinished,
            finalTotal = finalTotal,
            position = position,
        )
    }

    private fun generateId(): Long = System.currentTimeMillis()
}
