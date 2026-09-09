package com.otakeeesen.byebyemoneylist

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductAliasEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreCategoryCrossRef
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the pull-update DAO helpers overwrite *shared* fields only and leave the
 * local-only fields (store logo/address/receiptName, product picturePath/status/changedAt)
 * untouched.
 */
@RunWith(AndroidJUnit4::class)
class SyncUpdateDaoTest {

    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun storePullUpdate_overwritesSharedFieldsOnly() {
        val store = StoreEntity(
            id = 1, name = "Rewe", logoPath = "/logo.png",
            address = "Hauptstrasse 1", receiptName = "REWE", serverId = "s-1"
        )
        database.storeDao().insertStore(store)

        database.storeDao().updateSharedFromServer(1, "Rewe City", "Zeil 10")

        val updated = database.storeDao().getStoreById(1)!!
        assertEquals("Rewe City", updated.name)
        assertEquals("Zeil 10", updated.address)
        assertEquals("/logo.png", updated.logoPath)
        assertEquals("REWE", updated.receiptName)
    }

    @Test
    fun storePullUpdate_replacesCategoryCrossRefs() {
        database.categoryDao().insertCategory(CategoryEntity(id = 10, name = "Food", color = "#FF6B6B", parentId = null, isIncome = false, emoji = null, serverId = "c-1"))
        database.categoryDao().insertCategory(CategoryEntity(id = 11, name = "Drinks", color = "#FF6B6B", parentId = null, isIncome = false, emoji = null, serverId = "c-2"))
        database.categoryDao().insertCategory(CategoryEntity(id = 12, name = "Home", color = "#FF6B6B", parentId = null, isIncome = false, emoji = null, serverId = "c-3"))
        database.storeDao().insertStore(StoreEntity(id = 1, name = "Rewe", logoPath = null, serverId = "s-1"))
        database.storeDao().insertStoreCategoryCrossRef(StoreCategoryCrossRef(storeId = 1, categoryId = 10))
        database.storeDao().insertStoreCategoryCrossRef(StoreCategoryCrossRef(storeId = 1, categoryId = 11))

        // Pull-side replacement: delete then write the server set.
        database.storeDao().deleteCategoriesForStore(1)
        database.storeDao().insertStoreCategoryCrossRef(StoreCategoryCrossRef(storeId = 1, categoryId = 10))
        database.storeDao().insertStoreCategoryCrossRef(StoreCategoryCrossRef(storeId = 1, categoryId = 12))

        val remaining = database.storeDao().getAllStoreCategoryCrossRefsOnce()
            .filter { it.storeId == 1L }
            .map { it.categoryId }
            .sorted()
        assertEquals(listOf(10L, 12L), remaining)
    }

    @Test
    fun productPullUpdate_overwritesSharedFieldsOnly() {
        val insertedId = database.productDao().insertProduct(
            ProductEntity(
                name = "Milk", barcode = "123", picturePath = "/pic.png",
                status = "barcode", changedAt = 111L, isFavorite = false,
                isSubscription = false, isIncome = false, serverId = "p-1"
            )
        )

        database.productDao().updateFromServer(
            id = insertedId, name = "Milch", barcode = "42", categoryId = null,
            isFavorite = true, isSubscription = false, isIncome = false
        )

        val updated = database.productDao().getProductById(insertedId)!!
        assertEquals("Milch", updated.name)
        assertEquals("42", updated.barcode)
        assertEquals(true, updated.isFavorite)
        assertEquals("/pic.png", updated.picturePath)
        assertEquals("barcode", updated.status)
        assertEquals(111L, updated.changedAt)
    }

    @Test
    fun productPullUpdate_replacesAliasesWithTheServerSet() {
        val productId = database.productDao().insertProduct(
            ProductEntity(name = "Milk", barcode = "123", picturePath = null, serverId = "p-1")
        )
        database.productAliasDao().insertAlias(
            ProductAliasEntity(id = 0, productId = productId, aliasName = "Old", storeId = 1L)
        )

        database.productAliasDao().deleteByProductId(productId)
        database.productAliasDao().insertAlias(
            ProductAliasEntity(id = 0, productId = productId, aliasName = "Fresh", storeId = null)
        )

        val aliases = database.productAliasDao().getAliasesByProductId(productId)
        assertEquals(listOf("Fresh"), aliases.map { it.aliasName })
        assertNull(aliases.single().storeId)
    }

    @Test
    fun categoryPullUpdate_overwritesAllSharedFields() {
        database.categoryDao().insertCategory(
            CategoryEntity(id = 1, name = "Food", color = "#FF6B6B", parentId = null, isIncome = false, emoji = null, serverId = "c-1")
        )

        database.categoryDao().updateFromServer(
            id = 1, name = "Groceries", color = "#FF00FF", emoji = "🛒", isIncome = true, parentId = 7
        )

        val updated = database.categoryDao().getCategoryById(1)!!
        assertEquals("Groceries", updated.name)
        assertEquals("#FF00FF", updated.color)
        assertEquals("🛒", updated.emoji)
        assertEquals(true, updated.isIncome)
        assertEquals(7L, updated.parentId)
    }
}
