package com.otakeeesen.byebyemoneylist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Delete
import androidx.room.Query
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListItemEntity
import kotlinx.coroutines.flow.Flow

data class ShoppingListItemWithProduct(
    val id: Long,
    val shoppingListId: Long,
    val productId: Long,
    val quantity: Int,
    val isChecked: Boolean,
    val position: Int,
    val productName: String?,
    val productPicturePath: String?,
    val price: Double,
)

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
    
    @Query("""
        SELECT sli.id, sli.shoppingListId, sli.productId, sli.quantity, sli.isChecked, sli.position,
               p.name AS productName, p.picturePath AS productPicturePath,
               COALESCE((SELECT pr.value FROM prices pr WHERE pr.productId = sli.productId ORDER BY pr.date DESC LIMIT 1), 0.0) AS price
        FROM shopping_list_items sli
        LEFT JOIN products p ON sli.productId = p.id
        ORDER BY sli.position ASC
    """)
    fun getAllItemsWithProduct(): Flow<List<ShoppingListItemWithProduct>>

    @Insert
    fun insertShoppingListItem(item: ShoppingListItemEntity)
    
    @Update
    fun updateShoppingListItem(item: ShoppingListItemEntity)
    
    @Delete
    fun deleteShoppingListItem(item: ShoppingListItemEntity)

    @Query("UPDATE shopping_list_items SET isChecked = :isChecked WHERE id = :id")
    fun updateItemChecked(id: Long, isChecked: Boolean)

    @Query("UPDATE shopping_list_items SET position = :position WHERE id = :id")
    fun updateItemPosition(id: Long, position: Int)

    @Query("DELETE FROM shopping_list_items WHERE id = :id")
    fun deleteShoppingListItemById(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM shopping_list_items WHERE shoppingListId = :listId")
    fun getMaxPositionForList(listId: Long): Int
}