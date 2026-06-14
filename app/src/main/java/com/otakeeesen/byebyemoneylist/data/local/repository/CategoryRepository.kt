package com.otakeeesen.byebyemoneylist.data.local.repository

import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryColors
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class CategoryRepository(private val database: AppDatabase) {

    val allCategories: Flow<List<CategoryEntity>> = database.categoryDao().getAllCategories()

    suspend fun getAllCategoriesOnce(): List<CategoryEntity> {
        return withContext(Dispatchers.IO) {
            database.categoryDao().getAllCategoriesOnce()
        }
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

    private data class DefaultCategory(
        val nameResId: Int,
        val color: String,
        val children: List<Pair<Int, String>>,
        val isIncome: Boolean = false
    )

    suspend fun createDefaultCategories(context: android.content.Context) {
        withContext(Dispatchers.IO) {
            val categories = listOf(
                DefaultCategory(com.otakeeesen.byebyemoneylist.R.string.def_cat_supermarket, CategoryColors.GREEN, listOf(
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_bakery to CategoryColors.YELLOW,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_dairy to CategoryColors.YELLOW,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_eggs to CategoryColors.YELLOW,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_meat to CategoryColors.RED,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_seafood to CategoryColors.BLUE,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_cereals_muesli to CategoryColors.ORANGE,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_produce to CategoryColors.GREEN,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_frozen to CategoryColors.BLUE,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_beverages to CategoryColors.BLUE,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_snacks to CategoryColors.ORANGE,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_pantry to CategoryColors.TEAL
                )),
                DefaultCategory(com.otakeeesen.byebyemoneylist.R.string.def_cat_health_beauty, CategoryColors.DEFAULT_COLOR, listOf(
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_personal_care to CategoryColors.PURPLE,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_pharmacy to CategoryColors.PURPLE
                )),
                DefaultCategory(com.otakeeesen.byebyemoneylist.R.string.def_cat_household, CategoryColors.DEFAULT_COLOR, listOf(
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_cleaning to CategoryColors.TEAL,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_paper_goods to CategoryColors.TEAL,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_kitchen to CategoryColors.TEAL,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_laundry to CategoryColors.TEAL
                )),
                DefaultCategory(com.otakeeesen.byebyemoneylist.R.string.def_cat_automotive, CategoryColors.PURPLE, listOf(
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_fuel to CategoryColors.RED,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_car_maintenance to CategoryColors.ORANGE
                )),
                DefaultCategory(com.otakeeesen.byebyemoneylist.R.string.def_cat_services, CategoryColors.DEFAULT_COLOR, listOf(
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_utilities to CategoryColors.BLUE,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_rent to CategoryColors.BLUE,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_subscriptions to CategoryColors.PURPLE
                )),
                DefaultCategory(com.otakeeesen.byebyemoneylist.R.string.def_cat_lifestyle, CategoryColors.DEFAULT_COLOR, listOf(
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_restaurants to CategoryColors.ORANGE,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_entertainment to CategoryColors.ORANGE
                )),
                DefaultCategory(com.otakeeesen.byebyemoneylist.R.string.def_cat_income, CategoryColors.GREEN, listOf(
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_salary to CategoryColors.GREEN,
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_other_income to CategoryColors.GREEN
                ), isIncome = true)
            )

            var baseId = System.currentTimeMillis()
            categories.forEach { def ->
                val parentName = context.getString(def.nameResId)
                val parentId = baseId++
                database.categoryDao().insertCategory(
                    CategoryEntity(id = parentId, name = parentName, color = def.color, parentId = null, isIncome = def.isIncome)
                )

                def.children.forEach { (childResId, color) ->
                    val childName = context.getString(childResId)
                    database.categoryDao().insertCategory(
                        CategoryEntity(id = baseId++, name = childName, color = color, parentId = parentId, isIncome = def.isIncome)
                    )
                }
            }
        }
    }

    private fun generateId(): Long = System.currentTimeMillis()
}
