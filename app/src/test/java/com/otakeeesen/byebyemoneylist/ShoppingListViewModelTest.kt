package com.otakeeesen.byebyemoneylist

import com.otakeeesen.byebyemoneylist.data.ShoppingList
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
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
    fun testHierarchicalCategoryFiltering() {
        val parentCat = CategoryEntity(id = 1, name = "Food", color = "#FF0000")
        val childCat = CategoryEntity(id = 2, name = "Fruit", color = "#00FF00", parentId = 1)
        
        val categoryMap = mapOf(1L to parentCat, 2L to childCat)
        
        val list = ShoppingList(
            id = 1, 
            title = "Fruits", 
            items = emptyList(), 
            createDate = 1000, 
            categories = listOf(childCat),
            storeId = null
        )
        
        val selectedCategoryIds = setOf(1L) // Select parent category
        
        val matches = list.categories.any { cat ->
            var current: CategoryEntity? = cat
            var matched = false
            while (current != null) {
                if (current.id in selectedCategoryIds) {
                    matched = true
                    break
                }
                current = current.parentId?.let { categoryMap[it] }
            }
            matched
        }
        
        assertTrue("List with child category should match when parent category is selected", matches)
    }
}
