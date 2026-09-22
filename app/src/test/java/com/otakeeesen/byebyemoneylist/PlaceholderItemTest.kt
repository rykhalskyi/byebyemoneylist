package com.otakeeesen.byebyemoneylist

import com.otakeeesen.byebyemoneylist.data.PurchaseItem
import com.otakeeesen.byebyemoneylist.data.ShoppingList
import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.dao.ShoppingListDao
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListItemEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PlaceholderItemTest {

    @Test
    fun `placeholder items contribute nothing to itemsTotal`() {
        val list = ShoppingList(
            id = 1,
            title = "Groceries",
            storeId = null,
            items = listOf(
                PurchaseItem(
                    id = 1, productId = 0L, name = "Milk", price = null, quantity = 2.0,
                    imageUrl = "", checked = true, isPlaceholder = true
                ),
                PurchaseItem(
                    id = 2, productId = 5L, name = "Bread", price = 2.0, quantity = 1.0,
                    imageUrl = "", checked = true
                )
            )
        )

        assertEquals(2.0, list.itemsTotal, 0.0001)
        assertEquals(2, list.checkedCount)
        assertEquals(2, list.totalCount)
        assertTrue(list.items.first().isPlaceholder)
        assertFalse(list.items.last().isPlaceholder)
    }

    @Test
    fun `addPlaceholderItem inserts a product-less placeholder with customName`() = runBlocking {
        val db = mock<AppDatabase>()
        val shoppingListDao = mock<ShoppingListDao>()
        whenever(db.shoppingListDao()).thenReturn(shoppingListDao)
        whenever(db.storeDao()).thenReturn(mock<com.otakeeesen.byebyemoneylist.data.local.dao.StoreDao>())
        whenever(shoppingListDao.getMaxPositionForList(1L)).thenReturn(4)

        val repository = ShoppingListRepository(db)

        repository.addPlaceholderItem(listId = 1L, name = "Cheese", quantity = 3.0)

        val captor = argumentCaptor<ShoppingListItemEntity>()
        verify(shoppingListDao).insertShoppingListItem(captor.capture())
        val inserted = captor.firstValue
        assertEquals(1L, inserted.shoppingListId)
        assertEquals(0L, inserted.productId)
        assertEquals("Cheese", inserted.customName)
        assertEquals(3.0, inserted.quantity, 0.0001)
        assertEquals(5, inserted.position)
        assertTrue(inserted.isPlaceholder)
    }
}
