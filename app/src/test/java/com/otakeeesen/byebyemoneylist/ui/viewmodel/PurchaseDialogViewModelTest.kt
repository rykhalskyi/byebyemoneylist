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
}
