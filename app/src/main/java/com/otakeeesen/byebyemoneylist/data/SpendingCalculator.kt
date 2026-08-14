package com.otakeeesen.byebyemoneylist.data

import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.dao.ShoppingListItemWithProduct
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.StoreRepository
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import kotlin.math.abs

const val UNKNOWN_PRODUCT_NAME = "Unknown"
const val UNCATEGORIZED_NAME = "Uncategorized"
const val QUICK_PURCHASE_PRODUCT_NAME = "Quick Purchase"

data class AdjustedItem(
    val productName: String,
    val productId: Long,
    val quantity: Double,
    val itemTotal: Double,
    val listPriceActual: Double,
    val discount: Double?,
    val listId: Long,
    val storeId: Long?,
    val storeName: String?,
    val dateMillis: Long,
    val categoryId: Long?,
    val categoryName: String?,
    val isIncome: Boolean
)

fun ShoppingListEntity.toDomain(items: List<ShoppingListItemWithProduct>): ShoppingList {
    return ShoppingList(
        id = this.id,
        title = this.name,
        items = items.map {
            PurchaseItem(
                id = it.id,
                productId = it.productId,
                name = it.productName ?: UNKNOWN_PRODUCT_NAME,
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

suspend fun computeAdjustedItems(
    startMillis: Long,
    endMillis: Long,
    shoppingListRepository: ShoppingListRepository,
    categoryRepository: CategoryRepository,
    storeRepository: StoreRepository,
    preferencesManager: PreferencesManager
): List<AdjustedItem> {
    val lists = shoppingListRepository.getFinishedListsInTimeRange(startMillis, endMillis)
    if (lists.isEmpty()) return emptyList()

    val listIds = lists.map { it.id }
    val allItems = shoppingListRepository.getItemsWithProductForListsSync(listIds) ?: return emptyList()
    val crossRefs = shoppingListRepository.getCategoryCrossRefsForListsSync(listIds)
    val listCategoryMap = crossRefs.groupBy { it.shoppingListId }.mapValues { entry -> entry.value.map { it.categoryId } }

    val allCategories = categoryRepository.getAllCategoriesOnce()
    val categoryIdMap = allCategories.associateBy { it.id }
    val allStores = storeRepository.getAllStoresOnce()
    val storeNameMap = allStores.associate { it.id to it.name }
    val rule = preferencesManager.getActualPriceRule()

    val results = mutableListOf<AdjustedItem>()

    lists.forEach { list ->
        val listItems = allItems.filter { it.shoppingListId == list.id }
        val listStoreName = storeNameMap[list.storeId]

        if (listItems.isEmpty() && list.finalTotal != null) {
            val categoryIds = listCategoryMap[list.id] ?: emptyList()
            val categoryId = categoryIds.firstOrNull()
            val catName = categoryId?.let { categoryIdMap[it]?.name } ?: UNCATEGORIZED_NAME

            results.add(
                AdjustedItem(
                    productName = QUICK_PURCHASE_PRODUCT_NAME,
                    productId = 0L,
                    quantity = 1.0,
                    itemTotal = list.finalTotal,
                    listPriceActual = list.finalTotal,
                    discount = null,
                    listId = list.id,
                    storeId = list.storeId,
                    storeName = listStoreName,
                    dateMillis = list.purchaseDate ?: list.createDate,
                    categoryId = categoryId,
                    categoryName = catName,
                    isIncome = list.isIncome
                )
            )
        } else {
            val domainList = list.toDomain(listItems)
            val listPriceActual = domainList.calculateActualPrice(rule)

            listItems.forEach { item ->
                val itemTotal = (item.itemPrice ?: item.price) * item.quantity - (item.discount ?: 0.0)
                val catName = item.productCategoryId?.let { categoryIdMap[it]?.name } ?: UNCATEGORIZED_NAME

                results.add(
                    AdjustedItem(
                        productName = item.productName ?: UNKNOWN_PRODUCT_NAME,
                        productId = item.productId,
                        quantity = item.quantity,
                        itemTotal = itemTotal,
                        listPriceActual = listPriceActual,
                        discount = item.discount,
                        listId = list.id,
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
    }

    return results
}

/**
 * Canonical monthly expense sum: for every non-income list, the absolute value of
 * its actual price (respecting the user's PURCHASE_PRICE / BIGGER_VALUE rule).
 *
 * Callers are responsible for providing only the lists that should be included
 * (e.g. finished lists within the target time range).
 */
fun sumExpenses(lists: List<ShoppingList>, rule: String): Double =
    lists.filter { !it.isIncome }.sumOf { abs(it.calculateActualPrice(rule)) }

/** Convenience overload that converts [ShoppingListEntity]s (plus their items) to domain before summing. */
fun sumExpenses(
    lists: List<ShoppingListEntity>,
    items: List<ShoppingListItemWithProduct>,
    rule: String
): Double {
    val itemsByListId = items.groupBy { it.shoppingListId }
    return sumExpenses(lists.map { it.toDomain(itemsByListId[it.id].orEmpty()) }, rule)
}

/**
 * Sum of monthly expenses from already-adjusted items: each list contributes
 * |listPriceActual| exactly once.
 */
fun sumExpenses(adjustedItems: List<AdjustedItem>): Double =
    adjustedItems.filter { !it.isIncome }
        .groupBy { it.listId }
        .values
        .sumOf { abs(it.first().listPriceActual) }

fun getAllDescendantIds(parentId: Long, allCategories: List<CategoryEntity>): List<Long> {
    val descendants = mutableListOf<Long>()
    val toProcess = ArrayDeque(listOf(parentId))
    while (toProcess.isNotEmpty()) {
        val currentId = toProcess.removeFirst()
        val children = allCategories.filter { it.parentId == currentId }.map { it.id }
        descendants.addAll(children)
        toProcess.addAll(children)
    }
    return descendants
}
