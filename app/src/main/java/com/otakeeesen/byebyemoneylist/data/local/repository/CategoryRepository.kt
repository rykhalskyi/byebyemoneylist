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

    suspend fun createDefaultCategories(context: android.content.Context) {
        withContext(Dispatchers.IO) {
            val categories = listOf(
                Triple(com.otakeeesen.byebyemoneylist.R.string.def_cat_supermarket, CategoryColors.GREEN, listOf(
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_bakery to CategoryColors.YELLOW,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_dairy to CategoryColors.YELLOW,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_meat to CategoryColors.RED,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_seafood to CategoryColors.BLUE,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_cereals_muesli to CategoryColors.ORANGE,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_produce to CategoryColors.GREEN,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_frozen to CategoryColors.BLUE,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_beverages to CategoryColors.BLUE,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_snacks to CategoryColors.ORANGE,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_pantry to CategoryColors.TEAL
                )),
                Triple(com.otakeeesen.byebyemoneylist.R.string.def_cat_health_beauty, CategoryColors.DEFAULT_COLOR, listOf(
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_personal_care to CategoryColors.PURPLE,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_pharmacy to CategoryColors.PURPLE
                )),
                Triple(com.otakeeesen.byebyemoneylist.R.string.def_cat_household, CategoryColors.DEFAULT_COLOR, listOf(
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_cleaning to CategoryColors.TEAL,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_paper_goods to CategoryColors.TEAL,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_kitchen to CategoryColors.TEAL,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_laundry to CategoryColors.TEAL
                )),
                Triple(com.otakeeesen.byebyemoneylist.R.string.def_cat_automotive, CategoryColors.PURPLE, listOf(
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_fuel to CategoryColors.RED,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_car_maintenance to CategoryColors.ORANGE
                )),
                Triple(com.otakeeesen.byebyemoneylist.R.string.def_cat_services, CategoryColors.DEFAULT_COLOR, listOf(
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_utilities to CategoryColors.BLUE,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_rent to CategoryColors.BLUE,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_subscriptions to CategoryColors.PURPLE
                )),
                Triple(com.otakeeesen.byebyemoneylist.R.string.def_cat_lifestyle, CategoryColors.DEFAULT_COLOR, listOf(
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_restaurants to CategoryColors.ORANGE,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_entertainment to CategoryColors.ORANGE
                ))
            )

            var baseId = System.currentTimeMillis()
            categories.forEach { (parentResId, parentColor, children) ->
                val parentName = context.getString(parentResId)
                val parentId = baseId++
                database.categoryDao().insertCategory(
                    CategoryEntity(id = parentId, name = parentName, color = parentColor, parentId = null)
                )

                children.forEach { (childResId, color) ->
                    val childName = context.getString(childResId)
                    database.categoryDao().insertCategory(
                        CategoryEntity(id = baseId++, name = childName, color = color, parentId = parentId)
                    )
                }
            }
        }
    }

    private fun generateId(): Long = System.currentTimeMillis()
}
