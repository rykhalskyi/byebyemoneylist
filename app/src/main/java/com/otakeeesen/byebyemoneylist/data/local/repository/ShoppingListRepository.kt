package com.otakeeesen.byebyemoneylist.data.local.repository

import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.dao.ShoppingListItemWithProduct
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListItemEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import kotlinx.coroutines.flow.Flow

class ShoppingListRepository(private val database: AppDatabase) {

    val allShoppingLists: Flow<List<ShoppingListEntity>> = database.shoppingListDao().getAllShoppingLists()

    val allStores: Flow<List<StoreEntity>> = database.storeDao().getAllStores()

    suspend fun getAllStoresOnce(): List<StoreEntity> {
        return database.storeDao().getAllStoresOnce()
    }

    suspend fun getStoreByName(name: String): StoreEntity? {
        return database.storeDao().getStoreByName(name)
    }

    suspend fun insertStore(store: StoreEntity) {
        database.storeDao().insertStore(store)
    }

    fun getItemsForList(listId: Long): Flow<List<ShoppingListItemEntity>> {
        return database.shoppingListDao().getItemsForList(listId)
    }

    fun getAllItemsWithProduct(): Flow<List<ShoppingListItemWithProduct>> {
        return database.shoppingListDao().getAllItemsWithProduct()
    }

    suspend fun insertShoppingList(shoppingList: ShoppingListEntity) {
        database.shoppingListDao().insertShoppingList(shoppingList)
    }

    suspend fun updateShoppingList(shoppingList: ShoppingListEntity) {
        database.shoppingListDao().updateShoppingList(shoppingList)
    }

    suspend fun deleteShoppingList(shoppingList: ShoppingListEntity) {
        database.shoppingListDao().deleteShoppingList(shoppingList)
    }

    suspend fun insertShoppingListItem(item: ShoppingListItemEntity) {
        database.shoppingListDao().insertShoppingListItem(item)
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
