package com.otakeeesen.byebyemoneylist

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.ProductRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: ProductRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ProductRepository(database)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun findBestProductMatchId_fuzzyMatchesAndSavesAlias() = runBlocking {
        database.productDao().insertProduct(ProductEntity(id = 1, name = "BirneConference", barcode = "", picturePath = null))
        val products = database.productDao().getAllProductsOnce()

        val id = repository.findBestProductMatchId("Birne Conference 1kg", null, products)

        assertEquals(1L, id)

        // Alias was saved so future lookups hit the exact-alias fast path
        val alias = database.productAliasDao().getAliasesByName("Birne Conference 1kg")
        assertEquals(1, alias.size)
        assertEquals(1L, alias[0].productId)
    }

    @Test
    fun findBestProductMatchId_returnsNullWhenNoMatch() = runBlocking {
        database.productDao().insertProduct(ProductEntity(id = 1, name = "Ananas", barcode = "", picturePath = null))
        val products = database.productDao().getAllProductsOnce()

        val id = repository.findBestProductMatchId("Apfel", null, products)

        assertNull(id)
    }

    @Test
    fun findBestProductMatchId_returnsExactProductName() = runBlocking {
        database.productDao().insertProduct(ProductEntity(id = 1, name = "Bananen", barcode = "", picturePath = null))
        val products = database.productDao().getAllProductsOnce()

        val id = repository.findBestProductMatchId("Bananen", null, products)

        assertEquals(1L, id)
    }
}
