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
}
