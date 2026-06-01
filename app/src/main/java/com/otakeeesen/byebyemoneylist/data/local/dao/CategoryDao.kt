package com.otakeeesen.byebyemoneylist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategoriesOnce(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    fun getCategoryById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    fun getCategoryByName(name: String): CategoryEntity?

    @Query("""
        SELECT c.* FROM categories c
        JOIN store_category_cross_ref sccr ON c.id = sccr.categoryId
        WHERE sccr.storeId = :storeId
    """)
    fun getCategoriesByStoreId(storeId: Long): Flow<List<CategoryEntity>>

    @Query("""
        SELECT c.* FROM categories c
        JOIN store_category_cross_ref sccr ON c.id = sccr.categoryId
        WHERE sccr.storeId = :storeId
    """)
    fun getCategoriesByStoreIdOnce(storeId: Long): List<CategoryEntity>

    @Query("""
        SELECT c.* FROM categories c
        JOIN shopping_list_category_cross_ref slccr ON c.id = slccr.categoryId
        WHERE slccr.shoppingListId = :shoppingListId
    """)
    fun getCategoriesByShoppingListId(shoppingListId: Long): Flow<List<CategoryEntity>>

    @Query("""
        SELECT c.* FROM categories c
        JOIN shopping_list_category_cross_ref slccr ON c.id = slccr.categoryId
        WHERE slccr.shoppingListId = :shoppingListId
    """)
    fun getCategoriesByShoppingListIdOnce(shoppingListId: Long): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCategory(category: CategoryEntity)

    @Update
    fun updateCategory(category: CategoryEntity)

    @Delete
    fun deleteCategory(category: CategoryEntity)
}
