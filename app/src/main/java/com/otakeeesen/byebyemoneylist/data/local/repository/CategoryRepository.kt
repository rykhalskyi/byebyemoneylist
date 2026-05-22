package com.otakeeesen.byebyemoneylist.data.local.repository

import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryColors
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

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
        database.categoryDao().insertCategory(category)
    }

    suspend fun updateCategory(category: CategoryEntity) {
        database.categoryDao().updateCategory(category)
    }

    suspend fun deleteCategory(category: CategoryEntity) {
        database.categoryDao().deleteCategory(category)
    }

    private fun generateId(): Long = System.currentTimeMillis()
}
