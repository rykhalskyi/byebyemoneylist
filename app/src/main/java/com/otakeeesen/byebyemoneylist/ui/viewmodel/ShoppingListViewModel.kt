package com.otakeeesen.byebyemoneylist.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.data.PurchaseItem
import com.otakeeesen.byebyemoneylist.data.ShoppingList
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ShoppingListUiState(
    val shoppingLists: List<ShoppingList> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class CreateListDialogState(
    val categories: List<CategoryEntity> = emptyList(),
    val stores: List<StoreEntity> = emptyList(),
)

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

    init {
        viewModelScope.launch {
            repository.allShoppingLists.collect { entities ->
                val storeList = withContext(Dispatchers.IO) { repository.getAllStoresOnce() }
                val categoryList = withContext(Dispatchers.IO) { categoryRepository.getAllCategoriesOnce() }
                val storeMap = storeList.associate { it.id to it.name }
                val categoryMap = categoryList.associate { it.id to it.name }

                _uiState.update { state ->
                    state.copy(
                        shoppingLists = entities.map { entity ->
                            entity.toDomain(
                                items = emptyList(),
                                storeName = storeMap[entity.storeId],
                                categoryName = categoryMap[entity.categoryId],
                            )
                        },
                    )
                }
            }
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

    fun createList(name: String, categoryName: String, storeName: String) {
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

    fun toggleItemChecked(purchaseItem: PurchaseItem, isChecked: Boolean) {
        // Stub: will need actual item persistence logic
    }

    private fun ShoppingListEntity.toDomain(
        items: List<PurchaseItem>,
        storeName: String?,
        categoryName: String?,
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
