package com.otakeeesen.byebyemoneylist

import com.otakeeesen.byebyemoneylist.data.agent.LlmTextGenerator
import com.otakeeesen.byebyemoneylist.data.agent.ToBuyAutoMatcher
import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
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
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ToBuyAutoMatchRepositoryTest {

    private val activeToBuyList = ShoppingListEntity(
        id = 100L,
        name = "To Buy 01.01.2026",
        createDate = 1L,
        purchaseDate = null,
        storeId = null,
        isFinished = false,
        kind = "NEED_TO_BUY",
        isActive = true,
    )

    private fun dbWithDao(): Pair<AppDatabase, ShoppingListDao> {
        val db = mock<AppDatabase>()
        val dao = mock<ShoppingListDao>()
        whenever(db.shoppingListDao()).thenReturn(dao)
        whenever(db.storeDao()).thenReturn(mock<StoreDao>())
        return db to dao
    }

    private class FakeMatcher(private val ids: List<Long>) :
        ToBuyAutoMatcher(mock<PreferencesManager>(), mock<LlmTextGenerator>()) {
        var pendingSeen: List<Pair<Long, String>>? = null
        var purchasedSeen: List<String>? = null
        override suspend fun match(pending: List<Pair<Long, String>>, purchased: List<String>): List<Long> {
            pendingSeen = pending
            purchasedSeen = purchased
            return ids
        }
    }

    private suspend fun ShoppingListRepository.purchase(items: List<ScannedItem>) {
        processPurchase(
            listId = null,
            listName = "Store 01.01.2026",
            storeName = "Store",
            price = 10.0,
            items = items,
            productRepository = mock<ProductRepository>(),
            priceRepository = mock<PriceRepository>(),
            categoryRepository = mock<CategoryRepository>(),
        )
    }

    @Test
    fun `checks matched pending items on the active to buy list`() {
        val (db, dao) = dbWithDao()
        whenever(dao.getActiveToBuyList()).thenReturn(activeToBuyList)
        whenever(dao.getItemsForListSync(100L)).thenReturn(
            listOf(
                ShoppingListItemEntity(id = 1, shoppingListId = 100, productId = 0, quantity = 1.0, isChecked = false, customName = "Milk"),
                ShoppingListItemEntity(id = 2, shoppingListId = 100, productId = 0, quantity = 1.0, isChecked = false, customName = "Bread"),
                ShoppingListItemEntity(id = 3, shoppingListId = 100, productId = 0, quantity = 1.0, isChecked = true, customName = "Eggs"),
            )
        )
        whenever(dao.getShoppingListItemById(1L)).thenReturn(
            ShoppingListItemEntity(id = 1, shoppingListId = 100, productId = 0, quantity = 1.0, isChecked = false, customName = "Milk")
        )
        whenever(dao.getShoppingListIdByItemId(1L)).thenReturn(100L)

        val matcher = FakeMatcher(listOf(1L))
        runBlocking {
            ShoppingListRepository(db, matcher, this).purchase(
                listOf(ScannedItem(name = "milk", quantity = 1.0, price = 2.0, productId = 5L))
            )
        }

        verify(dao).updateItemChecked(1L, true)
        verify(dao, never()).updateItemChecked(eq(2L), any())
        assertEquals(listOf(1L to "Milk", 2L to "Bread"), matcher.pendingSeen)
        assertEquals(listOf("milk"), matcher.purchasedSeen)
    }

    @Test
    fun `does nothing when there is no active to buy list`() {
        val (db, dao) = dbWithDao()
        whenever(dao.getActiveToBuyList()).thenReturn(null)

        val matcher = FakeMatcher(listOf(1L))
        runBlocking {
            ShoppingListRepository(db, matcher, this).purchase(
                listOf(ScannedItem(name = "milk", quantity = 1.0, price = 2.0, productId = 5L))
            )
        }

        assertNull(matcher.pendingSeen)
        verify(dao, never()).updateItemChecked(any(), any())
    }

    @Test
    fun `does not auto match when the target list is a need to buy list`() {
        val (db, dao) = dbWithDao()
        val needToBuyId = 9L
        whenever(dao.getShoppingListById(needToBuyId)).thenReturn(
            activeToBuyList.copy(id = needToBuyId)
        )

        val matcher = FakeMatcher(listOf(1L))
        runBlocking {
            ShoppingListRepository(db, matcher, this).processPurchase(
                listId = needToBuyId,
                listName = null,
                storeName = "Store",
                price = 10.0,
                items = listOf(ScannedItem(name = "milk", quantity = 1.0, price = 2.0, productId = 5L)),
                productRepository = mock<ProductRepository>(),
                priceRepository = mock<PriceRepository>(),
                categoryRepository = mock<CategoryRepository>(),
            )
        }

        assertNull(matcher.pendingSeen)
        verify(dao, never()).getActiveToBuyList()
    }
}
