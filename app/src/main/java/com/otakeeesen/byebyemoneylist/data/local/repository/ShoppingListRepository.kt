package com.otakeeesen.byebyemoneylist.data.local.repository

import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.dao.ShoppingListItemWithProduct
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductAliasEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListCategoryCrossRef
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListItemEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreCategoryCrossRef
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.ui.components.ScannedItem
import kotlinx.coroutines.flow.Flow

class ShoppingListRepository(private val database: AppDatabase) {

    suspend fun processPurchase(
        listId: Long?,
        listName: String?,
        storeName: String,
        price: Double,
        items: List<ScannedItem> = emptyList(),
        productRepository: ProductRepository,
        priceRepository: PriceRepository,
        isChecked: Boolean = true
    ) {
        // 1. Match or Create Store
        val sid = if (storeName.isNotBlank()) {
            val existingStore = getStoreByName(storeName) ?:
            getAllStoresOnce().find { it.receiptName == storeName }

            if (existingStore != null) {
                existingStore.id
            } else {
                val id = generateId()
                insertStore(StoreEntity(id = id, name = storeName, logoPath = null, receiptName = storeName))
                id
            }
        } else null

        // 2. Resolve target list
        val targetListId = listId ?: if (!listName.isNullOrBlank()) {
            val nid = generateId()
            insertShoppingList(ShoppingListEntity(id = nid, name = listName, createDate = System.currentTimeMillis(), purchaseDate = System.currentTimeMillis(), storeId = sid, isFinished = true, finalTotal = price))
            nid
        } else null

        if (targetListId != null) {
            if (items.isEmpty()) {
                // Manual entry with only total price
                insertShoppingListItem(ShoppingListItemEntity(id = generateId(), shoppingListId = targetListId, productId = 0L, quantity = 1, isChecked = isChecked, position = 0))
            } else {
                // Process items with smart matching
                val currentProducts = productRepository.getAllProductsOnce()
                items.forEachIndexed { i, item ->
                    val pid = if (item.productId != null && item.productId != 0L) {
                        item.productId
                    } else {
                        val bestAlias = productRepository.findBestAliasMatch(item.name, sid)
                        if (bestAlias != null) {
                            bestAlias.productId
                        } else {
                            // Try exact name match in products
                            val existingProduct = currentProducts.find { it.name.equals(item.name, ignoreCase = true) }
                            if (existingProduct != null) {
                                // Save as new alias for future matching
                                productRepository.insertAlias(ProductAliasEntity(id = generateId() + i + 500, productId = existingProduct.id, aliasName = item.name, storeId = sid))
                                existingProduct.id
                            } else {
                                // Truly new product - mark as "added"
                                val newPid = generateId() + i
                                productRepository.insertProduct(ProductEntity(id = newPid, name = item.name, barcode = "", picturePath = null, category = "General", status = "added", changedAt = System.currentTimeMillis()))
                                // Save alias
                                productRepository.insertAlias(ProductAliasEntity(id = generateId() + i + 500, productId = newPid, aliasName = item.name, storeId = sid))
                                newPid
                            }
                        }
                    }
                    // Save price and update changedAt
                    priceRepository.upsertPriceForProduct(pid, sid, item.price)

                    insertShoppingListItem(ShoppingListItemEntity(id = generateId() + i + 1000, shoppingListId = targetListId, productId = pid, quantity = item.quantity.toInt(), isChecked = isChecked, price = item.price, position = i))
                }
            }
        }
    }

    private fun generateId(): Long = System.currentTimeMillis()

    suspend fun getAllShoppingListsOnce(): List<ShoppingListEntity> {
        return database.shoppingListDao().getAllShoppingListsSynchronous()
    }

    val allShoppingLists: Flow<List<ShoppingListEntity>> = database.shoppingListDao().getAllShoppingLists()

    val allStores: Flow<List<StoreEntity>> = database.storeDao().getAllStores()

    suspend fun getAllStoresOnce(): List<StoreEntity> {
        return database.storeDao().getAllStoresOnce()
    }

    suspend fun getStoreByName(name: String): StoreEntity? {
        return database.storeDao().getStoreByName(name)
    }

    suspend fun insertStore(store: StoreEntity, categoryIds: List<Long> = emptyList()) {
        database.storeDao().insertStore(store)
        database.storeDao().deleteCategoriesForStore(store.id)
        categoryIds.forEach { categoryId ->
            database.storeDao().insertStoreCategoryCrossRef(StoreCategoryCrossRef(store.id, categoryId))
        }
    }

    fun getItemsForList(listId: Long): Flow<List<ShoppingListItemEntity>> {
        return database.shoppingListDao().getItemsForList(listId)
    }

    fun getAllItemsWithProduct(): Flow<List<ShoppingListItemWithProduct>> {
        return database.shoppingListDao().getAllItemsWithProduct()
    }

    fun getAllShoppingListCategoryCrossRefs(): Flow<List<ShoppingListCategoryCrossRef>> {
        return database.shoppingListDao().getAllShoppingListCategoryCrossRefs()
    }

    suspend fun getShoppingListById(id: Long): ShoppingListEntity? {
        return database.shoppingListDao().getShoppingListById(id)
    }

    suspend fun insertShoppingList(shoppingList: ShoppingListEntity, categoryIds: List<Long> = emptyList()) {
        database.shoppingListDao().insertShoppingList(shoppingList)
        syncCategories(shoppingList.id, categoryIds)
    }

    suspend fun updateShoppingList(shoppingList: ShoppingListEntity, categoryIds: List<Long> = emptyList()) {
        database.shoppingListDao().updateShoppingList(shoppingList)
        syncCategories(shoppingList.id, categoryIds)
    }

    private fun syncCategories(shoppingListId: Long, categoryIds: List<Long>) {
        database.shoppingListDao().deleteCategoriesForShoppingList(shoppingListId)
        categoryIds.forEach { categoryId ->
            database.shoppingListDao().insertShoppingListCategoryCrossRef(ShoppingListCategoryCrossRef(shoppingListId, categoryId))
        }
    }

    suspend fun deleteShoppingList(shoppingList: ShoppingListEntity) {
        database.shoppingListDao().deleteShoppingList(shoppingList)
    }

    suspend fun insertShoppingListItem(item: ShoppingListItemEntity) {
        database.shoppingListDao().insertShoppingListItem(item)
    }

    suspend fun updateShoppingListItem(item: ShoppingListItemEntity) {
        database.shoppingListDao().updateShoppingListItem(item)
    }

    suspend fun checkAndForwardRecurringLists() {
        val allLists = getAllShoppingListsOnce()
        val currentTime = System.currentTimeMillis()

        allLists.filter { it.isRecurring && !it.isFinished }.forEach { list ->
            var listToForward = list
            while (currentTime >= getEndOfPeriod(listToForward.createDate, listToForward.recurringPeriod)) {
                val endOfPeriod = getEndOfPeriod(listToForward.createDate, listToForward.recurringPeriod)
                
                // 1. Calculate total for the finished list
                val items = database.shoppingListDao().getItemsForListSync(listToForward.id)
                val total = items.sumOf { item ->
                    item.price ?: run {
                        // Fallback to latest price from database
                        database.priceDao().getLatestPriceForProduct(item.productId)?.value ?: 0.0
                    }
                }

                // 2. Finish current list
                val updatedList = listToForward.copy(
                    isFinished = true,
                    purchaseDate = endOfPeriod - 1000,
                    finalTotal = total
                )
                database.shoppingListDao().updateShoppingList(updatedList)

                // 3. Create new list
                val newListId = generateId()
                val newList = listToForward.copy(
                    id = newListId,
                    createDate = endOfPeriod,
                    purchaseDate = null,
                    isFinished = false,
                    finalTotal = null,
                    position = database.shoppingListDao().getMaxListPosition() + 1
                )
                database.shoppingListDao().insertShoppingList(newList)

                // 4. Sync categories
                val categoryIds = database.shoppingListDao().getCategoriesForShoppingListSync(listToForward.id)
                syncCategories(newListId, categoryIds)

                // 5. Copy items if needed
                if (!listToForward.isForwardEmpty) {
                    items.forEachIndexed { index, item ->
                        database.shoppingListDao().insertShoppingListItem(
                            item.copy(
                                id = generateId() + index + 100,
                                shoppingListId = newListId,
                                isChecked = false
                            )
                        )
                    }
                }
                
                listToForward = newList
            }
        }
    }

    private fun getEndOfPeriod(startTime: Long, period: String): Long {
        val ldt = java.time.Instant.ofEpochMilli(startTime).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
        val endLdt = when (period) {
            "WEEK" -> ldt.plusWeeks(1).with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).withHour(0).withMinute(0).withSecond(0).withNano(0)
            "MONTH" -> ldt.plusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
            "YEAR" -> ldt.plusYears(1).withDayOfYear(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
            else -> ldt.plusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
        }
        val endMillis = endLdt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        return if (endMillis <= startTime) {
            // Ensure we move forward at least one period if the logic results in same or past time
            when (period) {
                "WEEK" -> ldt.plusWeeks(1).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                "MONTH" -> ldt.plusMonths(1).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                "YEAR" -> ldt.plusYears(1).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                else -> ldt.plusMonths(1).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        } else {
            endMillis
        }
    }

    suspend fun getShoppingListItemById(id: Long): ShoppingListItemEntity? {
        return database.shoppingListDao().getShoppingListItemById(id)
    }

    suspend fun updateItemChecked(id: Long, isChecked: Boolean) {
        database.shoppingListDao().updateItemChecked(id, isChecked)
    }

    suspend fun updateItemPosition(id: Long, position: Int) {
        database.shoppingListDao().updateItemPosition(id, position)
    }

    suspend fun getMaxPositionForList(listId: Long): Int {
        return database.shoppingListDao().getMaxPositionForList(listId)
    }

    suspend fun updateListPosition(id: Long, position: Int) {
        database.shoppingListDao().updateShoppingListPosition(id, position)
    }

    suspend fun getMaxListPosition(): Int {
        return database.shoppingListDao().getMaxListPosition()
    }

    suspend fun deleteShoppingListItemAndReturn(id: Long): ShoppingListItemEntity? {
        val item = database.shoppingListDao().getShoppingListItemById(id)
        if (item != null) {
            database.shoppingListDao().deleteShoppingListItem(item)
        }
        return item
    }
}
