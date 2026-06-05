package com.otakeeesen.byebyemoneylist.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.otakeeesen.byebyemoneylist.data.ShoppingList
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.ui.components.product.PurchaseMode
import com.otakeeesen.byebyemoneylist.ui.components.scanner.ScannedItem
import com.otakeeesen.byebyemoneylist.ui.components.scanner.ScannedReceipt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PurchaseDialogState(
    val listText: String = "",
    val selectedListId: Long? = null,
    val storeText: String = "",
    val priceText: String = "",
    val listError: Boolean = false,
    val storeError: Boolean = false,
    val priceError: Boolean = false,
    val purchaseMode: PurchaseMode = PurchaseMode.MANUAL,
    val pendingListConfirm: String? = null,
    val pendingStoreConfirm: String? = null,
    val scannedReceipt: ScannedReceipt? = null,
    val itemsExpanded: Boolean = false,
    val pendingConfirmData: Pair<String, String>? = null
)

class PurchaseDialogViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PurchaseDialogState())
    val uiState: StateFlow<PurchaseDialogState> = _uiState.asStateFlow()

    fun reset() {
        _uiState.value = PurchaseDialogState()
    }

    fun updateListText(text: String) {
        _uiState.update { it.copy(listText = text, selectedListId = null, listError = false) }
    }

    fun setSelectedListId(id: Long?) {
        _uiState.update { it.copy(selectedListId = id) }
    }

    fun updateStoreText(text: String) {
        _uiState.update { it.copy(storeText = text, storeError = false) }
    }

    fun updatePriceText(text: String) {
        _uiState.update { it.copy(priceText = text, priceError = false) }
    }

    fun setPurchaseMode(mode: PurchaseMode) {
        _uiState.update { it.copy(purchaseMode = mode) }
    }

    fun setScannedReceipt(receipt: ScannedReceipt?) {
        _uiState.update { it.copy(scannedReceipt = receipt) }
    }

    fun toggleItemsExpanded() {
        _uiState.update { it.copy(itemsExpanded = !it.itemsExpanded) }
    }

    fun setPendingListConfirm(name: String?) {
        _uiState.update { it.copy(pendingListConfirm = name) }
    }

    fun setPendingStoreConfirm(name: String?) {
        _uiState.update { it.copy(pendingStoreConfirm = name) }
    }

    fun processScannedReceipt(receipt: ScannedReceipt, stores: List<StoreEntity>) {
        val dateStr = SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date())
        
        _uiState.update { currentState ->
            var newPriceText = currentState.priceText
            var newPriceError = currentState.priceError
            receipt.totalSum?.let { 
                newPriceText = String.format("%.2f", it)
                newPriceError = false
            }

            var newStoreText = currentState.storeText
            var newStoreError = currentState.storeError
            if (newStoreText.isBlank()) {
                val matchedStore = stores.find { 
                    it.name.equals(receipt.storeName, ignoreCase = true) && 
                    (receipt.storeAddress == null || it.address.equals(receipt.storeAddress, ignoreCase = true))
                }
                newStoreText = matchedStore?.name ?: receipt.storeName ?: ""
                if (newStoreText.isNotBlank()) newStoreError = false
            }

            var newListText = currentState.listText
            var newSelectedListId = currentState.selectedListId
            var newListError = currentState.listError
            if (newListText.isBlank()) {
                val suggestedStore = newStoreText.ifBlank { "Store" }
                newListText = "$suggestedStore $dateStr"
                newSelectedListId = null
                newListError = false
            }

            currentState.copy(
                priceText = newPriceText,
                priceError = newPriceError,
                storeText = newStoreText,
                storeError = newStoreError,
                listText = newListText,
                selectedListId = newSelectedListId,
                listError = newListError,
                scannedReceipt = receipt,
                itemsExpanded = receipt.items.isNotEmpty()
            )
        }
    }

    fun validateAndConfirm(
        unfinishedLists: List<ShoppingList>, 
        stores: List<StoreEntity>, 
        onConfirm: (listId: Long?, listName: String, storeName: String, price: Double, items: List<ScannedItem>) -> Unit
    ) {
        val currentState = _uiState.value
        val trimmedList = currentState.listText.trim()
        val trimmedStore = currentState.storeText.trim()
        val trimmedPrice = currentState.priceText.trim().replace(',', '.')
        val priceDouble = trimmedPrice.toDoubleOrNull()

        val listError = trimmedList.isEmpty()
        val storeError = trimmedStore.isEmpty()
        val priceError = trimmedPrice.isEmpty() || priceDouble == null

        if (listError || storeError || priceError) {
            _uiState.update { it.copy(listError = listError, storeError = storeError, priceError = priceError) }
            return
        }

        val listExists = unfinishedLists.any { it.title.equals(trimmedList, ignoreCase = true) }
        val storeExists = stores.any { it.name.equals(trimmedStore, ignoreCase = true) }

        if (!listExists && currentState.selectedListId == null) {
            _uiState.update { it.copy(pendingListConfirm = trimmedList, pendingConfirmData = Pair(trimmedList, trimmedStore)) }
            return
        }

        if (!storeExists) {
            _uiState.update { it.copy(pendingStoreConfirm = trimmedStore, pendingConfirmData = Pair(trimmedList, trimmedStore)) }
            return
        }

        onConfirm(currentState.selectedListId, trimmedList, trimmedStore, priceDouble!!, currentState.scannedReceipt?.items ?: emptyList())
    }
    
    // ... existing setters
}
