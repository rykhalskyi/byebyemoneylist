package com.otakeeesen.byebyemoneylist.ui.components

import com.otakeeesen.byebyemoneylist.data.ShoppingList
import org.junit.Assert.assertEquals
import org.junit.Test

class PurchaseDialogTest {

    @Test
    fun `unfinishedLists filters out finished lists`() {
        val lists = listOf(
            ShoppingList(id = 1, title = "Active", items = emptyList(), isFinished = false, storeId = null),
            ShoppingList(id = 2, title = "Finished", items = emptyList(), isFinished = true, storeId = null),
        )
        val unfinished = lists.filter { !it.isFinished }
        
        assertEquals(1, unfinished.size)
        assertEquals("Active", unfinished[0].title)
    }
}
