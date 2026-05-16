package com.otakeeesen.byebyemoneylist.data.local.repository

import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
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
        database.categoryDao().insertCategory(CategoryEntity(id = id, name = name))
        return id
    }

    private fun generateId(): Long = System.currentTimeMillis()
}
