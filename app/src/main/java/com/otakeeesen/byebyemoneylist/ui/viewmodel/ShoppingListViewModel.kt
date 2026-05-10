package com.otakeeesen.byebyemoneylist.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.data.PurchaseItem
import com.otakeeesen.byebyemoneylist.data.ShoppingList
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras

data class ShoppingListUiState(
    val shoppingLists: List<ShoppingList> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class ShoppingListViewModel(
    private val repository: ShoppingListRepository
) : ViewModel() {

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
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
                // Here we would normally map entities to domain models
                // For now, keeping it simple as we refine the UI
                _uiState.value = _uiState.value.copy(
                    shoppingLists = entities.map { it.toDomain(emptyList()) }
                )
            }
        }
    }

    private fun ShoppingListEntity.toDomain(items: List<PurchaseItem>): ShoppingList {
        return ShoppingList(
            id = id,
            title = name,
            items = items,
            isFinished = isFinished,
            finalTotal = finalTotal
        )
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
            createDate = 0, // Should be managed properly
            purchaseDate = null,
            storeId = null,
            isFinished = isFinished,
            finalTotal = finalTotal
        )
    }

    fun toggleItemChecked(purchaseItem: PurchaseItem, isChecked: Boolean) {
        // This will need actual item persistence logic
    }

    private fun getCurrentListForItem(purchaseItem: PurchaseItem): ShoppingList? {
        return _uiState.value.shoppingLists.find { list ->
            list.items.any { it.id == purchaseItem.id }
        }
    }

    private fun getMockShoppingLists(): List<ShoppingList> {
        return listOf(
            ShoppingList(
                id = 1,
                title = "Weekly Groceries",
                items = listOf(
                    PurchaseItem(1, "Milk", 1.99, "https://example.com/milk.jpg", true),
                    PurchaseItem(2, "Bread", 2.49, "https://example.com/bread.jpg", false)
                )
            ),
            ShoppingList(
                id = 2,
                title = "Household Items",
                items = listOf(
                    PurchaseItem(3, "Laundry Detergent", 12.99, "https://example.com/laundry.jpg", false),
                    PurchaseItem(4, "Paper Towels", 8.99, "https://example.com/paper-towels.jpg", true)
                )
            ),
            ShoppingList(
                id = 3,
                title = "Office Supplies",
                items = listOf(
                    PurchaseItem(5, "Notebooks", 5.99, "https://example.com/notebooks.jpg", false),
                    PurchaseItem(6, "Pens", 3.49, "https://example.com/pens.jpg", true),
                    PurchaseItem(7, "Sticky Notes", 2.99, "https://example.com/sticky-notes.jpg", false)
                )
            )
        )
    }
}