package com.otakeeesen.byebyemoneylist.data

import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductStatsCalculatorTest {

    private val calculator = ProductStatsCalculator()

    // ── Helper factories ───────────────────────────────────────────────

    private fun adjustedItem(
        productId: Long,
        productName: String,
        quantity: Double,
        itemTotal: Double,
        isIncome: Boolean = false,
        categoryId: Long? = null,
        listId: Long = 1L,
    ) = AdjustedItem(
        productName = productName,
        productId = productId,
        quantity = quantity,
        itemTotal = itemTotal,
        listPriceActual = if (isIncome) itemTotal else -itemTotal,
        discount = null,
        listId = listId,
        storeId = null,
        storeName = null,
        dateMillis = 1_000_000L,
        categoryId = categoryId,
        categoryName = null,
        isIncome = isIncome
    )

    private fun category(
        id: Long,
        name: String,
        parentId: Long? = null
    ) = CategoryEntity(id = id, name = name, color = "#FFF", parentId = parentId)

    // ── computeProductStats ────────────────────────────────────────────

    @Test
    fun `computeProductStats single item returns product stat with correct values`() {
        val items = listOf(
            adjustedItem(productId = 1L, productName = "Milk", quantity = 2.0, itemTotal = 5.0)
        )
        val result = calculator.computeProductStats(items)
        assertEquals(1, result.size)
        val stat = result.first()
        assertEquals(1L, stat.productId)
        assertEquals("Milk", stat.name)
        assertEquals(2.0, stat.quantity, 0.001)
        assertEquals(5.0, stat.totalSpent, 0.001)
    }

    @Test
    fun `computeProductStats multiple items for same product aggregates quantity and totalSpent`() {
        val items = listOf(
            adjustedItem(productId = 1L, productName = "Milk", quantity = 2.0, itemTotal = 5.0),
            adjustedItem(productId = 1L, productName = "Milk", quantity = 3.0, itemTotal = 7.5, listId = 2L)
        )
        val result = calculator.computeProductStats(items)
        assertEquals(1, result.size)
        val stat = result.first()
        assertEquals("Milk", stat.name)
        assertEquals(5.0, stat.quantity, 0.001)
        assertEquals(12.5, stat.totalSpent, 0.001)
    }

    @Test
    fun `computeProductStats income item produces negative totalSpent`() {
        val items = listOf(
            adjustedItem(productId = 1L, productName = "Salary", quantity = 1.0, itemTotal = 1000.0, isIncome = true)
        )
        val result = calculator.computeProductStats(items)
        assertEquals(1, result.size)
        assertEquals(-1000.0, result.first().totalSpent, 0.001)
    }

    @Test
    fun `computeProductStats mixed income and expense nets correctly`() {
        val items = listOf(
            adjustedItem(productId = 1L, productName = "Item", quantity = 2.0, itemTotal = 10.0, isIncome = false),
            adjustedItem(productId = 1L, productName = "Item", quantity = 1.0, itemTotal = 5.0, isIncome = true)
        )
        val result = calculator.computeProductStats(items)
        assertEquals(1, result.size)
        // expense: +10.0, income: -5.0 → net = 5.0
        assertEquals(5.0, result.first().totalSpent, 0.001)
    }

    @Test
    fun `computeProductStats multiple products returns all`() {
        val items = listOf(
            adjustedItem(productId = 1L, productName = "Milk", quantity = 2.0, itemTotal = 5.0),
            adjustedItem(productId = 2L, productName = "Bread", quantity = 1.0, itemTotal = 2.0),
            adjustedItem(productId = 3L, productName = "Eggs", quantity = 12.0, itemTotal = 4.0, listId = 2L)
        )
        val result = calculator.computeProductStats(items)
        assertEquals(3, result.size)
    }

    @Test
    fun `computeProductStats empty list returns empty`() {
        val result = calculator.computeProductStats(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `computeProductStats preserves categoryId from item`() {
        val items = listOf(
            adjustedItem(productId = 1L, productName = "Milk", quantity = 1.0, itemTotal = 3.0, categoryId = 5L)
        )
        val result = calculator.computeProductStats(items)
        assertEquals(5L, result.first().categoryId)
    }

    // ── expandCategoryIds ──────────────────────────────────────────────

    @Test
    fun `expandCategoryIds category with no children returns just its own id`() {
        val cats = listOf(category(1L, "Food"))
        val result = calculator.expandCategoryIds(setOf(1L), cats)
        assertEquals(setOf(1L), result)
    }

    @Test
    fun `expandCategoryIds category with direct children includes children ids`() {
        val cats = listOf(
            category(1L, "Food"),
            category(2L, "Dairy", parentId = 1L),
            category(3L, "Bakery", parentId = 1L)
        )
        val result = calculator.expandCategoryIds(setOf(1L), cats)
        assertEquals(setOf(1L, 2L, 3L), result)
    }

    @Test
    fun `expandCategoryIds category with grandchildren includes all descendants`() {
        val cats = listOf(
            category(1L, "Food"),
            category(2L, "Dairy", parentId = 1L),
            category(3L, "Cheese", parentId = 2L),
            category(4L, "Yogurt", parentId = 2L)
        )
        val result = calculator.expandCategoryIds(setOf(1L), cats)
        assertEquals(setOf(1L, 2L, 3L, 4L), result)
    }

    @Test
    fun `expandCategoryIds multiple input ids unions all expanded sets`() {
        val cats = listOf(
            category(1L, "Food"),
            category(2L, "Dairy", parentId = 1L),
            category(3L, "Drinks"),
            category(4L, "Soda", parentId = 3L)
        )
        val result = calculator.expandCategoryIds(setOf(1L, 3L), cats)
        assertEquals(setOf(1L, 2L, 3L, 4L), result)
    }

    @Test
    fun `expandCategoryIds empty input returns empty set`() {
        val cats = listOf(category(1L, "Food"))
        val result = calculator.expandCategoryIds(emptySet(), cats)
        assertTrue(result.isEmpty())
    }

    // ── filterProductStats ─────────────────────────────────────────────

    @Test
    fun `filterProductStats no filter returns all products with positive totalSpent sorted descending`() {
        val stats = listOf(
            ProductStat(1L, "Milk", 2.0, 5.0, 1L),
            ProductStat(2L, "Bread", 1.0, 10.0, 1L),
            ProductStat(3L, "Eggs", 12.0, 3.0, 2L)
        )
        val result = calculator.filterProductStats(stats, targetCategoryIds = null)
        assertEquals(3, result.size)
        assertEquals(10.0, result[0].totalSpent, 0.001)
        assertEquals(5.0, result[1].totalSpent, 0.001)
        assertEquals(3.0, result[2].totalSpent, 0.001)
    }

    @Test
    fun `filterProductStats category filter keeps only matching products`() {
        val stats = listOf(
            ProductStat(1L, "Milk", 2.0, 5.0, categoryId = 1L),
            ProductStat(2L, "Bread", 1.0, 10.0, categoryId = 1L),
            ProductStat(3L, "Eggs", 12.0, 3.0, categoryId = 2L)
        )
        val result = calculator.filterProductStats(stats, targetCategoryIds = setOf(1L))
        assertEquals(2, result.size)
        assertTrue(result.all { it.categoryId == 1L })
    }

    @Test
    fun `filterProductStats with null categoryId is filtered correctly`() {
        val stats = listOf(
            ProductStat(1L, "Milk", 2.0, 5.0, categoryId = 1L),
            ProductStat(2L, "Unknown", 1.0, 3.0, categoryId = null)
        )
        val result = calculator.filterProductStats(stats, targetCategoryIds = setOf(1L))
        assertEquals(1, result.size)
        assertEquals(1L, result.first().categoryId)
    }

    @Test
    fun `filterProductStats with null targetCategoryIds includes null categoryId items`() {
        val stats = listOf(
            ProductStat(1L, "Milk", 2.0, 5.0, categoryId = 1L),
            ProductStat(2L, "Unknown", 1.0, 3.0, categoryId = null)
        )
        val result = calculator.filterProductStats(stats, targetCategoryIds = null)
        assertEquals(2, result.size)
    }

    @Test
    fun `filterProductStats search query keeps only products whose name contains the query`() {
        val stats = listOf(
            ProductStat(1L, "Milk", 2.0, 5.0, 1L),
            ProductStat(2L, "Bread", 1.0, 10.0, 1L),
            ProductStat(3L, "Milk Chocolate", 1.0, 3.0, 2L)
        )
        val result = calculator.filterProductStats(stats, targetCategoryIds = null, searchQuery = "Milk")
        assertEquals(2, result.size)
        assertTrue(result.all { it.name.contains("Milk", ignoreCase = true) })
    }

    @Test
    fun `filterProductStats search query is case insensitive`() {
        val stats = listOf(
            ProductStat(1L, "milk", 2.0, 5.0, 1L),
            ProductStat(2L, "Bread", 1.0, 10.0, 1L)
        )
        val result = calculator.filterProductStats(stats, targetCategoryIds = null, searchQuery = "MILK")
        assertEquals(1, result.size)
        assertEquals("milk", result.first().name)
    }

    @Test
    fun `filterProductStats combined category and search filters together`() {
        val stats = listOf(
            ProductStat(1L, "Milk", 2.0, 5.0, categoryId = 1L),
            ProductStat(2L, "Bread", 1.0, 10.0, categoryId = 1L),
            ProductStat(3L, "Milk Chocolate", 1.0, 3.0, categoryId = 2L)
        )
        val result = calculator.filterProductStats(
            stats = stats,
            targetCategoryIds = setOf(1L),
            searchQuery = "Milk"
        )
        assertEquals(1, result.size)
        assertEquals(1L, result.first().productId)
    }

    @Test
    fun `filterProductStats excludes products with zero totalSpent`() {
        val stats = listOf(
            ProductStat(1L, "Milk", 2.0, 5.0, 1L),
            ProductStat(2L, "Free", 1.0, 0.0, 1L),
            ProductStat(3L, "WriteOff", 1.0, -3.0, 1L)
        )
        val result = calculator.filterProductStats(stats, targetCategoryIds = null)
        assertEquals(1, result.size)
        assertEquals(1L, result.first().productId)
    }

    @Test
    fun `filterProductStats returns empty when no products match`() {
        val stats = listOf(
            ProductStat(1L, "Milk", 2.0, 5.0, categoryId = 1L)
        )
        val result = calculator.filterProductStats(stats, targetCategoryIds = setOf(99L))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `filterProductStats returns empty when search matches nothing`() {
        val stats = listOf(
            ProductStat(1L, "Milk", 2.0, 5.0, 1L)
        )
        val result = calculator.filterProductStats(stats, targetCategoryIds = null, searchQuery = "Nonexistent")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `filterProductStats empty input returns empty`() {
        val result = calculator.filterProductStats(emptyList(), targetCategoryIds = null)
        assertTrue(result.isEmpty())
    }

    // ── End-to-end: compute + expand + filter ──────────────────────────

    @Test
    fun `end to end compute expand and filter matches UI product stats tab behavior`() {
        val cats = listOf(
            category(1L, "Food"),
            category(2L, "Dairy", parentId = 1L),
            category(3L, "Bakery", parentId = 1L),
            category(4L, "Drinks", parentId = 1L),
            category(5L, "Soda", parentId = 4L)
        )

        val items = listOf(
            // Dairy products
            adjustedItem(productId = 10L, productName = "Milk", quantity = 2.0, itemTotal = 5.0, categoryId = 2L),
            adjustedItem(productId = 10L, productName = "Milk", quantity = 1.0, itemTotal = 2.5, categoryId = 2L, listId = 2L),
            adjustedItem(productId = 11L, productName = "Cheese", quantity = 1.0, itemTotal = 8.0, categoryId = 2L),
            // Bakery products
            adjustedItem(productId = 12L, productName = "Bread", quantity = 1.0, itemTotal = 3.0, categoryId = 3L),
            // Soda (child of Drinks)
            adjustedItem(productId = 13L, productName = "Cola", quantity = 6.0, itemTotal = 12.0, categoryId = 5L),
            // Uncategorized
            adjustedItem(productId = 14L, productName = "Unknown", quantity = 1.0, itemTotal = 2.0, categoryId = null),
            // Income (should be excluded by totalSpent > 0)
            adjustedItem(productId = 15L, productName = "Stock Sale", quantity = 1.0, itemTotal = 100.0, categoryId = null, isIncome = true)
        )

        val stats = calculator.computeProductStats(items)
        assertEquals(6, stats.size)

        // User selects "Food" (id=1) → should match Dairy (2), Bakery (3), Drinks (4), Soda (5)
        val expandedIds = calculator.expandCategoryIds(setOf(1L), cats)
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), expandedIds)

        val filtered = calculator.filterProductStats(stats, targetCategoryIds = expandedIds)
        // Should return: Milk (7.5), Cheese (8), Bread (3), Cola (12)
        // Excludes: Unknown (no category), Stock Sale (income → -100 totalSpent → excluded)
        assertEquals(4, filtered.size)
        assertEquals(listOf(12.0, 8.0, 7.5, 3.0), filtered.map { it.totalSpent })

        // With search query "col" → only Cola
        val searched = calculator.filterProductStats(stats, targetCategoryIds = expandedIds, searchQuery = "col")
        assertEquals(1, searched.size)
        assertEquals("Cola", searched.first().name)
    }
}
