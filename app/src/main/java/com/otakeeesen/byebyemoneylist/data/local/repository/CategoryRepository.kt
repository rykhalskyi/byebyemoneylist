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

    suspend fun createDefaultCategories(context: android.content.Context): Map<Int, Long> {
        return withContext(Dispatchers.IO) {
            val createdCategories = mutableMapOf<Int, Long>()
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
                    com.otakeeesen.byebyemoneylist.R.string.def_cat_freelance to CategoryColors.GREEN
                ), isIncome = true)
            )

            var baseId = System.currentTimeMillis()
            categories.forEach { def ->
                val parentName = context.getString(def.nameResId)
                val parentId = baseId++
                database.categoryDao().insertCategory(
                    CategoryEntity(id = parentId, name = parentName, color = def.color, parentId = null, isIncome = def.isIncome)
                )
                createdCategories[def.nameResId] = parentId

                def.children.forEach { (childResId, color) ->
                    val childName = context.getString(childResId)
                    val childId = baseId++
                    database.categoryDao().insertCategory(
                        CategoryEntity(id = childId, name = childName, color = color, parentId = parentId, isIncome = def.isIncome)
                    )
                    createdCategories[childResId] = childId
                }
            }
            createdCategories
        }
    }

    suspend fun createInitialData(
        context: android.content.Context,
        productRepository: ProductRepository,
        shoppingListRepository: ShoppingListRepository
    ) {
        val createdCategories = createDefaultCategories(context)

        withContext(Dispatchers.IO) {
            var currentId = System.currentTimeMillis() + 1000

            // 1. Create default products
            val salaryProdId = currentId++
            productRepository.insertProduct(
                com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity(
                    id = salaryProdId,
                    name = context.getString(com.otakeeesen.byebyemoneylist.R.string.def_prod_salary),
                    barcode = "",
                    picturePath = null,
                    categoryId = createdCategories[com.otakeeesen.byebyemoneylist.R.string.def_cat_salary],
                    status = "reviewed",
                    isIncome = true
                )
            )

            val rentProdId = currentId++
            productRepository.insertProduct(
                com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity(
                    id = rentProdId,
                    name = context.getString(com.otakeeesen.byebyemoneylist.R.string.def_prod_rent),
                    barcode = "",
                    picturePath = null,
                    categoryId = createdCategories[com.otakeeesen.byebyemoneylist.R.string.def_cat_rent],
                    status = "reviewed",
                    isSubscription = true
                )
            )

            val utilitiesProdId = currentId++
            productRepository.insertProduct(
                com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity(
                    id = utilitiesProdId,
                    name = context.getString(com.otakeeesen.byebyemoneylist.R.string.def_prod_utilities),
                    barcode = "",
                    picturePath = null,
                    categoryId = createdCategories[com.otakeeesen.byebyemoneylist.R.string.def_cat_utilities],
                    status = "reviewed",
                    isSubscription = true
                )
            )

            // 2. Create default lists
            val incomeListId = currentId++
            val incomeCatId = createdCategories[com.otakeeesen.byebyemoneylist.R.string.def_cat_income]
            shoppingListRepository.insertShoppingList(
                com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity(
                    id = incomeListId,
                    name = context.getString(com.otakeeesen.byebyemoneylist.R.string.def_list_income),
                    createDate = System.currentTimeMillis(),
                    purchaseDate = null,
                    storeId = null,
                    isFinished = false,
                    isIncome = true
                ),
                categoryIds = if (incomeCatId != null) listOf(incomeCatId) else emptyList()
            )
            // Add Salary product to Income list
            shoppingListRepository.insertShoppingListItem(
                com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListItemEntity(
                    id = currentId++,
                    shoppingListId = incomeListId,
                    productId = salaryProdId,
                    quantity = 1.0,
                    isChecked = false
                )
            )

            val subsListId = currentId++
            val subsCatId = createdCategories[com.otakeeesen.byebyemoneylist.R.string.def_cat_subscriptions]
            shoppingListRepository.insertShoppingList(
                com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity(
                    id = subsListId,
                    name = context.getString(com.otakeeesen.byebyemoneylist.R.string.def_list_subscriptions),
                    createDate = System.currentTimeMillis(),
                    purchaseDate = System.currentTimeMillis(),
                    storeId = null,
                    isFinished = true,
                    isSubscription = true,
                    isRecurring = true,
                    recurringPeriod = "MONTH"
                ),
                categoryIds = if (subsCatId != null) listOf(subsCatId) else emptyList()
            )
            // Add Rent to Subscriptions list
            shoppingListRepository.insertShoppingListItem(
                com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListItemEntity(
                    id = currentId++,
                    shoppingListId = subsListId,
                    productId = rentProdId,
                    quantity = 1.0,
                    isChecked = false
                )
            )
            // Add Utilities to Subscriptions list
            shoppingListRepository.insertShoppingListItem(
                com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListItemEntity(
                    id = currentId++,
                    shoppingListId = subsListId,
                    productId = utilitiesProdId,
                    quantity = 1.0,
                    isChecked = false
                )
            )

            val autoListId = currentId++
            val autoCatId = createdCategories[com.otakeeesen.byebyemoneylist.R.string.def_cat_automotive]
            shoppingListRepository.insertShoppingList(
                com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity(
                    id = autoListId,
                    name = context.getString(com.otakeeesen.byebyemoneylist.R.string.def_list_auto),
                    createDate = System.currentTimeMillis(),
                    purchaseDate = null,
                    storeId = null,
                    isFinished = false,
                    isRecurring = true,
                    recurringPeriod = "MONTH"
                ),
                categoryIds = if (autoCatId != null) listOf(autoCatId) else emptyList()
            )
        }
    }

    private fun generateId(): Long = System.currentTimeMillis()
}
