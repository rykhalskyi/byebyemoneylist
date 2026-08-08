package com.otakeeesen.byebyemoneylist.data.local

import com.otakeeesen.byebyemoneylist.data.getAllDescendantIds
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

data class CategorySpendingData(
    val monthTotal: Double,
    val overallTotal: Double,
    val categoryName: String,
    val categoryColor: Long
)

class DashboardRepository(private val database: AppDatabase) {

    private fun parseHexColor(colorStr: String?): Long {
        if (colorStr.isNullOrBlank()) return 0xFFFF6B6BL
        return try {
            android.graphics.Color.parseColor(colorStr).toLong() and 0xFFFFFFFFL
        } catch (e: Exception) {
            0xFFFF6B6BL
        }
    }

    suspend fun getCategorySpending(
        categoryId: Long,
        monthStart: Long,
        monthEnd: Long
    ): CategorySpendingData = withContext(Dispatchers.IO) {
        val allCategories = database.categoryDao().getAllCategoriesOnce()

        val catMap = allCategories.associateBy { it.id }
        val category = catMap[categoryId]
        val categoryName = category?.name ?: "Category #$categoryId"
        val categoryColor = parseHexColor(category?.color ?: CategoryColors.DEFAULT_COLOR)

        val targetCatIds = setOf(categoryId) + getAllDescendantIds(categoryId, allCategories)

        // Month total calculation
        val monthLists = database.shoppingListDao()
            .getFinishedListsInTimeRange(monthStart, monthEnd)
            .filter { !it.isIncome }

        val monthTotal = if (monthLists.isNotEmpty()) {
            val listIds = monthLists.map { it.id }
            val items = database.shoppingListDao().getItemsWithProductForListsSync(listIds)
            items.filter { it.productCategoryId != null && it.productCategoryId in targetCatIds }
                .sumOf { (it.itemPrice ?: it.price) * it.quantity - (it.discount ?: 0.0) }
        } else {
            0.0
        }

        // Overall total calculation
        val overallLists = database.shoppingListDao()
            .getFinishedListsInTimeRange(0L, Long.MAX_VALUE)
            .filter { !it.isIncome }

        val overallTotal = if (overallLists.isNotEmpty()) {
            val listIds = overallLists.map { it.id }
            val items = database.shoppingListDao().getItemsWithProductForListsSync(listIds)
            items.filter { it.productCategoryId != null && it.productCategoryId in targetCatIds }
                .sumOf { (it.itemPrice ?: it.price) * it.quantity - (it.discount ?: 0.0) }
        } else {
            0.0
        }

        CategorySpendingData(
            monthTotal = monthTotal,
            overallTotal = overallTotal,
            categoryName = categoryName,
            categoryColor = categoryColor
        )
    }

    suspend fun getSpentToday(): Double = withContext(Dispatchers.IO) {
        val now = LocalDate.now()
        val startOfToday = now.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfToday = now.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val lists = database.shoppingListDao()
            .getFinishedListsInTimeRange(startOfToday, endOfToday)
            .filter { !it.isIncome }

        if (lists.isEmpty()) return@withContext 0.0

        val listIds = lists.map { it.id }
        val items = database.shoppingListDao().getItemsWithProductForListsSync(listIds)
        val itemsByListId = items.groupBy { it.shoppingListId }

        var total = 0.0
        lists.forEach { list ->
            val listItems = itemsByListId[list.id]
            if (!listItems.isNullOrEmpty()) {
                total += listItems.sumOf { (it.itemPrice ?: it.price) * it.quantity - (it.discount ?: 0.0) }
            } else {
                total += (list.finalTotal ?: 0.0)
            }
        }
        total
    }

    suspend fun getThisMonthSpending(): Double = withContext(Dispatchers.IO) {
        val currentMonth = YearMonth.now()
        val startOfMonth = currentMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfMonth = currentMonth.atEndOfMonth().atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val lists = database.shoppingListDao()
            .getFinishedListsInTimeRange(startOfMonth, endOfMonth)
            .filter { !it.isIncome }

        if (lists.isEmpty()) return@withContext 0.0

        val listIds = lists.map { it.id }
        val items = database.shoppingListDao().getItemsWithProductForListsSync(listIds)
        val itemsByListId = items.groupBy { it.shoppingListId }

        var total = 0.0
        lists.forEach { list ->
            val listItems = itemsByListId[list.id]
            if (!listItems.isNullOrEmpty()) {
                total += listItems.sumOf { (it.itemPrice ?: it.price) * it.quantity - (it.discount ?: 0.0) }
            } else {
                total += (list.finalTotal ?: 0.0)
            }
        }
        total
    }

    suspend fun getLastMonthSpending(): Double = withContext(Dispatchers.IO) {
        val lastMonth = YearMonth.now().minusMonths(1)
        val startOfLastMonth = lastMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfLastMonth = lastMonth.atEndOfMonth().atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val lists = database.shoppingListDao()
            .getFinishedListsInTimeRange(startOfLastMonth, endOfLastMonth)
            .filter { !it.isIncome }

        if (lists.isEmpty()) return@withContext 0.0

        val listIds = lists.map { it.id }
        val items = database.shoppingListDao().getItemsWithProductForListsSync(listIds)
        val itemsByListId = items.groupBy { it.shoppingListId }

        var total = 0.0
        lists.forEach { list ->
            val listItems = itemsByListId[list.id]
            if (!listItems.isNullOrEmpty()) {
                total += listItems.sumOf { (it.itemPrice ?: it.price) * it.quantity - (it.discount ?: 0.0) }
            } else {
                total += (list.finalTotal ?: 0.0)
            }
        }
        total
    }
}
