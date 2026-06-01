package com.otakeeesen.byebyemoneylist.data.local.repository

import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryColors
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class CategoryRepository(private val database: AppDatabase) {

    val allCategories: Flow<List<CategoryEntity>> = database.categoryDao().getAllCategories()

    fun getAllCategoriesOnce(): List<CategoryEntity> {
        return database.categoryDao().getAllCategoriesOnce()
    }

    suspend fun getOrCreate(name: String): Long {
        val existing = database.categoryDao().getCategoryByName(name)
        if (existing != null) return existing.id

        val id = generateId()
        database.categoryDao().insertCategory(CategoryEntity(id = id, name = name, color = CategoryColors.DEFAULT_COLOR))
        return id
    }

    suspend fun insertCategory(category: CategoryEntity) {
        if (category.parentId != null && isCircularDependency(category.id, category.parentId)) {
            throw IllegalArgumentException("Circular dependency detected")
        }
        database.categoryDao().insertCategory(category)
    }

    suspend fun updateCategory(category: CategoryEntity) {
        if (category.parentId != null && isCircularDependency(category.id, category.parentId)) {
            throw IllegalArgumentException("Circular dependency detected")
        }
        database.categoryDao().updateCategory(category)
    }

    suspend fun deleteCategory(category: CategoryEntity) {
        database.categoryDao().deleteCategory(category)
    }

    suspend fun isCircularDependency(categoryId: Long, potentialParentId: Long): Boolean {
        if (categoryId == potentialParentId) return true
        var currentParentId: Long? = potentialParentId
        while (currentParentId != null) {
            if (currentParentId == categoryId) return true
            val parent = database.categoryDao().getCategoryById(currentParentId)
            currentParentId = parent?.parentId
        }
        return false
    }

    fun getCategoriesByStoreId(storeId: Long): Flow<List<CategoryEntity>> {
        return database.categoryDao().getCategoriesByStoreId(storeId)
    }

    suspend fun getCategoriesByStoreIdOnce(storeId: Long): List<CategoryEntity> {
        return withContext(Dispatchers.IO) {
            database.categoryDao().getCategoriesByStoreIdOnce(storeId)
        }
    }

    fun getCategoriesByShoppingListId(shoppingListId: Long): Flow<List<CategoryEntity>> {
        return database.categoryDao().getCategoriesByShoppingListId(shoppingListId)
    }

    suspend fun getCategoriesByShoppingListIdOnce(shoppingListId: Long): List<CategoryEntity> {
        return withContext(Dispatchers.IO) {
            database.categoryDao().getCategoriesByShoppingListIdOnce(shoppingListId)
        }
    }

    private fun generateId(): Long = System.currentTimeMillis()
}
