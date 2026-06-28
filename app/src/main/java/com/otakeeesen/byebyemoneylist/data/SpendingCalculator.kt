package com.otakeeesen.byebyemoneylist.data

import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.dao.ShoppingListItemWithProduct
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.StoreRepository
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager

const val UNKNOWN_PRODUCT_NAME = "Unknown"
const val UNCATEGORIZED_NAME = "Uncategorized"

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

    val allCategories = categoryRepository.getAllCategoriesOnce()
    val categoryIdMap = allCategories.associateBy { it.id }
    val allStores = storeRepository.getAllStoresOnce()
    val storeNameMap = allStores.associate { it.id to it.name }
    val rule = preferencesManager.getActualPriceRule()

    val results = mutableListOf<AdjustedItem>()

    lists.forEach { list ->
        val listItems = allItems.filter { it.shoppingListId == list.id }
        val domainList = list.toDomain(listItems)
        val listPriceActual = domainList.calculateActualPrice(rule)
        val listStoreName = storeNameMap[list.storeId]

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

    return results
}

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
