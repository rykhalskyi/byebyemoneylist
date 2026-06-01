package com.otakeeesen.byebyemoneylist

import com.otakeeesen.byebyemoneylist.data.ShoppingList
import com.otakeeesen.byebyemoneylist.ui.viewmodel.ShoppingListUiState
import com.otakeeesen.byebyemoneylist.ui.viewmodel.ShoppingListItem
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ShoppingListViewModelTest {

    @Test
    fun testSortingLogic() {
        val now = System.currentTimeMillis()
        val list1 = ShoppingList(id = 1, title = "A", items = emptyList(), createDate = now - 1000, storeId = null)
        val list2 = ShoppingList(id = 2, title = "B", items = emptyList(), createDate = now - 2000, purchaseDate = now - 500, storeId = null)
        
        val lists = listOf(list1, list2)
        
        // Ascending
        val ascComparator = compareBy<ShoppingList> { it.sortDate }
        val sortedAsc = lists.sortedWith(ascComparator)
        assertTrue(sortedAsc[0].id == 1L) // sortDate = now-1000
        assertTrue(sortedAsc[1].id == 2L) // sortDate = now-500

        // Descending
        val descComparator = compareByDescending<ShoppingList> { it.sortDate }
        val sortedDesc = lists.sortedWith(descComparator)
        assertTrue(sortedDesc[0].id == 2L)
        assertTrue(sortedDesc[1].id == 1L)
    }
}
