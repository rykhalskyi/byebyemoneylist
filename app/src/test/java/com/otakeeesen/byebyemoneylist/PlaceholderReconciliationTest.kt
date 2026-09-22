package com.otakeeesen.byebyemoneylist

import com.otakeeesen.byebyemoneylist.data.MatchState
import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.dao.ProductDao
import com.otakeeesen.byebyemoneylist.data.local.dao.ShoppingListDao
import com.otakeeesen.byebyemoneylist.data.local.dao.StoreDao
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListItemEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.PriceRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ProductRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import com.otakeeesen.byebyemoneylist.ui.components.scanner.ScannedItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PlaceholderReconciliationTest {

    private val listId = 1L

    private fun list() = ShoppingListEntity(
        id = listId, name = "Groceries", createDate = 1L,
        purchaseDate = null, storeId = null, isFinished = false,
    )

    private class Fixture {
        val db: AppDatabase = mock()
        val dao: ShoppingListDao = mock()
        val storeDao: StoreDao = mock()
        val productRepo: ProductRepository = mock()
        val priceRepo: PriceRepository = mock()
        val categoryRepo: CategoryRepository = mock()
        val repository: ShoppingListRepository

        init {
            runBlocking {
                whenever(db.shoppingListDao()).thenReturn(dao)
                whenever(db.storeDao()).thenReturn(storeDao)
                whenever(db.productDao()).thenReturn(mock<ProductDao>())
                whenever(storeDao.getAllStoresOnce()).thenReturn(emptyList())
            }
            repository = ShoppingListRepository(db)
        }
    }

    @Test
    fun `manual purchase marks unchecked free-text rows not bought`() = runBlocking {
        val f = Fixture()
        whenever(f.dao.getShoppingListById(listId)).thenReturn(list())
        whenever(f.dao.getCategoriesForShoppingListSync(listId)).thenReturn(emptyList())
        val unchecked = ShoppingListItemEntity(
            id = 100, shoppingListId = listId, productId = 0L, quantity = 1.0,
            isChecked = false, customName = "Bread", isPlaceholder = true,
        )
        val checked = ShoppingListItemEntity(
            id = 101, shoppingListId = listId, productId = 0L, quantity = 1.0,
            isChecked = true, customName = "Milk", isPlaceholder = true,
        )
        whenever(f.dao.getItemsForListSync(listId)).thenReturn(listOf(unchecked, checked))

        f.repository.processPurchase(
            listId = listId, listName = null, storeName = "Store", price = 5.0,
            items = emptyList(),
            productRepository = f.productRepo, priceRepository = f.priceRepo,
            categoryRepository = f.categoryRepo,
        )

        val captor = argumentCaptor<ShoppingListItemEntity>()
        verify(f.dao).updateShoppingListItem(captor.capture())
        val updated = captor.firstValue
        assertEquals(100L, updated.id)
        assertEquals(MatchState.NOT_BOUGHT, updated.matchState)
        assertTrue(updated.isPlaceholder)
    }

    @Test
    fun `matched purchase turns a free-text row into the bought product`() = runBlocking {
        val f = Fixture()
        whenever(f.dao.getShoppingListById(listId)).thenReturn(list())
        whenever(f.dao.getCategoriesForShoppingListSync(listId)).thenReturn(emptyList())
        val placeholder = ShoppingListItemEntity(
            id = 100, shoppingListId = listId, productId = 0L, quantity = 1.0,
            isChecked = false, customName = "Milk", isPlaceholder = true,
        )
        whenever(f.dao.getItemsForListSync(listId)).thenReturn(listOf(placeholder))
        whenever(f.productRepo.getAllProductsOnce()).thenReturn(emptyList())
        whenever(f.dao.getItemsWithProductForListsSync(listOf(listId))).thenReturn(emptyList())

        val scanned = ScannedItem(name = "Milk", quantity = 2.0, price = 1.5, productId = 7L)
        f.repository.processPurchase(
            listId = listId, listName = null, storeName = "Store", price = 3.0,
            items = listOf(scanned),
            productRepository = f.productRepo, priceRepository = f.priceRepo,
            categoryRepository = f.categoryRepo,
            reconciliations = mapOf(100L to 0),
        )

        verify(f.dao, never()).insertShoppingListItem(any())
        val captor = argumentCaptor<ShoppingListItemEntity>()
        verify(f.dao).updateShoppingListItem(captor.capture())
        val updated = captor.firstValue
        assertEquals(100L, updated.id)
        assertEquals(7L, updated.productId)
        assertFalse(updated.isPlaceholder)
        assertEquals(MatchState.MATCHED, updated.matchState)
        assertTrue(updated.isChecked)
        assertEquals("Milk", updated.customName)
        assertEquals(2.0, updated.quantity, 0.0001)
    }

    @Test
    fun `unmatched free-text row is marked not bought when a purchase has items`() = runBlocking {
        val f = Fixture()
        whenever(f.dao.getShoppingListById(listId)).thenReturn(list())
        whenever(f.dao.getCategoriesForShoppingListSync(listId)).thenReturn(emptyList())
        val placeholder = ShoppingListItemEntity(
            id = 100, shoppingListId = listId, productId = 0L, quantity = 1.0,
            isChecked = false, customName = "Bread", isPlaceholder = true,
        )
        whenever(f.dao.getItemsForListSync(listId)).thenReturn(listOf(placeholder))
        whenever(f.productRepo.getAllProductsOnce()).thenReturn(emptyList())
        whenever(f.dao.getItemsWithProductForListsSync(listOf(listId))).thenReturn(emptyList())

        val scanned = ScannedItem(name = "Milk", quantity = 1.0, price = 1.5, productId = 7L)
        f.repository.processPurchase(
            listId = listId, listName = null, storeName = "Store", price = 3.0,
            items = listOf(scanned),
            productRepository = f.productRepo, priceRepository = f.priceRepo,
            categoryRepository = f.categoryRepo,
            reconciliations = emptyMap(),
        )

        val captor = argumentCaptor<ShoppingListItemEntity>()
        verify(f.dao).updateShoppingListItem(captor.capture())
        assertEquals(MatchState.NOT_BOUGHT, captor.firstValue.matchState)
    }

    @Test
    fun `relink absorbs the chosen product row and keeps the typed text`() = runBlocking {
        val f = Fixture()
        val placeholder = ShoppingListItemEntity(
            id = 100, shoppingListId = listId, productId = 0L, quantity = 1.0,
            isChecked = false, customName = "Milk", isPlaceholder = true,
        )
        val productRow = ShoppingListItemEntity(
            id = 200, shoppingListId = listId, productId = 7L, quantity = 2.0,
            isChecked = true, price = 1.5, isPlaceholder = false,
        )
        whenever(f.dao.getShoppingListItemById(100L)).thenReturn(placeholder)
        whenever(f.dao.getShoppingListItemById(200L)).thenReturn(productRow)

        f.repository.relinkItem(itemId = 100L, toItemId = 200L)

        val captor = argumentCaptor<ShoppingListItemEntity>()
        verify(f.dao).updateShoppingListItem(captor.capture())
        val updated = captor.firstValue
        assertEquals(7L, updated.productId)
        assertFalse(updated.isPlaceholder)
        assertEquals(MatchState.MATCHED, updated.matchState)
        assertEquals("Milk", updated.customName)
        assertTrue(updated.isChecked)
        assertEquals(1.5, updated.price!!, 0.0001)
        verify(f.dao).deleteShoppingListItem(productRow)
    }

    @Test
    fun `relink with no target marks the row not bought`() = runBlocking {
        val f = Fixture()
        val placeholder = ShoppingListItemEntity(
            id = 100, shoppingListId = listId, productId = 7L, quantity = 1.0,
            isChecked = true, customName = "Milk", isPlaceholder = false, price = 1.5,
        )
        whenever(f.dao.getShoppingListItemById(100L)).thenReturn(placeholder)

        f.repository.relinkItem(itemId = 100L, toItemId = null)

        val captor = argumentCaptor<ShoppingListItemEntity>()
        verify(f.dao).updateShoppingListItem(captor.capture())
        val updated = captor.firstValue
        assertEquals(0L, updated.productId)
        assertTrue(updated.isPlaceholder)
        assertEquals(MatchState.NOT_BOUGHT, updated.matchState)
    }
}
