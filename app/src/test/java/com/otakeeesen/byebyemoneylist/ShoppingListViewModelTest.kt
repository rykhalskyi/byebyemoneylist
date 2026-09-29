package com.otakeeesen.byebyemoneylist

import com.otakeeesen.byebyemoneylist.data.ListKind
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
    fun testStatusFilterDistinguishesListsByKind() {
        val incomeList = ShoppingList(id = 1, title = "Income", items = emptyList(), createDate = 1000, isIncome = true, isFinished = true, storeId = null, kind = ListKind.INCOME)
        val toBuyList = ShoppingList(id = 2, title = "To Buy", items = emptyList(), createDate = 2000, storeId = null, kind = ListKind.NEED_TO_BUY)
        val purchaseList = ShoppingList(id = 3, title = "Purchase", items = emptyList(), createDate = 3000, isFinished = true, storeId = null, kind = ListKind.PURCHASE)

        val lists = listOf(incomeList, toBuyList, purchaseList)

        fun filterBy(status: ShoppingListViewModel.ListStatusFilter): List<ShoppingList> = lists.filter { list ->
            when (status) {
                ShoppingListViewModel.ListStatusFilter.ALL -> true
                ShoppingListViewModel.ListStatusFilter.TO_BUY -> list.kind == ListKind.NEED_TO_BUY
                ShoppingListViewModel.ListStatusFilter.PURCHASES -> list.kind == ListKind.PURCHASE
                ShoppingListViewModel.ListStatusFilter.INCOME -> list.kind == ListKind.INCOME
                ShoppingListViewModel.ListStatusFilter.SUBSCRIPTIONS -> list.kind == ListKind.SUBSCRIPTION
            }
        }

        assertEquals(listOf(3L), filterBy(ShoppingListViewModel.ListStatusFilter.PURCHASES).map { it.id })
        assertEquals(listOf(1L), filterBy(ShoppingListViewModel.ListStatusFilter.INCOME).map { it.id })
        assertEquals(listOf(2L), filterBy(ShoppingListViewModel.ListStatusFilter.TO_BUY).map { it.id })
        assertEquals(3, filterBy(ShoppingListViewModel.ListStatusFilter.ALL).size)
    }
}
