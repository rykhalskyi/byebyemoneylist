package com.otakeeesen.byebyemoneylist.data.agent

import android.util.Log
import com.otakeeesen.byebyemoneylist.data.AdjustedItem
import com.otakeeesen.byebyemoneylist.data.computeAdjustedItems
import com.otakeeesen.byebyemoneylist.data.getAllDescendantIds
import com.otakeeesen.byebyemoneylist.data.UNCATEGORIZED_NAME
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.PriceRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ProductRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.StoreRepository
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AgentQueryExecutor(
    private val shoppingListRepository: ShoppingListRepository,
    private val categoryRepository: CategoryRepository,
    private val productRepository: ProductRepository,
    private val priceRepository: PriceRepository,
    private val storeRepository: StoreRepository,
    private val preferencesManager: PreferencesManager
) {
    private val categorySplitPattern = Regex("\\s*,\\s*|\\s+and\\s+|\\s*&\\s*")
    // Resolve product names: direct contains match + alias resolution
    private suspend fun resolveProductNames(userQueryName: String): Set<String> {
        val names = mutableSetOf(userQueryName)
        val aliasNames = productRepository.findProductNamesByAlias(userQueryName)
        names.addAll(aliasNames)
        return names
    }

    private fun matchesProduct(productName: String, names: Set<String>): Boolean {
        return names.any { productName.contains(it, ignoreCase = true) }
    }

    suspend fun execute(query: AgentQuery): AgentResult = withContext(Dispatchers.IO) {
        try {
            // 1. Resolve date boundaries
            val startMillis = query.startDate?.let { parseDateToMillis(it, startOfDay = true) } ?: 0L
            val endMillis = query.endDate?.let { parseDateToMillis(it, startOfDay = false) } ?: Long.MAX_VALUE

            // 2. Fetch reference tables needed for category resolution
            val allCategories = categoryRepository.getAllCategoriesOnce()
            val categoryIdMap = allCategories.associateBy { it.id }

            // 3. Compute ratio-adjusted items (single source of truth)
            val processedItems = computeAdjustedItems(
                startMillis, endMillis,
                shoppingListRepository, categoryRepository, storeRepository, preferencesManager
            )

            if (processedItems.isEmpty()) {
                val currency = preferencesManager.getCurrencySymbol() ?: "$"
                return@withContext when (query.action) {
                    AgentAction.GET_TOTAL_SPENT -> AgentResult.TotalAmount(0.0, currency, "spending")
                    AgentAction.GET_TOTAL_INCOME -> AgentResult.TotalAmount(0.0, currency, "income")
                    AgentAction.LIST_PURCHASES -> AgentResult.PurchaseList(emptyList())
                    AgentAction.GET_TOP_CATEGORIES -> AgentResult.TopItems(emptyList(), "category")
                    AgentAction.GET_TOP_STORES -> AgentResult.TopItems(emptyList(), "store")
                    AgentAction.GET_TOP_PRODUCTS -> AgentResult.TopItems(emptyList(), "product")
                    AgentAction.GET_PRODUCT_PRICE_HISTORY -> AgentResult.PriceHistory(query.productName ?: "Product", emptyList())
                    AgentAction.GET_CATEGORIES -> AgentResult.NamedList(emptyList(), "category")
                    AgentAction.GET_PRODUCTS -> AgentResult.NamedList(emptyList(), "product")
                    AgentAction.GET_STORES -> AgentResult.NamedList(emptyList(), "store")
                    AgentAction.GET_SPENT_BY_PRODUCT -> AgentResult.TopItems(emptyList(), "product")
                    AgentAction.GET_SPENT_BY_CATEGORY -> AgentResult.TopItems(emptyList(), "product")
                    AgentAction.REJECT_NOT_RELEVANT -> AgentResult.Error("Not relevant")
                }
            }

            // 4. Resolve category filtering if requested
            val targetCategoryIds: Set<Long>? = if (!query.categoryName.isNullOrBlank()) {
                val categoryParts = query.categoryName.split(categorySplitPattern)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                Log.d("AgentQueryExecutor", "Category filter parts: $categoryParts, available categories: ${allCategories.map { it.name }}")
                val matchedCats = allCategories.filter { cat ->
                    categoryParts.any { part -> cat.name.contains(part, ignoreCase = true) }
                }
                Log.d("AgentQueryExecutor", "Matched category names: ${matchedCats.map { it.name }}, ids: ${matchedCats.map { it.id }}")
                val ids = matchedCats.flatMap { cat ->
                    getAllDescendantIds(cat.id, allCategories) + cat.id
                }.toSet()
                Log.d("AgentQueryExecutor", "Final targetCategoryIds (incl descendants): $ids")
                ids
            } else null

            // Helper to convert AdjustedItem to AgentPurchaseItem
            fun AdjustedItem.toAgentPurchaseItem() = AgentPurchaseItem(
                productName = productName,
                quantity = quantity,
                price = if (quantity > 0.0) itemTotal / quantity else itemTotal,
                discount = discount,
                storeName = storeName,
                date = formatMillisToDate(dateMillis),
                categoryName = categoryName
            )

            // 5. Perform the requested Action
            val currency = preferencesManager.getCurrencySymbol() ?: "$"

            Log.d("AgentQueryExecutor", "processedItems count=${processedItems.size}, unique categories: ${processedItems.map { "${it.categoryName}(${it.categoryId})" }.distinct()}, isIncome=${processedItems.map { it.isIncome }.distinct()}")

            val actionResult: AgentResult = when (query.action) {
                AgentAction.GET_TOTAL_SPENT -> {
                    val resolvedProductNames = if (!query.productName.isNullOrBlank()) resolveProductNames(query.productName) else emptySet()
                    val filtered = processedItems.filter { item ->
                        !item.isIncome &&
                        (query.productName.isNullOrBlank() || matchesProduct(item.productName, resolvedProductNames)) &&
                        (targetCategoryIds == null || item.categoryId in targetCategoryIds) &&
                        (query.storeName.isNullOrBlank() || item.storeName?.contains(query.storeName, ignoreCase = true) == true)
                    }
                    val sum = filtered.sumOf { it.itemTotal }
                    val qtySum = filtered.sumOf { it.quantity }
                    AgentResult.TotalAmount(sum, currency, "spending", qtySum)
                }
                AgentAction.GET_TOTAL_INCOME -> {
                    val filtered = processedItems.filter { item ->
                        item.isIncome &&
                        (targetCategoryIds == null || item.categoryId in targetCategoryIds)
                    }
                    val sum = filtered.sumOf { it.itemTotal }
                    val qtySum = filtered.sumOf { it.quantity }
                    AgentResult.TotalAmount(sum, currency, "income", qtySum)
                }
                AgentAction.LIST_PURCHASES -> {
                    val resolvedProductNames = if (!query.productName.isNullOrBlank()) resolveProductNames(query.productName) else emptySet()
                    val filtered = processedItems.filter { item ->
                        !item.isIncome &&
                        (query.productName.isNullOrBlank() || matchesProduct(item.productName, resolvedProductNames)) &&
                        (targetCategoryIds == null || item.categoryId in targetCategoryIds) &&
                        (query.storeName.isNullOrBlank() || item.storeName?.contains(query.storeName, ignoreCase = true) == true)
                    }.sortedByDescending { it.dateMillis }

                    val purchaseItems = filtered.map { item ->
                        AgentPurchaseItem(
                            productName = item.productName,
                            quantity = item.quantity,
                            price = if (item.quantity > 0.0) item.itemTotal / item.quantity else item.itemTotal,
                            discount = item.discount,
                            storeName = item.storeName,
                            date = formatMillisToDate(item.dateMillis),
                            categoryName = item.categoryName
                        )
                    }
                    val limited = if (query.limit != null && query.limit > 0) purchaseItems.take(query.limit) else purchaseItems.take(50)
                    AgentResult.PurchaseList(limited)
                }
                AgentAction.GET_TOP_CATEGORIES -> {
                    val filtered = processedItems.filter { !it.isIncome }
                    val grouped = filtered.groupBy { item ->
                        // Resolve root category ID
                        var root: CategoryEntity? = item.categoryId?.let { categoryIdMap[it] }
                        while (root?.parentId != null) {
                            val parent = categoryIdMap[root.parentId] ?: break
                            root = parent
                        }
                        root?.name ?: UNCATEGORIZED_NAME
                    }
                    val list = grouped.map { (name, items) ->
                        AgentTopItem(
                            name = name,
                            totalSpent = items.sumOf { it.itemTotal },
                            quantity = items.sumOf { it.quantity }
                        )
                    }.sortedByDescending { it.totalSpent }

                    val limited = if (query.limit != null && query.limit > 0) list.take(query.limit) else list.take(5)
                    AgentResult.TopItems(limited, "category")
                }
                AgentAction.GET_TOP_STORES -> {
                    val filtered = processedItems.filter { !it.isIncome }
                    val grouped = filtered.groupBy { it.storeName ?: "Unknown Store" }
                    val list = grouped.map { (name, items) ->
                        AgentTopItem(
                            name = name,
                            totalSpent = items.sumOf { it.itemTotal },
                            quantity = items.sumOf { it.quantity }
                        )
                    }.sortedByDescending { it.totalSpent }

                    val limited = if (query.limit != null && query.limit > 0) list.take(query.limit) else list.take(5)
                    AgentResult.TopItems(limited, "store")
                }
                AgentAction.GET_TOP_PRODUCTS -> {
                    val filtered = processedItems.filter { !it.isIncome }
                    val grouped = filtered.groupBy { it.productName }
                    val list = grouped.map { (name, items) ->
                        AgentTopItem(
                            name = name,
                            totalSpent = items.sumOf { it.itemTotal },
                            quantity = items.sumOf { it.quantity }
                        )
                    }.sortedByDescending { it.totalSpent }

                    val limited = if (query.limit != null && query.limit > 0) list.take(query.limit) else list.take(5)
                    AgentResult.TopItems(limited, "product")
                }
                AgentAction.GET_PRODUCT_PRICE_HISTORY -> {
                    val searchName = query.productName ?: ""
                    val resolvedNames = if (searchName.isNotBlank()) resolveProductNames(searchName) else emptySet()
                    val filtered = processedItems.filter { item ->
                        !item.isIncome && (searchName.isBlank() || matchesProduct(item.productName, resolvedNames))
                    }.sortedBy { it.dateMillis }

                    val pricePoints = filtered.map { item ->
                        AgentPricePoint(
                            storeName = item.storeName,
                            price = if (item.quantity > 0.0) item.itemTotal / item.quantity else item.itemTotal,
                            date = formatMillisToDate(item.dateMillis)
                        )
                    }
                    AgentResult.PriceHistory(searchName, pricePoints)
                }
                AgentAction.GET_CATEGORIES -> {
                    val cats = categoryRepository.getAllCategoriesOnce() ?: emptyList()
                    AgentResult.NamedList(
                        items = cats.map { NamedItem(id = it.id, name = it.name) },
                        listType = "category"
                    )
                }
                AgentAction.GET_PRODUCTS -> {
                    val prods = productRepository.getAllProductsOnce() ?: emptyList()
                    AgentResult.NamedList(
                        items = prods.map { NamedItem(id = it.id, name = it.name) },
                        listType = "product"
                    )
                }
                AgentAction.GET_STORES -> {
                    val stores = storeRepository.getAllStoresOnce() ?: emptyList()
                    AgentResult.NamedList(
                        items = stores.map { NamedItem(id = it.id, name = it.name) },
                        listType = "store"
                    )
                }
                AgentAction.GET_SPENT_BY_PRODUCT -> {
                    val resolvedProductNames = if (!query.productName.isNullOrBlank()) resolveProductNames(query.productName) else emptySet()
                    val filtered = processedItems.filter { item ->
                        !item.isIncome &&
                        (query.productName.isNullOrBlank() || matchesProduct(item.productName, resolvedProductNames)) &&
                        (targetCategoryIds == null || item.categoryId in targetCategoryIds)
                    }
                    Log.d("AgentQueryExecutor", "GET_SPENT_BY_PRODUCT: totalItems=${processedItems.size}, filtered=${filtered.size}, targetCategoryIds=$targetCategoryIds")
                    val grouped = filtered.groupBy { it.productName }
                    val list = grouped.map { (name, items) ->
                        AgentTopItem(
                            name = name,
                            totalSpent = items.sumOf { it.itemTotal },
                            quantity = items.sumOf { it.quantity },
                            items = items.map { it.toAgentPurchaseItem() }
                        )
                    }.sortedByDescending { it.totalSpent }
                    AgentResult.TopItems(list, "product")
                }
                AgentAction.GET_SPENT_BY_CATEGORY -> {
                    val filtered = processedItems.filter { item ->
                        !item.isIncome &&
                        (targetCategoryIds == null || item.categoryId in targetCategoryIds)
                    }
                    Log.d("AgentQueryExecutor", "GET_SPENT_BY_CATEGORY: totalItems=${processedItems.size}, filtered=${filtered.size}, targetCategoryIds=$targetCategoryIds")
                    val grouped = filtered.groupBy { it.productName }
                    val list = grouped.map { (name, items) ->
                        AgentTopItem(
                            name = name,
                            totalSpent = items.sumOf { it.itemTotal },
                            quantity = items.sumOf { it.quantity },
                            items = items.map { it.toAgentPurchaseItem() }
                        )
                    }.sortedByDescending { it.totalSpent }
                    val limited = if (query.limit != null && query.limit > 0) list.take(query.limit) else list.take(20)
                    AgentResult.TopItems(limited, "product")
                }
                AgentAction.REJECT_NOT_RELEVANT -> AgentResult.Error("Not relevant")
            }
            actionResult
        } catch (e: Exception) {
            Log.e("AgentQueryExecutor", "Execution error", e)
            AgentResult.Error(e.message ?: "Failed to execute query")
        }
    }

    private fun parseDateToMillis(dateStr: String, startOfDay: Boolean): Long {
        return try {
            val localDate = LocalDate.parse(dateStr)
            val zonedDateTime = if (startOfDay) {
                localDate.atStartOfDay(ZoneId.systemDefault())
            } else {
                localDate.atTime(23, 59, 59, 999_999_999).atZone(ZoneId.systemDefault())
            }
            zonedDateTime.toInstant().toEpochMilli()
        } catch (e: Exception) {
            if (startOfDay) 0L else Long.MAX_VALUE
        }
    }

    private fun formatMillisToDate(millis: Long): String {
        return try {
            val instant = Instant.ofEpochMilli(millis)
            val localDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
            localDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: Exception) {
            ""
        }
    }

}
