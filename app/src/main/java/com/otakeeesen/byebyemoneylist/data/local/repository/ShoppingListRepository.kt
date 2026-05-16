package com.otakeeesen.byebyemoneylist.data.local.repository

import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository for shopping list database operations.
 */
class ShoppingListRepository(private val database: AppDatabase) {

    val allShoppingLists: Flow<List<ShoppingListEntity>> = database.shoppingListDao().getAllShoppingLists()

    fun getItemsForList(listId: Long): Flow<List<ShoppingListItemEntity>> {
        return database.shoppingListDao().getItemsForList(listId)
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
}
