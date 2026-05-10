package com.otakeeesen.byebyemoneylist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Delete
import androidx.room.Query
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for shopping list operations.
 */
@Dao
interface ShoppingListDao {
    
    @Query("SELECT * FROM shopping_lists")
    fun getAllShoppingLists(): Flow<List<ShoppingListEntity>>
    
    @Query("SELECT * FROM shopping_lists WHERE id = :id")
    fun getShoppingListById(id: Long): ShoppingListEntity
    
    @Insert
    fun insertShoppingList(shoppingList: ShoppingListEntity)
    
    @Update
    fun updateShoppingList(shoppingList: ShoppingListEntity)
    
    @Delete
    fun deleteShoppingList(shoppingList: ShoppingListEntity)
    
    @Query("SELECT * FROM shopping_list_items WHERE shoppingListId = :listId")
    fun getItemsForList(listId: Long): Flow<List<ShoppingListItemEntity>>
    
    @Query("SELECT * FROM shopping_list_items WHERE id = :id")
    fun getShoppingListItemById(id: Long): ShoppingListItemEntity
    
    @Insert
    fun insertShoppingListItem(item: ShoppingListItemEntity)
    
    @Update
    fun updateShoppingListItem(item: ShoppingListItemEntity)
    
    @Delete
    fun deleteShoppingListItem(item: ShoppingListItemEntity)
}