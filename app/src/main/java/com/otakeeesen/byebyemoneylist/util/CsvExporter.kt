package com.otakeeesen.byebyemoneylist.util

import com.otakeeesen.byebyemoneylist.data.ShoppingList
import com.otakeeesen.byebyemoneylist.data.PurchaseItem
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    fun exportToCsv(
        lists: List<ShoppingList>,
        allCategories: List<CategoryEntity>,
        currencySymbol: String
    ): String {
        val categoriesMap = allCategories.associateBy { it.id }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val exportDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        // Group lists into finished and finished item processing
        val finishedLists = lists.filter { it.isFinished }

        // Gather all items across finished lists
        // Note: For subscriptions we treat all items as bought, for regular lists we check `checked` status.
        val boughtItems = lists.flatMap { list ->
            list.items.filter { it.checked || list.isSubscription }.map { item ->
                val listDate = if (list.purchaseDate != null && list.purchaseDate > 0) list.purchaseDate else list.createDate
                val unitPrice = item.price ?: 0.0
                val qty = item.quantity
                val disc = item.discount ?: 0.0
                val total = (qty * unitPrice) - disc
                
                ItemExportData(
                    date = listDate,
                    listName = list.title,
                    storeName = list.storeName ?: "None",
                    productName = item.customName ?: item.name,
                    categoryId = item.categoryId,
                    quantity = qty,
                    unitPrice = unitPrice,
                    discount = disc,
                    totalPrice = maxOf(0.0, total),
                    isSubscription = list.isSubscription
                )
            }
        }

        // 1. General Calculations
        val totalSpent = boughtItems.sumOf { it.totalPrice }
        val totalItemsCount = boughtItems.sumOf { it.quantity }
        val totalListsCount = lists.size
        val finishedListsCount = finishedLists.size

        // 2. Category Calculations
        // Map category ID to its total spending and item counts
        val categorySpending = mutableMapOf<Long?, Double>()
        val categoryCounts = mutableMapOf<Long?, Double>()

        boughtItems.forEach { item ->
            val catId = item.categoryId
            categorySpending[catId] = (categorySpending[catId] ?: 0.0) + item.totalPrice
            categoryCounts[catId] = (categoryCounts[catId] ?: 0.0) + item.quantity
        }

        // Sort categories by spending descending
        val sortedCategories = categorySpending.entries.sortedByDescending { it.value }

        // Build CSV string
        val sb = StringBuilder()

        // --- SECTION 1: GENERAL SUMMARY ---
        sb.append("=== GENERAL SUMMARY ===\n")
        sb.append("Metric,Value\n")
        sb.append("Overall Spending (Sum of Items),${formatDouble(totalSpent)} $currencySymbol\n")
        sb.append("Total Items Purchased,${formatDouble(totalItemsCount)}\n")
        sb.append("Total Shopping Lists,${totalListsCount}\n")
        sb.append("Finished Shopping Lists,${finishedListsCount}\n")
        sb.append("Export Date,${exportDateFormat.format(Date())}\n")
        sb.append("\n")

        // --- SECTION 2: SPENDING BY CATEGORY ---
        sb.append("=== SPENDING BY CATEGORY ===\n")
        sb.append("Category Path,Total Spent ($currencySymbol),Item Count,% of Total Spending\n")
        
        sortedCategories.forEach { (catId, spent) ->
            val path = getCategoryPath(catId, categoriesMap)
            val count = categoryCounts[catId] ?: 0.0
            val percentage = if (totalSpent > 0) (spent / totalSpent) * 100 else 0.0
            val percentString = String.format(Locale.US, "%.1f%%", percentage)
            sb.append("${escapeCsv(path)},${formatDouble(spent)},${formatDouble(count)},$percentString\n")
        }
        sb.append("\n")

        // --- SECTION 3: DETAILED PURCHASE LOG ---
        sb.append("=== DETAILED PURCHASE LOG ===\n")
        sb.append("Date,List Name,Store,Product Name,Category Path,Quantity,Unit Price ($currencySymbol),Discount ($currencySymbol),Total Price ($currencySymbol),Status\n")

        // Sort items by date descending
        val sortedItems = boughtItems.sortedByDescending { it.date }
        sortedItems.forEach { item ->
            val dateStr = dateFormat.format(Date(item.date))
            val path = getCategoryPath(item.categoryId, categoriesMap)
            val status = if (item.isSubscription) "Subscription" else "Bought"
            
            sb.append("${escapeCsv(dateStr)},")
            sb.append("${escapeCsv(item.listName)},")
            sb.append("${escapeCsv(item.storeName)},")
            sb.append("${escapeCsv(item.productName)},")
            sb.append("${escapeCsv(path)},")
            sb.append("${formatDouble(item.quantity)},")
            sb.append("${formatDouble(item.unitPrice)},")
            sb.append("${formatDouble(item.discount)},")
            sb.append("${formatDouble(item.totalPrice)},")
            sb.append("$status\n")
        }

        return sb.toString()
    }

    private fun getCategoryPath(categoryId: Long?, categoriesMap: Map<Long, CategoryEntity>): String {
        if (categoryId == null) return "Uncategorized"
        val path = mutableListOf<String>()
        var current = categoriesMap[categoryId]
        while (current != null) {
            path.add(0, current.name)
            current = current.parentId?.let { categoriesMap[it] }
        }
        return if (path.isEmpty()) "Uncategorized" else path.joinToString(" > ")
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }

    private fun formatDouble(value: Double): String {
        return if (value % 1.0 == 0.0) {
            String.format(Locale.US, "%.0f", value)
        } else {
            String.format(Locale.US, "%.2f", value)
        }
    }

    private data class ItemExportData(
        val date: Long,
        val listName: String,
        val storeName: String,
        val productName: String,
        val categoryId: Long?,
        val quantity: Double,
        val unitPrice: Double,
        val discount: Double,
        val totalPrice: Double,
        val isSubscription: Boolean
    )
}
