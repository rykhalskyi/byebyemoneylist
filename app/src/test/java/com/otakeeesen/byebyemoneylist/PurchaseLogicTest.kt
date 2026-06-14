package com.otakeeesen.byebyemoneylist

import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.dao.ShoppingListDao
import com.otakeeesen.byebyemoneylist.data.local.dao.ProductDao
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListItemEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ProductRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.PriceRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.*

class PurchaseLogicTest {

    @Test
    fun `processPurchase marks existing list as finished and removes zero quantity items`() = runBlocking {
        // Setup Mocks
        val db = mock<AppDatabase>()
        val shoppingListDao = mock<ShoppingListDao>()
        val productDao = mock<ProductDao>()
        val storeDao = mock<com.otakeeesen.byebyemoneylist.data.local.dao.StoreDao>()
        
        whenever(db.shoppingListDao()).thenReturn(shoppingListDao)
        whenever(db.productDao()).thenReturn(productDao)
        whenever(db.storeDao()).thenReturn(storeDao)
        
        val productRepo = mock<ProductRepository>()
        val priceRepo = mock<PriceRepository>()
        val categoryRepo = mock<CategoryRepository>()
        val repository = ShoppingListRepository(db)

        // Setup Test Data
        val listId = 1L
        val list = ShoppingListEntity(id = listId, name = "Test List", createDate = System.currentTimeMillis(), purchaseDate = null, storeId = null, isFinished = false)
        whenever(shoppingListDao.getShoppingListById(listId)).thenReturn(list)

        val item1 = ShoppingListItemEntity(id = 10, shoppingListId = listId, productId = 1, quantity = 2.0, price = 1.0, isChecked = true)
        val item2 = ShoppingListItemEntity(id = 11, shoppingListId = listId, productId = 2, quantity = 0.0, price = 0.0, isChecked = false)
        whenever(shoppingListDao.getItemsForListSync(listId)).thenReturn(listOf(item1, item2))

        // Act
        repository.processPurchase(
            listId = listId,
            listName = null,
            storeName = "Test Store",
            price = 2.0,
            items = emptyList(),
            productRepository = productRepo,
            priceRepository = priceRepo,
            categoryRepository = categoryRepo
        )

        // Assert
        val captor = argumentCaptor<ShoppingListEntity>()
        verify(shoppingListDao).updateShoppingList(captor.capture())
        assertTrue(captor.firstValue.isFinished)
        verify(shoppingListDao).deleteShoppingListItem(item2)
        verify(shoppingListDao, never()).deleteShoppingListItem(item1)
    }
}
