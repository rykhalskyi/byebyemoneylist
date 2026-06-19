package com.otakeeesen.byebyemoneylist.data.agent

import android.util.Log
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.*
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import com.otakeeesen.byebyemoneylist.data.ShoppingList
import com.otakeeesen.byebyemoneylist.data.PurchaseItem
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

            // 2. Fetch required reference tables
            val allCategories = categoryRepository.getAllCategoriesOnce()
            val categoryIdMap = allCategories.associateBy { it.id }
            val categoryNameMap = allCategories.associate { it.id to it.name }.toMutableMap().apply {
                put(-1L, "Uncategorized")
            }

            val allStores = storeRepository.getAllStoresOnce()
            val storeNameMap = allStores.associate { it.id to it.name }

            // 3. Fetch finished lists and items in time range
            val lists = shoppingListRepository.getFinishedListsInTimeRange(startMillis, endMillis)
            if (lists.isEmpty()) {
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
                    AgentAction.GET_SPENT_BY_CATEGORY -> AgentResult.TopItems(emptyList(), "category")
                    AgentAction.REJECT_NOT_RELEVANT -> AgentResult.Error("Not relevant")
                }
            }

            val listIds = lists.map { it.id }
            val allItems = shoppingListRepository.getItemsWithProductForListsSync(listIds) ?: emptyList()

            // 4. Resolve category filtering if requested
            val targetCategoryIds = if (!query.categoryName.isNullOrBlank()) {
                val matchedCats = allCategories.filter { it.name.contains(query.categoryName, ignoreCase = true) }
                val ids = matchedCats.flatMap { cat ->
                    getAllDescendantIds(cat.id, allCategories) + cat.id
                }.toSet()
                ids
            } else null

            // Helper to convert Entity to Domain ShoppingList object for calculation
            val rule = preferencesManager.getActualPriceRule()
            fun ShoppingListEntity.toDomain(items: List<com.otakeeesen.byebyemoneylist.data.local.dao.ShoppingListItemWithProduct>): ShoppingList {
                return ShoppingList(
                    id = this.id,
                    title = this.name,
                    items = items.map {
                        PurchaseItem(
                            id = it.id,
                            productId = it.productId,
                            name = it.productName ?: "Unknown",
                            price = it.itemPrice ?: it.price,
                            quantity = it.quantity,
                            imageUrl = it.productPicturePath ?: "",
                            checked = it.isChecked,
                            position = it.position,
                            productStatus = it.productStatus,
                            isSubscription = it.productIsSubscription,
                            discount = it.discount,
                            customName = it.customName,
                            categoryId = it.productCategoryId,
                            isFavorite = it.productIsFavorite
                        )
                    },
                    isFinished = this.isFinished,
                    finalTotal = this.finalTotal,
                    storeName = null,
                    createDate = this.createDate,
                    categories = emptyList(),
                    position = this.position,
                    storeId = this.storeId,
                    purchaseDate = this.purchaseDate,
                    isRecurring = this.isRecurring,
                    recurringPeriod = this.recurringPeriod,
                    isForwardEmpty = this.isForwardEmpty,
                    isArchived = this.isArchived,
                    isSubscription = this.isSubscription,
                    isIncome = this.isIncome
                )
            }

            // 5. Build processed item data
            data class ProcessedItem(
                val productName: String,
                val productId: Long,
                val quantity: Double,
                val itemTotal: Double,
                val discount: Double?,
                val storeId: Long?,
                val storeName: String?,
                val dateMillis: Long,
                val categoryId: Long?,
                val categoryName: String?,
                val isIncome: Boolean
            )

            val processedItems = mutableListOf<ProcessedItem>()

            lists.forEach { list ->
                val listItems = allItems.filter { it.shoppingListId == list.id }
                val domainList = list.toDomain(listItems)
                val listPriceActual = domainList.calculateActualPrice(rule)
                val itemsSum = listItems.sumOf { (it.itemPrice ?: it.price) * it.quantity - (it.discount ?: 0.0) }
                val ratio = if (itemsSum != 0.0) Math.abs(listPriceActual) / Math.abs(itemsSum) else 1.0

                val listStoreName = storeNameMap[list.storeId]

                listItems.forEach { item ->
                    val rawItemTotal = (item.itemPrice ?: item.price) * item.quantity - (item.discount ?: 0.0)
                    val itemTotal = rawItemTotal * ratio
                    val catName = item.productCategoryId?.let { categoryIdMap[it]?.name } ?: "Uncategorized"

                    processedItems.add(
                        ProcessedItem(
                            productName = item.productName ?: "Unknown",
                            productId = item.productId,
                            quantity = item.quantity,
                            itemTotal = itemTotal,
                            discount = item.discount,
                            storeId = list.storeId,
                            storeName = listStoreName,
                            dateMillis = list.purchaseDate ?: list.createDate,
                            categoryId = item.productCategoryId,
                            categoryName = catName,
                            isIncome = list.isIncome
                        )
                    )
                }
            }

            // 6. Perform the requested Action
            val currency = preferencesManager.getCurrencySymbol() ?: "$"

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
                        root?.name ?: "Uncategorized"
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
                    val filtered = processedItems.filter { !it.isIncome }
                    val grouped = filtered.groupBy { it.productName }
                    val list = grouped.map { (name, items) ->
                        AgentTopItem(
                            name = name,
                            totalSpent = items.sumOf { it.itemTotal },
                            quantity = items.sumOf { it.quantity }
                        )
                    }.sortedByDescending { it.totalSpent }
                    AgentResult.TopItems(list, "product")
                }
                AgentAction.GET_SPENT_BY_CATEGORY -> {
                    val filtered = processedItems.filter { !it.isIncome }
                    val grouped = filtered.groupBy { it.categoryName ?: "Uncategorized" }
                    val list = grouped.map { (name, items) ->
                        AgentTopItem(
                            name = name,
                            totalSpent = items.sumOf { it.itemTotal },
                            quantity = items.sumOf { it.quantity }
                        )
                    }.sortedByDescending { it.totalSpent }
                    AgentResult.TopItems(list, "category")
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

    private fun getAllDescendantIds(parentId: Long, allCategories: List<CategoryEntity>): List<Long> {
        val descendants = mutableListOf<Long>()
        val toProcess = mutableListOf(parentId)
        while (toProcess.isNotEmpty()) {
            val currentId = toProcess.removeAt(0)
            val children = allCategories.filter { it.parentId == currentId }.map { it.id }
            descendants.addAll(children)
            toProcess.addAll(children)
        }
        return descendants
    }
}
