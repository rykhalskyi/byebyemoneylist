package com.otakeeesen.byebyemoneylist

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.entity.PriceEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListItemEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShoppingListRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: ShoppingListRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ShoppingListRepository(database)
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun startOfMonth(monthsDelta: Long): Long {
        val target = java.time.LocalDate.now().withDayOfMonth(1).plusMonths(monthsDelta)
        return target.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun makeList(
        id: Long, name: String, createDate: Long,
        isRecurring: Boolean = false, recurringPeriod: String = "MONTH",
        isForwardEmpty: Boolean = true, isSubscription: Boolean = false,
        isIncome: Boolean = false, isFinished: Boolean = false,
        isArchived: Boolean = false
    ) = ShoppingListEntity(
        id = id, name = name, createDate = createDate,
        purchaseDate = null, storeId = null,
        isRecurring = isRecurring, recurringPeriod = recurringPeriod,
        isForwardEmpty = isForwardEmpty, isSubscription = isSubscription,
        isIncome = isIncome, isFinished = isFinished, isArchived = isArchived
    )

    private fun makeItem(id: Long, listId: Long, productId: Long, price: Double?, quantity: Double = 1.0, checked: Boolean = false) =
        ShoppingListItemEntity(id = id, shoppingListId = listId, productId = productId, quantity = quantity, isChecked = checked, price = price)

    @Test
    fun forward_empty_archivesOldAndCreatesEmptyNew() = runBlocking {
        val listId = 1L
        database.shoppingListDao().insertShoppingList(
            makeList(listId, "Groceries", startOfMonth(-1), isRecurring = true, isForwardEmpty = true)
        )
        database.shoppingListDao().insertShoppingListItem(makeItem(100L, listId, 10L, 5.0))
        database.shoppingListDao().insertShoppingListItem(makeItem(101L, listId, 11L, 3.0, checked = true))

        repository.checkAndForwardRecurringLists()

        val allLists = repository.getAllShoppingListsOnce()
        assertEquals(2, allLists.size)

        val oldList = allLists.first { it.id == listId }
        assertTrue(oldList.isFinished)
        assertTrue(oldList.isArchived)
        assertNotNull(oldList.purchaseDate)
        assertEquals(8.0, oldList.finalTotal!!, 0.001)

        val newList = allLists.first { it.id != listId }
        assertFalse(newList.isFinished)
        assertFalse(newList.isArchived)
        assertNull(newList.purchaseDate)

        val newItems = database.shoppingListDao().getItemsForListSync(newList.id)
        assertTrue(newItems.isEmpty())
    }

    @Test
    fun forward_notEmpty_copiesItemsToNewList() = runBlocking {
        val listId = 2L
        database.shoppingListDao().insertShoppingList(
            makeList(listId, "Weekly Shop", startOfMonth(-1), isRecurring = true, isForwardEmpty = false)
        )
        database.shoppingListDao().insertShoppingListItem(makeItem(200L, listId, 20L, 7.0, checked = true))
        database.shoppingListDao().insertShoppingListItem(makeItem(201L, listId, 21L, 2.0, quantity = 3.0))

        repository.checkAndForwardRecurringLists()

        val allLists = repository.getAllShoppingListsOnce()
        assertEquals(2, allLists.size)
        assertTrue(allLists.first { it.id == listId }.isArchived)

        val newList = allLists.first { it.id != listId }
        val newItems = database.shoppingListDao().getItemsForListSync(newList.id)
        assertEquals(2, newItems.size)
        assertTrue(newItems.all { !it.isChecked })
        assertEquals(listOf(20L, 21L), newItems.map { it.productId }.sorted())
        assertEquals(7.0, newItems.find { it.productId == 20L }?.price)
        assertEquals(2.0, newItems.find { it.productId == 21L }?.price)
    }

    @Test
    fun forward_periodNotPassed_leavesUnchanged() = runBlocking {
        val listId = 3L
        database.shoppingListDao().insertShoppingList(
            makeList(listId, "Current List", System.currentTimeMillis(), isRecurring = true)
        )

        repository.checkAndForwardRecurringLists()

        val allLists = repository.getAllShoppingListsOnce()
        assertEquals(1, allLists.size)
        assertEquals(listId, allLists[0].id)
        assertFalse(allLists[0].isFinished)
        assertFalse(allLists[0].isArchived)
    }

    @Test
    fun forward_subscription_forwardsAndKeepsFinished() = runBlocking {
        val listId = 4L
        database.shoppingListDao().insertShoppingList(
            makeList(listId, "Monthly Bills", startOfMonth(-1), isRecurring = true, isSubscription = true, isFinished = true, isForwardEmpty = false)
        )
        database.shoppingListDao().insertShoppingListItem(makeItem(300L, listId, 30L, 15.0))

        repository.checkAndForwardRecurringLists()

        val allLists = repository.getAllShoppingListsOnce()
        assertEquals(2, allLists.size)

        val oldList = allLists.first { it.id == listId }
        assertTrue(oldList.isFinished)
        assertTrue(oldList.isArchived)

        val newList = allLists.first { it.id != listId }
        assertTrue(newList.isSubscription)
        assertTrue(newList.isFinished)
        assertFalse(newList.isArchived)

        val newItems = database.shoppingListDao().getItemsForListSync(newList.id)
        assertEquals(1, newItems.size)
        assertFalse(newItems[0].isChecked)
        assertEquals(30L, newItems[0].productId)
    }

    @Test
    fun forward_incomeRecurring_forwardsAsFinished() = runBlocking {
        val listId = 5L
        database.shoppingListDao().insertShoppingList(
            makeList(listId, "Salary", startOfMonth(-1), isRecurring = true, isIncome = true, isFinished = true, isForwardEmpty = false)
        )
        database.shoppingListDao().insertShoppingListItem(makeItem(400L, listId, 40L, 5000.0))

        repository.checkAndForwardRecurringLists()

        val allLists = repository.getAllShoppingListsOnce()
        assertEquals(2, allLists.size)
        assertTrue(allLists.first { it.id == listId }.isArchived)

        val newList = allLists.first { it.id != listId }
        assertTrue(newList.isIncome)
        assertTrue(newList.isFinished)
    }

    @Test
    fun forward_multiplePeriods_whileLoopCycles() = runBlocking {
        val listId = 6L
        database.shoppingListDao().insertShoppingList(
            makeList(listId, "Old Recurring", startOfMonth(-3), isRecurring = true)
        )

        repository.checkAndForwardRecurringLists()

        val allLists = repository.getAllShoppingListsOnce()
        assertTrue(allLists.size >= 2)

        val archivedLists = allLists.filter { it.isArchived }
        assertTrue(archivedLists.isNotEmpty())

        val activeLists = allLists.filter { !it.isArchived }
        assertEquals(1, activeLists.size)
        assertFalse(activeLists[0].isFinished)

        val allSorted = allLists.sortedBy { it.createDate }
        assertFalse(allSorted.last().isArchived)
    }

    @Test
    fun forward_alreadyArchived_notForwardedAgain() = runBlocking {
        val listId = 7L
        database.shoppingListDao().insertShoppingList(
            makeList(listId, "Archived List", startOfMonth(-1), isRecurring = true, isFinished = true, isArchived = true)
        )

        repository.checkAndForwardRecurringLists()

        val allLists = repository.getAllShoppingListsOnce()
        assertEquals(1, allLists.size)
        assertEquals(listId, allLists[0].id)
    }

    @Test
    fun forward_nonRecurring_notForwarded() = runBlocking {
        val listId = 8L
        database.shoppingListDao().insertShoppingList(
            makeList(listId, "One-time Purchase", startOfMonth(-1))
        )

        repository.checkAndForwardRecurringLists()

        val allLists = repository.getAllShoppingListsOnce()
        assertEquals(1, allLists.size)
        assertEquals(listId, allLists[0].id)
    }

    @Test
    fun forward_totalFromExplicitItemPrices() = runBlocking {
        val listId = 9L
        database.shoppingListDao().insertShoppingList(
            makeList(listId, "Price Test", startOfMonth(-1), isRecurring = true)
        )
        database.shoppingListDao().insertShoppingListItem(makeItem(500L, listId, 50L, 12.5))
        database.shoppingListDao().insertShoppingListItem(makeItem(501L, listId, 51L, 7.5, quantity = 2.0, checked = true))

        repository.checkAndForwardRecurringLists()

        val oldList = repository.getAllShoppingListsOnce().first { it.id == listId }
        assertEquals(20.0, oldList.finalTotal!!, 0.001)
    }

    @Test
    fun forward_totalUsesPriceFallbackWhenItemPriceIsNull() = runBlocking {
        val listId = 10L
        val productId = 60L

        database.productDao().insertProduct(
            ProductEntity(id = productId, name = "Fallback Product", barcode = "", picturePath = null)
        )
        database.priceDao().insertPrice(
            PriceEntity(id = 600L, productId = productId, storeId = null, value = 9.99, date = startOfMonth(-1))
        )

        database.shoppingListDao().insertShoppingList(
            makeList(listId, "Fallback Test", startOfMonth(-1), isRecurring = true)
        )
        database.shoppingListDao().insertShoppingListItem(makeItem(601L, listId, productId, null))

        repository.checkAndForwardRecurringLists()

        val oldList = repository.getAllShoppingListsOnce().first { it.id == listId }
        assertEquals(9.99, oldList.finalTotal!!, 0.001)
    }
}
