package com.otakeeesen.byebyemoneylist

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.entity.*
import com.otakeeesen.byebyemoneylist.data.local.repository.StoreRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals

@RunWith(AndroidJUnit4::class)
class StoreRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: StoreRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = StoreRepository(database)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun mergeStores_withConflictingCategories_doesNotCrash() = runBlocking {
        // Setup categories
        val cat1 = CategoryEntity(1L, "Cat 1", "#FF0000", null)
        val cat2 = CategoryEntity(2L, "Cat 2", "#00FF00", null)
        database.categoryDao().insertCategory(cat1)
        database.categoryDao().insertCategory(cat2)

        // Setup stores
        val storeA = StoreEntity(1L, "Store A", null)
        val storeB = StoreEntity(2L, "Store B", null)
        database.storeDao().insertStore(storeA)
        database.storeDao().insertStore(storeB)

        // Setup cross refs: Both have Cat 1, Store B also has Cat 2
        database.storeDao().insertStoreCategoryCrossRef(StoreCategoryCrossRef(1L, 1L))
        database.storeDao().insertStoreCategoryCrossRef(StoreCategoryCrossRef(2L, 1L))
        database.storeDao().insertStoreCategoryCrossRef(StoreCategoryCrossRef(2L, 2L))

        // Merge B into A
        val resultStore = storeA.copy(name = "Merged Store")
        repository.mergeStores(1L, 2L, resultStore, listOf(1L, 2L))

        // Verify
        val refs = runBlocking { repository.getAllStoreCategoryCrossRefs().first() }
        assertEquals(2, refs.size) // Should be (1,1) and (1,2)
    }
}
