package com.otakeeesen.byebyemoneylist.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.otakeeesen.byebyemoneylist.data.PurchaseItem
import com.otakeeesen.byebyemoneylist.data.ShoppingList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ShoppingListUiState(
    val shoppingLists: List<ShoppingList> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class ShoppingListViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ShoppingListUiState())
    val uiState: StateFlow<ShoppingListUiState> = _uiState.asStateFlow()

    init {
        loadMockData()
    }

    private fun loadMockData() {
        _uiState.value = ShoppingListUiState(
            shoppingLists = getMockShoppingLists(),
            isLoading = false
        )
    }

    fun deleteShoppingList(shoppingList: ShoppingList) {
        viewModelScope.launch {
            val currentLists = _uiState.value.shoppingLists.toMutableList()
            currentLists.remove(shoppingList)
            _uiState.value = _uiState.value.copy(shoppingLists = currentLists)
        }
    }

    fun toggleItemChecked(purchaseItem: PurchaseItem, isChecked: Boolean) {
        viewModelScope.launch {
            val currentLists = _uiState.value.shoppingLists.map { list ->
                if (list.id == getCurrentListForItem(purchaseItem)?.id) {
                    list.copy(
                        items = list.items.map { item ->
                            if (item.id == purchaseItem.id) {
                                item.copy(checked = isChecked)
                            } else {
                                item
                            }
                        }
                    )
                } else {
                    list
                }
            }
            _uiState.value = _uiState.value.copy(shoppingLists = currentLists)
        }
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