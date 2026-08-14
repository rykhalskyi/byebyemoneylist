package com.otakeeesen.byebyemoneylist.data.local

import com.otakeeesen.byebyemoneylist.data.getAllDescendantIds
import com.otakeeesen.byebyemoneylist.data.sumExpenses
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
    val categoryColor: Long,
    val categoryEmoji: String?
)

class DashboardRepository(
    private val database: AppDatabase,
    private val preferencesManager: PreferencesManager,
) {

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
        val monthTotal = calculateCategoryTotal(monthStart, monthEnd, targetCatIds)

        // Overall total calculation
        val overallTotal = calculateCategoryTotal(0L, Long.MAX_VALUE, targetCatIds)

        CategorySpendingData(
            monthTotal = monthTotal,
            overallTotal = overallTotal,
            categoryName = categoryName,
            categoryColor = categoryColor,
            categoryEmoji = category?.emoji
        )
    }

    private fun calculateCategoryTotal(
        startTime: Long,
        endTime: Long,
        targetCatIds: Set<Long>
    ): Double {
        val lists = database.shoppingListDao()
            .getFinishedListsInTimeRange(startTime, endTime)
            .filter { !it.isIncome }

        if (lists.isEmpty()) return 0.0

        val listIds = lists.map { it.id }
        val items = database.shoppingListDao().getItemsWithProductForListsSync(listIds)
        val itemsByListId = items.groupBy { it.shoppingListId }

        val listsWithItems = itemsByListId.keys.toSet()
        val listsWithoutItems = lists.filter { it.id !in listsWithItems }

        // Product-based totals: items whose product belongs to the category or a descendant
        var total = items
            .filter { it.productCategoryId != null && it.productCategoryId in targetCatIds }
            .sumOf { (it.itemPrice ?: it.price) * it.quantity - (it.discount ?: 0.0) }

        // Lists without products: include their finalTotal if tagged with the category or a descendant
        if (listsWithoutItems.isNotEmpty()) {
            val crossRefs = database.shoppingListDao()
                .getCategoryCrossRefsForListsSync(listsWithoutItems.map { it.id })
            val taggedListIds = crossRefs
                .filter { it.categoryId in targetCatIds }
                .map { it.shoppingListId }
                .toSet()

            total += listsWithoutItems
                .filter { it.id in taggedListIds }
                .sumOf { it.finalTotal ?: 0.0 }
        }

        return total
    }

    suspend fun getSpentToday(): Double = withContext(Dispatchers.IO) {
        val now = LocalDate.now()
        val startOfToday = now.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfToday = now.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val lists = database.shoppingListDao()
            .getFinishedListsInTimeRange(startOfToday, endOfToday)

        if (lists.isEmpty()) return@withContext 0.0

        val items = database.shoppingListDao().getItemsWithProductForListsSync(lists.map { it.id })
        sumExpenses(lists, items, preferencesManager.getActualPriceRule())
    }

    suspend fun getThisMonthSpending(): Double = withContext(Dispatchers.IO) {
        val currentMonth = YearMonth.now()
        val startOfMonth = currentMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfMonth = currentMonth.atEndOfMonth().atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val lists = database.shoppingListDao()
            .getFinishedListsInTimeRange(startOfMonth, endOfMonth)

        if (lists.isEmpty()) return@withContext 0.0

        val items = database.shoppingListDao().getItemsWithProductForListsSync(lists.map { it.id })
        sumExpenses(lists, items, preferencesManager.getActualPriceRule())
    }

    suspend fun getLastMonthSpending(): Double = withContext(Dispatchers.IO) {
        val lastMonth = YearMonth.now().minusMonths(1)
        val startOfLastMonth = lastMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfLastMonth = lastMonth.atEndOfMonth().atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val lists = database.shoppingListDao()
            .getFinishedListsInTimeRange(startOfLastMonth, endOfLastMonth)

        if (lists.isEmpty()) return@withContext 0.0

        val items = database.shoppingListDao().getItemsWithProductForListsSync(lists.map { it.id })
        sumExpenses(lists, items, preferencesManager.getActualPriceRule())
    }
}
