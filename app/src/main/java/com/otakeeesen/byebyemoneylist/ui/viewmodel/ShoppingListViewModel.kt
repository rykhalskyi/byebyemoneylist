package com.otakeeesen.byebyemoneylist.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.data.PurchaseItem
import com.otakeeesen.byebyemoneylist.data.ShoppingList
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShoppingListUiState(
    val shoppingLists: List<ShoppingList> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class ShoppingListViewModel(
    private val repository: ShoppingListRepository,
) : ViewModel() {

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras,
            ): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as ByeByeMoneyApplication
                return ShoppingListViewModel(application.shoppingListRepository) as T
            }
        }
    }

    private val _uiState = MutableStateFlow(ShoppingListUiState())
    val uiState: StateFlow<ShoppingListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.allShoppingLists.collect { entities ->
                _uiState.update { state ->
                    state.copy(
                        shoppingLists = entities.map { it.toDomain(emptyList()) },
                    )
                }
            }
        }
    }

    private fun ShoppingListEntity.toDomain(items: List<PurchaseItem>): ShoppingList {
        return ShoppingList(
            id = id,
            title = name,
            items = items,
            isFinished = isFinished,
            finalTotal = finalTotal,
            storeName = null, // Will be resolved from storeId in future
            createDate = createDate,
        )
    }

    fun createList() {
        // Stub: will be implemented in a separate ticket
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
            repository.deleteShoppingList(shoppingList.toEntity())
        }
    }

    private fun ShoppingList.toEntity(): ShoppingListEntity {
        return ShoppingListEntity(
            id = id,
            name = title,
            createDate = createDate,
            purchaseDate = null,
            storeId = null,
            isFinished = isFinished,
            finalTotal = finalTotal,
        )
    }

    fun toggleItemChecked(purchaseItem: PurchaseItem, isChecked: Boolean) {
        // Stub: will need actual item persistence logic
    }
}
