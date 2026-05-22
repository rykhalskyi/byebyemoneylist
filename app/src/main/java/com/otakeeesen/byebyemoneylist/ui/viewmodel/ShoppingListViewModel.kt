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
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
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
import java.text.SimpleDateFormat
import java.util.*

data class ShoppingListUiState(
    val shoppingLists: List<ShoppingList> = emptyList(),
    val displayItems: List<ShoppingListItem> = emptyList(),
    val expandedYears: Set<Int> = emptySet(),
    val expandedMonths: Set<String> = emptySet(),
    val expandedCards: Set<Long> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

sealed class ShoppingListItem {
    data class YearHeader(val year: Int, val isExpanded: Boolean) : ShoppingListItem()
    data class MonthHeader(val yearMonth: String, val monthName: String, val isExpanded: Boolean) : ShoppingListItem()
    data class ListContent(val shoppingList: ShoppingList) : ShoppingListItem()
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

    private val _expandedYears = MutableStateFlow<Set<Int>>(emptySet())
    private val _expandedMonths = MutableStateFlow<Set<String>>(emptySet())
    private val _expandedCards = MutableStateFlow<Set<Long>>(emptySet())

    private var undoableItem: ShoppingListItemEntity? = null
    private var undoJob: Job? = null

    init {
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
                val storeMap = storeList.associate { it.id to it.name }
                val categoryMap = categoryList.associate { it.id to it.name }
                val categoryColorMap = categoryList.associate { it.id to it.color }
                val itemsByListId = itemsWithProduct.groupBy { it.shoppingListId }

                val shoppingLists = entities.map { entity ->
                    val items = (itemsByListId[entity.id]?.map { item ->
                        PurchaseItem(
                            id = item.id,
                            name = item.productName ?: "Unknown",
                            price = item.price,
                            imageUrl = item.productPicturePath ?: "",
                            checked = item.isChecked,
                            position = item.position,
                        )
                    } ?: emptyList()).sortedBy { it.position }

                    entity.toDomain(
                        items = items,
                        storeName = storeMap[entity.storeId],
                        categoryName = categoryMap[entity.categoryId],
                        categoryColor = categoryColorMap[entity.categoryId],
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
        val dateFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val monthFormat = SimpleDateFormat("MMMM", Locale.getDefault())

        // Filter out lists with invalid dates
        val validShoppingLists = shoppingLists.filter { it.createDate > 0 }

        val groupedByYear = validShoppingLists.groupBy { list ->
            val cal = Calendar.getInstance().apply { timeInMillis = list.createDate }
            cal.get(Calendar.YEAR)
        }

        val items = mutableListOf<ShoppingListItem>()

        groupedByYear.keys.sortedDescending().forEach { year ->
            val isYearExpanded = expandedYears.contains(year)
            items.add(ShoppingListItem.YearHeader(year, isYearExpanded))

            if (isYearExpanded) {
                val yearLists = groupedByYear[year] ?: emptyList()
                val groupedByMonth = yearLists.groupBy { list ->
                    dateFormat.format(Date(list.createDate))
                }

                groupedByMonth.keys.sortedDescending().forEach { yearMonth ->
                    val isMonthExpanded = expandedMonths.contains(yearMonth)
                    val date = dateFormat.parse(yearMonth) ?: return@forEach
                    val monthName = monthFormat.format(date)
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

                    items.add(ShoppingListItem.MonthHeader(yearMonth, monthName, isMonthExpanded))

                    if (isMonthExpanded) {
                        groupedByMonth[yearMonth]?.forEach { shoppingList ->
                            items.add(ShoppingListItem.ListContent(shoppingList))
                        }
                    }
                }
            }
        }

        return items
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

                val list = ShoppingListEntity(
                    id = generateId(),
                    name = name,
                    createDate = System.currentTimeMillis(),
                    purchaseDate = null,
                    storeId = storeId,
                    categoryId = categoryId,
                    isFinished = false,
                    finalTotal = null,
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
        // Stub: will be implemented in a separate ticket
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
        )
    }

    private fun ShoppingList.toEntity(): ShoppingListEntity {
        return ShoppingListEntity(
            id = id,
            name = title,
            createDate = createDate,
            purchaseDate = null,
            storeId = null,
            categoryId = null,
            isFinished = isFinished,
            finalTotal = finalTotal,
        )
    }

    private fun generateId(): Long = System.currentTimeMillis()
}
