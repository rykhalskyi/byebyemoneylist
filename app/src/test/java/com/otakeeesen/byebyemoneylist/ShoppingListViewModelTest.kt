package com.otakeeesen.byebyemoneylist

import com.otakeeesen.byebyemoneylist.data.ShoppingList
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.ui.viewmodel.ShoppingListViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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

    @Test
    fun testFilteringByTitle() {
        val list1 = ShoppingList(id = 1, title = "Apple", items = emptyList(), createDate = 1000, storeId = null)
        val list2 = ShoppingList(id = 2, title = "Banana", items = emptyList(), createDate = 2000, storeId = null)
        val lists = listOf(list1, list2)

        val query = "app"
        val filtered = lists.filter { it.title.contains(query, ignoreCase = true) }
        
        assertEquals(1, filtered.size)
        assertEquals("Apple", filtered[0].title)
    }

    @Test
    fun testFilteringByStore() {
        val list1 = ShoppingList(id = 1, title = "List 1", items = emptyList(), createDate = 1000, storeName = "Lidl", storeId = 1L)
        val list2 = ShoppingList(id = 2, title = "List 2", items = emptyList(), createDate = 2000, storeName = "Aldi", storeId = 2L)
        val lists = listOf(list1, list2)

        val query = "lidl"
        val filtered = lists.filter { it.storeName?.contains(query, ignoreCase = true) == true }
        
        assertEquals(1, filtered.size)
        assertEquals("Lidl", filtered[0].storeName)
    }

    @Test
    fun testFilteringByRecurringStatus() {
        val list1 = ShoppingList(id = 1, title = "List 1", items = emptyList(), createDate = 1000, isRecurring = true, storeId = null)
        val list2 = ShoppingList(id = 2, title = "List 2", items = emptyList(), createDate = 2000, isRecurring = false, storeId = null)
        val lists = listOf(list1, list2)

        val recurringOnly = lists.filter { it.isRecurring }
        assertEquals(1, recurringOnly.size)
        assertTrue(recurringOnly[0].isRecurring)

        val regularOnly = lists.filter { !it.isRecurring }
        assertEquals(1, regularOnly.size)
        assertTrue(!regularOnly[0].isRecurring)
    }

    @Test
    fun testFilteringByFavorites() {
        val favItem = com.otakeeesen.byebyemoneylist.data.PurchaseItem(
            id = 1, productId = 1, name = "Fav", price = 1.0, quantity = 1.0, imageUrl = "", checked = false, isFavorite = true
        )
        val regularItem = com.otakeeesen.byebyemoneylist.data.PurchaseItem(
            id = 2, productId = 2, name = "Regular", price = 1.0, quantity = 1.0, imageUrl = "", checked = false, isFavorite = false
        )
        
        val list1 = ShoppingList(id = 1, title = "List 1", items = listOf(favItem), createDate = 1000, storeId = null)
        val list2 = ShoppingList(id = 2, title = "List 2", items = listOf(regularItem), createDate = 2000, storeId = null)
        val lists = listOf(list1, list2)

        val favoritesOnly = lists.filter { list -> list.items.any { it.isFavorite } }
        assertEquals(1, favoritesOnly.size)
        assertEquals("List 1", favoritesOnly[0].title)
    }

    @Test
    fun testIncomeListsAlwaysIncluded() {
        val incomeList = ShoppingList(id = 1, title = "Income", items = emptyList(), createDate = 1000, isIncome = true, isFinished = false, storeId = null)
        val expenseListNew = ShoppingList(id = 2, title = "Expense New", items = emptyList(), createDate = 2000, isIncome = false, isFinished = false, storeId = null)
        val expenseListFinished = ShoppingList(id = 3, title = "Expense Finished", items = emptyList(), createDate = 3000, isIncome = false, isFinished = true, storeId = null)
        
        val lists = listOf(incomeList, expenseListNew, expenseListFinished)
        
        // Simulating the filter: status = FINISHED
        val filterStatus = ShoppingListViewModel.ListStatusFilter.FINISHED
        
        val filtered = lists.filter { list ->
            val matchesStatus = if (list.isIncome) true else when (filterStatus) {
                ShoppingListViewModel.ListStatusFilter.FINISHED -> list.isFinished && !list.isArchived
                else -> false
            }
            matchesStatus
        }
        
        // Income list (id=1) should be included even if it's not finished.
        // Finished expense list (id=3) should be included.
        // New expense list (id=2) should NOT be included.
        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.id == 1L })
        assertTrue(filtered.any { it.id == 3L })
    }
}
