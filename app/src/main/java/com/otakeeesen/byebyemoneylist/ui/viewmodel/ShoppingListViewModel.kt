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
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.PriceRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ProductRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import com.otakeeesen.byebyemoneylist.ui.components.ScannedReceipt
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
    val showFinishAndPayDialog: Boolean = false,
    val showInStoreDialog: Boolean = false,
    val selectedShoppingList: ShoppingList? = null,
    val editingItem: PurchaseItem? = null,
    val editingList: ShoppingList? = null,
    val showWelcomeDialog: Boolean = false,
    val hideCheckedItems: Boolean = false,
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
        viewModelScope.launch {
            repository.allShoppingLists.collect { shoppingLists ->
                val shouldShowWelcome = shoppingLists.isEmpty()
                _uiState.update { it.copy(showWelcomeDialog = shouldShowWelcome) }
            }
        }

        _uiState.update { it.copy(hideCheckedItems = preferencesManager.getHideCheckedItems()) }

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
                        val price = item.itemPrice
                            ?: withContext(Dispatchers.IO) {
                                priceRepository.getLatestPrice(item.productId, entity.storeId)?.value
                            }
                            ?: item.price ?: 0.0

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
                        monthLists.sortedWith(compareByDescending<ShoppingList> { it.isFinished }.thenBy { it.position }.thenByDescending { it.createDate }).forEach { items.add(ShoppingListItem.ListContent(it, yearMonth)) }
                    }
                }
            }
        }
        return items
    }

    fun resetSorting() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val allLists = repository.getAllShoppingListsOnce()
                allLists.forEachIndexed { index, list -> repository.updateListPosition(list.id, allLists.size - 1 - index) }
            }
        }
    }

    fun createStore(name: String, onResult: (Long) -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val existing = repository.getStoreByName(name)
                if (existing != null) onResult(existing.id)
                else {
                    val id = generateId()
                    repository.insertStore(StoreEntity(id = id, name = name, logoPath = null, category = ""))
                    onResult(id)
                }
            }
        }
    }

    fun createShoppingList(name: String, storeId: Long, onResult: (Long) -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val id = generateId()
                repository.insertShoppingList(ShoppingListEntity(id = id, name = name, createDate = System.currentTimeMillis(), purchaseDate = null, storeId = storeId, position = repository.getMaxListPosition() + 1))
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

    fun createList(name: String, categoryName: String, storeName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val categoryId = if (categoryName.isNotBlank()) categoryRepository.getOrCreate(categoryName) else null
                val storeId = if (storeName.isNotBlank()) {
                    val ex = repository.getStoreByName(storeName)
                    if (ex != null) ex.id else { val id = generateId(); repository.insertStore(StoreEntity(id = id, name = storeName, logoPath = null, category = "")); id }
                } else null
                repository.insertShoppingList(ShoppingListEntity(id = generateId(), name = name, createDate = System.currentTimeMillis(), purchaseDate = null, storeId = storeId, categoryId = categoryId, isFinished = false, finalTotal = null, position = repository.getMaxListPosition() + 1))
            }
        }
    }

    fun processDirectPurchase(listId: Long?, listName: String?, storeName: String, price: Double) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val targetListId = listId ?: if (!listName.isNullOrBlank()) {
                    val sid = if (storeName.isNotBlank()) {
                        val ex = repository.getStoreByName(storeName)
                        if (ex != null) ex.id else { val id = generateId(); repository.insertStore(StoreEntity(id = id, name = storeName, logoPath = null, category = "")); id }
                    } else null
                    val nid = generateId()
                    repository.insertShoppingList(ShoppingListEntity(id = nid, name = listName, createDate = System.currentTimeMillis(), purchaseDate = System.currentTimeMillis(), storeId = sid, categoryId = null, isFinished = true, finalTotal = price))
                    nid
                } else null
                if (targetListId != null) repository.insertShoppingListItem(ShoppingListItemEntity(id = generateId(), shoppingListId = targetListId, productId = 0L, quantity = 1, isChecked = true, position = 0))
            }
        }
    }

    fun processScannedReceipt(receipt: ScannedReceipt) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val sName = receipt.storeName ?: "Unknown Store"
                val sid = if (sName.isNotBlank()) {
                    val ex = repository.getStoreByName(sName)
                    if (ex != null) ex.id else { val id = generateId(); repository.insertStore(StoreEntity(id = id, name = sName, logoPath = null, category = "")); id }
                } else null
                val lid = generateId()
                repository.insertShoppingList(ShoppingListEntity(id = lid, name = "Receipt from $sName", createDate = System.currentTimeMillis(), purchaseDate = System.currentTimeMillis(), storeId = sid, categoryId = null, isFinished = true, finalTotal = receipt.totalSum ?: 0.0))
                receipt.items.forEachIndexed { i, item ->
                    val pid = if (item.name.isNotBlank()) {
                        val id = generateId() + i
                        productRepository.insertProduct(ProductEntity(id = id, name = item.name, barcode = "", picturePath = null, category = "General"))
                        id
                    } else 0L
                    repository.insertShoppingListItem(ShoppingListItemEntity(id = generateId() + i + 1000, shoppingListId = lid, productId = pid, quantity = item.quantity.toInt(), isChecked = true, price = item.price, position = i))
                }
            }
        }
    }

    fun finishAndPay(shoppingList: ShoppingList) { _uiState.update { it.copy(showFinishAndPayDialog = true, selectedShoppingList = shoppingList) } }
    fun onFinishAndPayConfirm(total: Double) {
        val list = _uiState.value.selectedShoppingList ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.updateShoppingList(list.toEntity().copy(isFinished = true, finalTotal = total, purchaseDate = System.currentTimeMillis())) }
            _uiState.update { it.copy(showFinishAndPayDialog = false, selectedShoppingList = null) }
        }
    }
    fun dismissFinishAndPayDialog() { _uiState.update { it.copy(showFinishAndPayDialog = false, selectedShoppingList = null) } }
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
                    if (p != null) productRepository.updateProduct(p.copy(name = newName, picturePath = newImageUrl.ifBlank { null }))
                }
            }
            stopEditingItem()
        }
    }

    fun updateList(list: ShoppingList, name: String, categoryName: String, storeName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val cid = if (categoryName.isNotBlank()) categoryRepository.getOrCreate(categoryName) else null
                val sid = if (storeName.isNotBlank()) {
                    val ex = repository.getStoreByName(storeName)
                    if (ex != null) ex.id else { val id = generateId(); repository.insertStore(StoreEntity(id = id, name = storeName, logoPath = null, category = "")); id }
                } else null
                repository.updateShoppingList(list.toEntity().copy(name = name, categoryId = cid, storeId = sid))
            }
            stopEditingList()
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

    private fun ShoppingListEntity.toDomain(items: List<PurchaseItem>, storeName: String?, categoryName: String?, categoryColor: String?, position: Int): ShoppingList {
        return ShoppingList(id, name, items, isFinished, finalTotal, storeName, createDate, categoryName, categoryColor, position, storeId, categoryId, purchaseDate)
    }
    private fun ShoppingList.toEntity(): ShoppingListEntity {
        return ShoppingListEntity(id, title, createDate, purchaseDate, storeId, categoryId, isFinished, finalTotal, position)
    }
    private fun generateId(): Long = System.currentTimeMillis()
}
