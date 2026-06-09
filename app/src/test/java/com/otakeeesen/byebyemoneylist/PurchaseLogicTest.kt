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
import org.mockito.Mockito.*

class PurchaseLogicTest {

    @Test
    fun `processPurchase marks existing list as finished and removes zero quantity items`() = runBlocking {
        // Setup Mocks
        val db = mock(AppDatabase::class.java)
        val shoppingListDao = mock(ShoppingListDao::class.java)
        val productDao = mock(ProductDao::class.java)
        val storeDao = mock(com.otakeeesen.byebyemoneylist.data.local.dao.StoreDao::class.java)
        
        `when`(db.shoppingListDao()).thenReturn(shoppingListDao)
        `when`(db.productDao()).thenReturn(productDao)
        `when`(db.storeDao()).thenReturn(storeDao)
        
        val productRepo = mock(ProductRepository::class.java)
        val priceRepo = mock(PriceRepository::class.java)
        val categoryRepo = mock(CategoryRepository::class.java)
        val repository = ShoppingListRepository(db)

        // Setup Test Data
        val listId = 1L
        val list = ShoppingListEntity(id = listId, name = "Test List", createDate = System.currentTimeMillis(), purchaseDate = null, storeId = null, isFinished = false)
        `when`(shoppingListDao.getShoppingListById(listId)).thenReturn(list)

        val item1 = ShoppingListItemEntity(id = 10, shoppingListId = listId, productId = 1, quantity = 2.0, price = 1.0, isChecked = true)
        val item2 = ShoppingListItemEntity(id = 11, shoppingListId = listId, productId = 2, quantity = 0.0, price = 0.0, isChecked = false)
        `when`(shoppingListDao.getItemsForListSync(listId)).thenReturn(listOf(item1, item2))

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
        verify(shoppingListDao).updateShoppingList(argThat { it.isFinished })
        verify(shoppingListDao).deleteShoppingListItem(item2)
        verify(shoppingListDao, never()).deleteShoppingListItem(item1)
    }
}
