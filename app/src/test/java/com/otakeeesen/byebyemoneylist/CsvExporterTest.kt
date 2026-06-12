package com.otakeeesen.byebyemoneylist

import com.otakeeesen.byebyemoneylist.data.ShoppingList
import com.otakeeesen.byebyemoneylist.data.PurchaseItem
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.util.CsvExporter
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExporterTest {

    @Test
    fun testExportToCsv_basicStructure() {
        val categories = listOf(
            CategoryEntity(id = 1L, name = "Food"),
            CategoryEntity(id = 2L, name = "Groceries", parentId = 1L),
            CategoryEntity(id = 3L, name = "Electronics")
        )

        val items = listOf(
            PurchaseItem(
                id = 101L,
                productId = 10L,
                name = "Milk",
                price = 2.50,
                quantity = 2.0,
                imageUrl = "",
                checked = true,
                categoryId = 2L
            ),
            PurchaseItem(
                id = 102L,
                productId = 11L,
                name = "Laptop, Pro",
                price = 1000.0,
                quantity = 1.0,
                imageUrl = "",
                checked = true,
                categoryId = 3L,
                discount = 100.0
            ),
            PurchaseItem(
                id = 103L,
                productId = 12L,
                name = "Bread",
                price = 1.80,
                quantity = 1.0,
                imageUrl = "",
                checked = false, // Unchecked item, shouldn't be included in bought items totals
                categoryId = 2L
            )
        )

        val list = ShoppingList(
            id = 1L,
            title = "Walmart Weekly",
            items = items,
            isFinished = true,
            finalTotal = 905.0,
            storeName = "Walmart",
            createDate = 1718049600000L, // 2024-06-10 20:00:00 UTC approximately
            purchaseDate = 1718049600000L,
            categories = emptyList(),
            position = 0,
            storeId = 10L
        )

        val csv = CsvExporter.exportToCsv(listOf(list), categories, "€")

        // Assert general structure and sections exist
        assertTrue(csv.contains("=== GENERAL SUMMARY ==="))
        assertTrue(csv.contains("=== SPENDING BY CATEGORY ==="))
        assertTrue(csv.contains("=== DETAILED PURCHASE LOG ==="))

        // Assert totals
        // Total spent: Milk: 2 * 2.50 = 5.00. Laptop: 1 * 1000.0 - 100 = 900.00. Total = 905.00
        assertTrue(csv.contains("Overall Spending (Sum of Items),905 €"))
        assertTrue(csv.contains("Total Items Purchased,3")) // 2 milk + 1 laptop

        // Assert category path output
        // Milk category path: "Food > Groceries"
        // Laptop category path: "Electronics"
        assertTrue(csv.contains("Food > Groceries,5,2,0.6%")) // spent=5, count=2, percent=5/905 = 0.55% -> 0.6%
        assertTrue(csv.contains("Electronics,900,1,99.4%")) // spent=900, count=1, percent=900/905 = 99.44% -> 99.4%

        // Assert purchase log items and CSV escaping
        // Laptop name has a comma so it should be escaped as "Laptop, Pro"
        assertTrue(csv.contains("\"Laptop, Pro\""))
    }
}
