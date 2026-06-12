package com.otakeeesen.byebyemoneylist.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.data.ShoppingList
import com.otakeeesen.byebyemoneylist.data.PurchaseItem
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import com.otakeeesen.byebyemoneylist.util.CsvExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExportViewModel(
    private val shoppingListRepository: ShoppingListRepository,
    private val categoryRepository: CategoryRepository,
    private val preferencesManager: PreferencesManager,
) : ViewModel() {

    fun exportData(
        context: android.content.Context,
        uri: android.net.Uri,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val csvContent = withContext(Dispatchers.IO) {
                    val entities = shoppingListRepository.getAllShoppingListsOnce()
                    val itemsWithProduct = shoppingListRepository.getAllItemsWithProduct().first()
                    val categoryCrossRefs = shoppingListRepository.getAllShoppingListCategoryCrossRefs().first()
                    
                    val storeList = shoppingListRepository.getAllStoresOnce()
                    val categoryList = categoryRepository.getAllCategoriesOnce()
                    
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
                                quantity = item.quantity,
                                imageUrl = item.productPicturePath ?: "",
                                checked = item.isChecked,
                                position = item.position,
                                productStatus = item.productStatus,
                                isSubscription = item.productIsSubscription,
                                discount = item.discount,
                                customName = item.customName,
                                categoryId = item.productCategoryId
                            )
                        } ?: emptyList()).sortedBy { it.position }

                        ShoppingList(
                            id = entity.id,
                            title = entity.name,
                            items = items,
                            isFinished = entity.isFinished,
                            finalTotal = entity.finalTotal,
                            storeName = store?.name,
                            createDate = entity.createDate,
                            categories = listCategories,
                            position = entity.position,
                            storeId = entity.storeId,
                            purchaseDate = entity.purchaseDate,
                            isRecurring = entity.isRecurring,
                            recurringPeriod = entity.recurringPeriod,
                            isForwardEmpty = entity.isForwardEmpty,
                            isArchived = entity.isArchived,
                            isSubscription = entity.isSubscription
                        )
                    }

                    val currencySymbol = preferencesManager.getCurrencySymbol() ?: ""
                    CsvExporter.exportToCsv(shoppingLists, categoryList, currencySymbol)
                }

                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(csvContent.toByteArray(Charsets.UTF_8))
                    } ?: throw java.io.IOException("Failed to open output stream")
                }
                
                onSuccess()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras,
            ): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as ByeByeMoneyApplication
                return ExportViewModel(
                    application.shoppingListRepository,
                    application.categoryRepository,
                    application.preferencesManager,
                ) as T
            }
        }
    }
}
