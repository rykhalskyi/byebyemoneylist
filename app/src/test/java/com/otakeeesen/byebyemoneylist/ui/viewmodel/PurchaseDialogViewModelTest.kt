package com.otakeeesen.byebyemoneylist.ui.viewmodel

import com.otakeeesen.byebyemoneylist.ui.components.product.PurchaseMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PurchaseDialogViewModelTest {

    @Test
    fun `reset clears the state`() = runTest {
        val viewModel = PurchaseDialogViewModel()
        
        // Change state
        viewModel.updateListText("Some List")
        viewModel.setPurchaseMode(PurchaseMode.SCAN)
        
        // Verify state changed
        assertNotEquals(PurchaseDialogState(), viewModel.uiState.value)
        
        // Reset
        viewModel.reset()
        
        // Verify state is default
        assertEquals(PurchaseDialogState(), viewModel.uiState.value)
    }

    @Test
    fun `setSelectedList updates priceText with itemsTotal`() = runTest {
        val viewModel = PurchaseDialogViewModel()
        val item = com.otakeeesen.byebyemoneylist.data.PurchaseItem(
            id = 1, 
            productId = 1,
            name = "Test", 
            quantity = 2.0, 
            price = 10.0, 
            checked = true, 
            imageUrl = ""
        )
        val list = com.otakeeesen.byebyemoneylist.data.ShoppingList(
            id = 1,
            title = "Test List",
            items = listOf(item),
            storeId = null
        )

        viewModel.setSelectedList(list)

        assertEquals("20.00", viewModel.uiState.value.priceText)
    }

    @Test
    fun `empty list name generates store and date based name`() = runTest {
        val viewModel = PurchaseDialogViewModel()
        viewModel.updateStoreText("Walmart")
        viewModel.updatePriceText("10.00")
        viewModel.updateSelectedCategory(1L)

        viewModel.validateAndConfirm(
            unfinishedLists = emptyList(),
            stores = listOf(
                com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity(id = 1, name = "Walmart", logoPath = null)
            ),
            onConfirm = { _, _, _, _, _, _, _ -> }
        )

        val dateStr = java.text.SimpleDateFormat("dd.MM.yy", java.util.Locale.getDefault()).format(java.util.Date())
        assertEquals("Walmart $dateStr", viewModel.uiState.value.pendingListConfirm)
        assertEquals(false, viewModel.uiState.value.listError)
    }

    @Test
    fun `processScannedReceipt fuzzy matches store name`() = runTest {
        val viewModel = PurchaseDialogViewModel()
        val stores = listOf(
            com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity(id = 1, name = "REWE City", logoPath = null, receiptName = "REWE"),
            com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity(id = 2, name = "Aldi", logoPath = null)
        )
        val receipt = com.otakeeesen.byebyemoneylist.ui.components.scanner.ScannedReceipt(
            storeName = "REWE",
            items = emptyList(),
            totalSum = 5.0
        )

        viewModel.processScannedReceipt(receipt, stores)

        assertEquals("REWE City", viewModel.uiState.value.storeText)
    }

    @Test
    fun `processScannedReceipt keeps unknown store name`() = runTest {
        val viewModel = PurchaseDialogViewModel()
        val stores = listOf(
            com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity(id = 1, name = "Aldi", logoPath = null)
        )
        val receipt = com.otakeeesen.byebyemoneylist.ui.components.scanner.ScannedReceipt(
            storeName = "Tesco",
            items = emptyList(),
            totalSum = 5.0
        )

        viewModel.processScannedReceipt(receipt, stores)

        assertEquals("Tesco", viewModel.uiState.value.storeText)
    }
}
