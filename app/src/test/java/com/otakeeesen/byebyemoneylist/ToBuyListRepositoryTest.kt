package com.otakeeesen.byebyemoneylist

import com.otakeeesen.byebyemoneylist.data.ListKind
import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.dao.ShoppingListDao
import com.otakeeesen.byebyemoneylist.data.local.dao.StoreDao
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListItemEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.PriceRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ProductRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ToBuyListRepositoryTest {

    private fun repositoryWithDao(): Pair<ShoppingListRepository, ShoppingListDao> {
        val db = mock<AppDatabase>()
        val dao = mock<ShoppingListDao>()
        whenever(db.shoppingListDao()).thenReturn(dao)
        // The repository eagerly subscribes to allStores in its constructor.
        whenever(db.storeDao()).thenReturn(mock<StoreDao>())
        return ShoppingListRepository(db) to dao
    }

    @Test
    fun createToBuyList_deactivatesPreviousAndInsertsActiveNeedToBuy() = runBlocking {
        val (repository, dao) = repositoryWithDao()
        whenever(dao.getMaxListPosition()).thenReturn(4)

        val id = repository.createToBuyList()

        verify(dao).deactivateAllToBuyLists()
        val captor = argumentCaptor<ShoppingListEntity>()
        verify(dao).insertShoppingList(captor.capture())
        val list = captor.firstValue
        assertEquals(id, list.id)
        assertEquals(ListKind.NEED_TO_BUY.name, list.kind)
        assertTrue(list.isActive)
        assertFalse(list.isFinished)
        assertNull(list.storeId)
        assertNull(list.finalTotal)
        assertEquals(5, list.position)
        assertTrue(
            "Title should be 'To Buy dd.MM.yyyy' but was '${list.name}'",
            list.name.matches(Regex("""To Buy \d{2}\.\d{2}\.\d{4}"""))
        )
    }

    @Test
    fun addToBuyItem_insertsPlainTextItemWithoutProductOrPrice() = runBlocking {
        val (repository, dao) = repositoryWithDao()
        whenever(dao.getMaxPositionForList(7L)).thenReturn(2)

        repository.addToBuyItem(7L, "  Milk  ")

        val captor = argumentCaptor<ShoppingListItemEntity>()
        verify(dao).insertShoppingListItem(captor.capture())
        val item = captor.firstValue
        assertEquals(7L, item.shoppingListId)
        assertEquals(0L, item.productId)
        assertEquals("Milk", item.customName)
        assertEquals(1.0, item.quantity, 0.0)
        assertNull(item.price)
        assertNull(item.discount)
        assertFalse(item.isChecked)
        assertEquals(3, item.position)
        // repository marks the list modified so the sync layer notices the new item
        verify(dao).updateModifiedAt(eq(7L), any())
    }

    @Test
    fun addToBuyItem_appendsAfterExistingItems() = runBlocking {
        val (repository, dao) = repositoryWithDao()
        whenever(dao.getMaxPositionForList(7L)).thenReturn(10)

        repository.addToBuyItem(7L, "Bread")

        val captor = argumentCaptor<ShoppingListItemEntity>()
        verify(dao).insertShoppingListItem(captor.capture())
        assertEquals(11, captor.firstValue.position)
    }

    @Test
    fun processPurchase_ignoresNeedToBuyList() = runBlocking {
        val (repository, dao) = repositoryWithDao()
        val listId = 9L
        whenever(dao.getShoppingListById(listId)).thenReturn(
            ShoppingListEntity(
                id = listId,
                name = "To Buy 01.01.2026",
                createDate = 1L,
                purchaseDate = null,
                storeId = null,
                isFinished = false,
                kind = ListKind.NEED_TO_BUY.name,
                isActive = true,
            )
        )

        repository.processPurchase(
            listId = listId,
            listName = null,
            storeName = "",
            price = 5.0,
            productRepository = mock<ProductRepository>(),
            priceRepository = mock<PriceRepository>(),
            categoryRepository = mock<CategoryRepository>(),
        )

        verify(dao, never()).updateShoppingList(any())
        verify(dao, never()).insertShoppingList(any())
        verify(dao, never()).deleteShoppingListItem(any())
    }
}
