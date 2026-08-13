package com.otakeeesen.byebyemoneylist

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.DashboardRepository
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListCategoryCrossRef
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListItemEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class DashboardRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: DashboardRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DashboardRepository(database)
    }

    @After
    fun teardown() {
        database.close()
    }

    private val nowMillis: Long
        get() = System.currentTimeMillis()

    private fun thisMonthStart(): Long =
        YearMonth.now().atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun thisMonthEnd(): Long =
        YearMonth.now().atEndOfMonth().atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun makeList(
        id: Long, name: String, createDate: Long = nowMillis,
        isFinished: Boolean = true, finalTotal: Double? = null, isIncome: Boolean = false
    ) = ShoppingListEntity(
        id = id, name = name, createDate = createDate,
        purchaseDate = createDate, storeId = null,
        isFinished = isFinished, finalTotal = finalTotal, isIncome = isIncome
    )

    private fun makeItem(id: Long, listId: Long, productId: Long, price: Double?, quantity: Double = 1.0) =
        ShoppingListItemEntity(id = id, shoppingListId = listId, productId = productId, quantity = quantity, isChecked = true, price = price)

    private fun makeProduct(id: Long, name: String, categoryId: Long?) =
        ProductEntity(id = id, name = name, barcode = "", picturePath = null, categoryId = categoryId)

    @Test
    fun categorySpending_sumsProductsOfCategoryAndChildCategories() = runBlocking {
        database.categoryDao().insertCategory(CategoryEntity(1L, "Food", "#FF0000"))
        database.categoryDao().insertCategory(CategoryEntity(2L, "Dairy", "#00FF00", 1L))

        database.productDao().insertProduct(makeProduct(10L, "Bread", 1L))
        database.productDao().insertProduct(makeProduct(11L, "Milk", 2L))
        database.productDao().insertProduct(makeProduct(12L, "Shampoo", 3L))

        database.shoppingListDao().insertShoppingList(makeList(100L, "Shop", finalTotal = 0.0))
        database.shoppingListDao().insertShoppingListItem(makeItem(1000L, 100L, 10L, 2.0))
        database.shoppingListDao().insertShoppingListItem(makeItem(1001L, 100L, 11L, 1.5, quantity = 2.0))
        database.shoppingListDao().insertShoppingListItem(makeItem(1002L, 100L, 12L, 5.0))

        val data = repository.getCategorySpending(1L, thisMonthStart(), thisMonthEnd())

        // Bread 2.0 + Milk 1.5*2 = 5.0 (child category); Shampoo excluded
        assertEquals(5.0, data.monthTotal, 0.001)
        assertEquals(5.0, data.overallTotal, 0.001)
    }

    @Test
    fun categorySpending_includesListsWithoutItemsTaggedWithCategory() = runBlocking {
        database.categoryDao().insertCategory(CategoryEntity(1L, "Food", "#FF0000"))

        // Finished list with no items, tagged with the category, finalTotal only
        database.shoppingListDao().insertShoppingList(makeList(100L, "Manual", finalTotal = 42.0))
        database.shoppingListDao().insertShoppingListCategoryCrossRef(
            ShoppingListCategoryCrossRef(100L, 1L)
        )

        // Finished list with no items, tagged with a different category -> excluded
        database.categoryDao().insertCategory(CategoryEntity(2L, "Transport", "#00FF00"))
        database.shoppingListDao().insertShoppingList(makeList(101L, "Other", finalTotal = 99.0))
        database.shoppingListDao().insertShoppingListCategoryCrossRef(
            ShoppingListCategoryCrossRef(101L, 2L)
        )

        // Unfinished list with no items -> excluded
        database.shoppingListDao().insertShoppingList(makeList(102L, "Draft", isFinished = false, finalTotal = 10.0))
        database.shoppingListDao().insertShoppingListCategoryCrossRef(
            ShoppingListCategoryCrossRef(102L, 1L)
        )

        val data = repository.getCategorySpending(1L, thisMonthStart(), thisMonthEnd())

        assertEquals(42.0, data.monthTotal, 0.001)
        assertEquals(42.0, data.overallTotal, 0.001)
    }

    @Test
    fun categorySpending_includesListsWithoutItemsTaggedWithChildCategory() = runBlocking {
        database.categoryDao().insertCategory(CategoryEntity(1L, "Food", "#FF0000"))
        database.categoryDao().insertCategory(CategoryEntity(2L, "Dairy", "#00FF00", 1L))

        // List without items tagged with the child category
        database.shoppingListDao().insertShoppingList(makeList(100L, "Dairy manual", finalTotal = 30.0))
        database.shoppingListDao().insertShoppingListCategoryCrossRef(
            ShoppingListCategoryCrossRef(100L, 2L)
        )

        val data = repository.getCategorySpending(1L, thisMonthStart(), thisMonthEnd())

        assertEquals(30.0, data.monthTotal, 0.001)
    }

    @Test
    fun categorySpending_ignoresIncomeLists() = runBlocking {
        database.categoryDao().insertCategory(CategoryEntity(1L, "Food", "#FF0000"))

        database.shoppingListDao().insertShoppingList(makeList(100L, "Income", finalTotal = 50.0, isIncome = true))
        database.shoppingListDao().insertShoppingListCategoryCrossRef(
            ShoppingListCategoryCrossRef(100L, 1L)
        )

        val data = repository.getCategorySpending(1L, thisMonthStart(), thisMonthEnd())

        assertEquals(0.0, data.monthTotal, 0.001)
    }

    @Test
    fun categorySpending_ignoresListsWithoutItemsAndWithoutTag() = runBlocking {
        database.categoryDao().insertCategory(CategoryEntity(1L, "Food", "#FF0000"))

        database.shoppingListDao().insertShoppingList(makeList(100L, "No tag", finalTotal = 20.0))

        val data = repository.getCategorySpending(1L, thisMonthStart(), thisMonthEnd())

        assertEquals(0.0, data.monthTotal, 0.001)
    }
}
