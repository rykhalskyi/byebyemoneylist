package com.otakeeesen.byebyemoneylist

import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.dao.ShoppingListDao
import com.otakeeesen.byebyemoneylist.data.local.dao.ProductDao
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
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

    @Test
    fun `processPurchase automatically assigns parent category if threshold is met`() = runBlocking {
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

        val listId = 1L
        val list = ShoppingListEntity(id = listId, name = "Test List", createDate = System.currentTimeMillis(), purchaseDate = null, storeId = null, isFinished = false)
        whenever(shoppingListDao.getShoppingListById(listId)).thenReturn(list)

        // Set up categories: Root "Supermarket" (101), Child "Bakery" (102), Child "Dairy" (103)
        // and another unrelated Root "Health & Beauty" (201)
        val catSupermarket = CategoryEntity(id = 101, name = "Supermarket", parentId = null)
        val catBakery = CategoryEntity(id = 102, name = "Bakery", parentId = 101)
        val catDairy = CategoryEntity(id = 103, name = "Dairy", parentId = 101)
        val catHealth = CategoryEntity(id = 201, name = "Health & Beauty", parentId = null)

        whenever(categoryRepo.getAllCategoriesOnce()).thenReturn(listOf(catSupermarket, catBakery, catDairy, catHealth))

        // We have 5 items in the list:
        // Item 1: Bakery (parent: Supermarket)
        // Item 2: Bakery (parent: Supermarket)
        // Item 3: Dairy (parent: Supermarket) -> Total 3 items (60%) under parent Supermarket (>= 40%)
        // Item 4: Health & Beauty (parent: none/Health & Beauty) -> 1 item (20%) under parent Health & Beauty (< 40%)
        // Item 5: No category -> 1 item
        val itemsWithProducts = listOf(
            com.otakeeesen.byebyemoneylist.data.local.dao.ShoppingListItemWithProduct(
                id = 1, shoppingListId = listId, productId = 10, quantity = 1.0, isChecked = true, position = 1,
                productName = "Bread", productPicturePath = null, productStatus = null, productIsSubscription = false, productIsFavorite = false,
                itemPrice = 1.0, price = 1.0, discount = null, customName = null, productCategoryId = 102
            ),
            com.otakeeesen.byebyemoneylist.data.local.dao.ShoppingListItemWithProduct(
                id = 2, shoppingListId = listId, productId = 11, quantity = 1.0, isChecked = true, position = 2,
                productName = "Cake", productPicturePath = null, productStatus = null, productIsSubscription = false, productIsFavorite = false,
                itemPrice = 1.0, price = 1.0, discount = null, customName = null, productCategoryId = 102
            ),
            com.otakeeesen.byebyemoneylist.data.local.dao.ShoppingListItemWithProduct(
                id = 3, shoppingListId = listId, productId = 12, quantity = 1.0, isChecked = true, position = 3,
                productName = "Milk", productPicturePath = null, productStatus = null, productIsSubscription = false, productIsFavorite = false,
                itemPrice = 2.0, price = 2.0, discount = null, customName = null, productCategoryId = 103
            ),
            com.otakeeesen.byebyemoneylist.data.local.dao.ShoppingListItemWithProduct(
                id = 4, shoppingListId = listId, productId = 13, quantity = 1.0, isChecked = true, position = 4,
                productName = "Soap", productPicturePath = null, productStatus = null, productIsSubscription = false, productIsFavorite = false,
                itemPrice = 1.5, price = 1.5, discount = null, customName = null, productCategoryId = 201
            ),
            com.otakeeesen.byebyemoneylist.data.local.dao.ShoppingListItemWithProduct(
                id = 5, shoppingListId = listId, productId = 14, quantity = 1.0, isChecked = true, position = 5,
                productName = "Uncategorized", productPicturePath = null, productStatus = null, productIsSubscription = false, productIsFavorite = false,
                itemPrice = 1.0, price = 1.0, discount = null, customName = null, productCategoryId = null
            )
        )

        whenever(shoppingListDao.getItemsWithProductForListsSync(listOf(listId))).thenReturn(itemsWithProducts)
        whenever(shoppingListDao.getItemsForListSync(listId)).thenReturn(emptyList()) // processPurchase triggers this

        // Act
        repository.processPurchase(
            listId = listId,
            listName = null,
            storeName = "Test Store",
            price = 5.0,
            items = listOf(
                com.otakeeesen.byebyemoneylist.ui.components.scanner.ScannedItem(
                    name = "Bread", price = 1.0, quantity = 1.0, categorySuggestion = "Bakery", productId = 10
                )
            ),
            productRepository = productRepo,
            priceRepository = priceRepo,
            categoryRepository = categoryRepo
        )

        // Assert
        // Verify that Category 101 (Supermarket) was assigned to the list since 3/5 = 60% of items belong to it.
        // Category 201 (Health & Beauty) should NOT be assigned (only 1/5 = 20% < 40%).
        val crossRefCaptor = argumentCaptor<com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListCategoryCrossRef>()
        verify(shoppingListDao).insertShoppingListCategoryCrossRef(crossRefCaptor.capture())
        
        val capturedCrossRefs = crossRefCaptor.allValues
        assertTrue(capturedCrossRefs.any { it.categoryId == 101L && it.shoppingListId == listId })
        assertTrue(capturedCrossRefs.none { it.categoryId == 201L })
    }
}
