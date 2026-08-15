package com.otakeeesen.byebyemoneylist.data

data class ProductAggregate(
    val productId: Long,
    val name: String,
    val categoryId: Long?,
    val quantity: Double,
    val totalSpent: Double,
    val items: List<AdjustedItem>
)

/**
 * Groups adjusted items by product id, aggregating quantity and sign-adjusted
 * spending (income contributes negatively). Single source of truth for
 * per-product totals — used by Analytics product stats and the agent tools.
 */
fun computeProductAggregates(items: List<AdjustedItem>): List<ProductAggregate> {
    val byProduct = linkedMapOf<Long, MutableList<AdjustedItem>>()
    items.forEach { item ->
        byProduct.getOrPut(item.productId) { mutableListOf() }.add(item)
    }
    return byProduct.map { (productId, list) ->
        ProductAggregate(
            productId = productId,
            name = list.first().productName,
            categoryId = list.first().categoryId,
            quantity = list.sumOf { it.quantity },
            totalSpent = list.sumOf { if (it.isIncome) -it.itemTotal else it.itemTotal },
            items = list
        )
    }
}

fun computeProductStats(items: List<AdjustedItem>): List<ProductStat> =
    computeProductAggregates(items).map {
        ProductStat(
            productId = it.productId,
            name = it.name,
            quantity = it.quantity,
            totalSpent = it.totalSpent,
            categoryId = it.categoryId
        )
    }

fun filterProductStats(
    stats: List<ProductStat>,
    targetCategoryIds: Set<Long>?,
    searchQuery: String = ""
): List<ProductStat> =
    stats.filter { stat ->
        val matchesSearch = stat.name.contains(searchQuery, ignoreCase = true)
        val matchesCategory = targetCategoryIds == null || stat.categoryId in targetCategoryIds
        matchesSearch && matchesCategory && stat.totalSpent > 0
    }.sortedByDescending { it.totalSpent }
