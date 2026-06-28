package com.otakeeesen.byebyemoneylist.data

import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity

class ProductStatsCalculator {

    fun computeProductStats(items: List<AdjustedItem>): List<ProductStat> {
        val productStatMap = mutableMapOf<Long, ProductStat>()
        items.forEach { item ->
            val itemTotal = item.itemTotal
            val existing = productStatMap[item.productId]
            if (existing == null) {
                productStatMap[item.productId] = ProductStat(
                    productId = item.productId,
                    name = item.productName,
                    quantity = item.quantity,
                    totalSpent = if (item.isIncome) -itemTotal else itemTotal,
                    categoryId = item.categoryId
                )
            } else {
                productStatMap[item.productId] = existing.copy(
                    quantity = existing.quantity + item.quantity,
                    totalSpent = existing.totalSpent + (if (item.isIncome) -itemTotal else itemTotal)
                )
            }
        }
        return productStatMap.values.toList()
    }

    fun expandCategoryIds(categoryIds: Set<Long>, allCategories: List<CategoryEntity>): Set<Long> {
        return categoryIds.flatMap { id ->
            getAllDescendantIds(id, allCategories) + id
        }.toSet()
    }

    fun filterProductStats(
        stats: List<ProductStat>,
        targetCategoryIds: Set<Long>?,
        searchQuery: String = ""
    ): List<ProductStat> {
        return stats.filter { stat ->
            val matchesSearch = stat.name.contains(searchQuery, ignoreCase = true)
            val matchesCategory = targetCategoryIds == null || stat.categoryId in targetCategoryIds
            matchesSearch && matchesCategory && stat.totalSpent > 0
        }.sortedByDescending { it.totalSpent }
    }
}
