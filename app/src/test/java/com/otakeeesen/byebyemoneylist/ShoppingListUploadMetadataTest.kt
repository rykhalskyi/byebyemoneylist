package com.otakeeesen.byebyemoneylist

import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.dao.ShoppingListDao
import com.otakeeesen.byebyemoneylist.data.local.dao.StoreDao
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

class ShoppingListUploadMetadataTest {

    private fun repositoryWithDao(): Pair<ShoppingListRepository, ShoppingListDao> {
        val db = mock<AppDatabase>()
        val dao = mock<ShoppingListDao>()
        whenever(db.shoppingListDao()).thenReturn(dao)
        whenever(db.storeDao()).thenReturn(mock<StoreDao>())
        return ShoppingListRepository(db, runInTransaction = { it() }) to dao
    }

    @Test
    fun `duplicating an uploaded list does not inherit upload metadata`() = runBlocking {
        val (repository, dao) = repositoryWithDao()
        val source = ShoppingListEntity(
            id = 1L,
            name = "Weekly",
            createDate = 1000L,
            purchaseDate = null,
            storeId = null,
            isFinished = true,
            uploadId = "upload-1",
            lastUploadedAt = 2000L,
        )
        whenever(dao.getShoppingListById(1L)).thenReturn(source)
        whenever(dao.getMaxListPosition()).thenReturn(3)
        whenever(dao.getCategoriesForShoppingListSync(1L)).thenReturn(emptyList())
        whenever(dao.getItemsForListSync(1L)).thenReturn(emptyList())

        repository.duplicateShoppingList(1L)

        val captor = argumentCaptor<ShoppingListEntity>()
        verify(dao).insertShoppingList(captor.capture())
        val duplicate = captor.firstValue
        assertNull("A duplicated list must get a fresh uploadId", duplicate.uploadId)
        assertNull("A duplicated list must not look already uploaded", duplicate.lastUploadedAt)
    }

    @Test
    fun `forwarding a recurring list does not inherit upload metadata`() = runBlocking {
        val (repository, dao) = repositoryWithDao()
        val recurring = ShoppingListEntity(
            id = 1L,
            name = "Monthly shop",
            createDate = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(40),
            purchaseDate = null,
            storeId = null,
            isFinished = true,
            isRecurring = true,
            recurringPeriod = "MONTH",
            isForwardEmpty = true,
            uploadId = "upload-1",
            lastUploadedAt = 2000L,
        )
        whenever(dao.getAllShoppingListsSynchronous()).thenReturn(listOf(recurring))
        whenever(dao.getItemsForListSync(1L)).thenReturn(emptyList())
        whenever(dao.getMaxListPosition()).thenReturn(5)
        whenever(dao.getCategoriesForShoppingListSync(any())).thenReturn(emptyList())

        repository.checkAndForwardRecurringLists()

        val captor = argumentCaptor<ShoppingListEntity>()
        verify(dao, atLeastOnce()).insertShoppingList(captor.capture())
        captor.allValues.forEach { forwarded ->
            assertNull("Forwarded list must get a fresh uploadId", forwarded.uploadId)
            assertNull("Forwarded list must not look already uploaded", forwarded.lastUploadedAt)
        }
    }
}
