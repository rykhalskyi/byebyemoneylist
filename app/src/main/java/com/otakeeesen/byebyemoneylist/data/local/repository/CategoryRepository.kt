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

        return createCategory(name = name)
    }

    /**
     * Creates a new category with a database-assigned (auto-increment) id and
     * returns that id. This is the single source of truth for category creation.
     */
    suspend fun createCategory(
        name: String,
        color: String = CategoryColors.DEFAULT_COLOR,
        parentId: Long? = null,
        isIncome: Boolean = false,
        emoji: String? = null,
    ): Long {
        return database.categoryDao().insertCategory(
            CategoryEntity(id = 0, name = name, color = color, parentId = parentId, isIncome = isIncome, emoji = emoji)
        )
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
        val children: List<DefaultChild>,
        val isIncome: Boolean = false,
        val emoji: String? = null
    )

    private data class DefaultChild(
        val nameResId: Int,
        val color: String,
        val emoji: String? = null
    )

    suspend fun createDefaultCategories(context: android.content.Context): Map<Int, Long> {
        return withContext(Dispatchers.IO) {
            val createdCategories = mutableMapOf<Int, Long>()
            val categories = listOf(
                DefaultCategory(com.otakeeesen.byebyemoneylist.R.string.def_cat_supermarket, CategoryColors.GREEN, listOf(
                    DefaultChild(com.otakeeesen.byebyemoneylist.R.string.def_cat_bakery, CategoryColors.YELLOW, "🥐"),
                    DefaultChild(com.otakeeesen.byebyemoneylist.R.string.def_cat_dairy, CategoryColors.YELLOW, "🥛"),
                    DefaultChild(com.otakeeesen.byebyemoneylist.R.string.def_cat_eggs, CategoryColors.YELLOW, "🥚"),
                    DefaultChild(com.otakeeesen.byebyemoneylist.R.string.def_cat_meat, CategoryColors.RED, "🥩"),
                    DefaultChild(com.otakeeesen.byebyemoneylist.R.string.def_cat_seafood, CategoryColors.BLUE, "🐟"),
                    DefaultChild(com.otakeeesen.byebyemoneylist.R.string.def_cat_cereals_muesli, CategoryColors.ORANGE, "🥣"),
                    DefaultChild(com.otakeeesen.byebyemoneylist.R.string.def_cat_produce, CategoryColors.GREEN, "🥦"),
                    DefaultChild(com.otakeeesen.byebyemoneylist.R.string.def_cat_frozen, CategoryColors.BLUE, "🧊"),
                    DefaultChild(com.otakeeesen.byebyemoneylist.R.string.def_cat_beverages, CategoryColors.BLUE, "🥤"),
                    DefaultChild(com.otakeeesen.byebyemoneylist.R.string.def_cat_snacks, CategoryColors.ORANGE, "🍿"),
                    DefaultChild(com.otakeeesen.byebyemoneylist.R.string.def_cat_pantry, CategoryColors.TEAL, "🗄️")
                ), emoji = "🛒"),
                DefaultCategory(com.otakeeesen.byebyemoneylist.R.string.def_cat_health_beauty, CategoryColors.DEFAULT_COLOR, listOf(
                    DefaultChild(com.otakeeesen.byebyemoneylist.R.string.def_cat_personal_care, CategoryColors.PURPLE, "🧴"),
                    DefaultChild(com.otakeeesen.byebyemoneylist.R.string.def_cat_pharmacy, CategoryColors.PURPLE, "💊")
                ), emoji = "🏥"),
                DefaultCategory(com.otakeeesen.byebyemoneylist.R.string.def_cat_household, CategoryColors.DEFAULT_COLOR, listOf(
                    DefaultChild(com.otakeeesen.byebyemoneylist.R.string.def_cat_cleaning, CategoryColors.TEAL, "🧹"),
                    DefaultChild(com.otakeeesen.byebyemoneylist.R.string.def_cat_paper_goods, CategoryColors.TEAL, "🧻"),
                    DefaultChild(com.otakeeesen.byebyemoneylist.R.string.def_cat_kitchen, CategoryColors.TEAL, "🍳"),
                    DefaultChild(com.otakeeesen.byebyemoneylist.R.string.def_cat_laundry, CategoryColors.TEAL, "🧺")
                ), emoji = "🏠"),
                DefaultCategory(com.otakeeesen.byebyemoneylist.R.string.def_cat_automotive, CategoryColors.PURPLE, listOf(
                    DefaultChild(com.otakeeesen.byebyemoneylist.R.string.def_cat_fuel, CategoryColors.RED, "⛽"),
                    DefaultChild(com.otakeeesen.byebyemoneylist.R.string.def_cat_car_maintenance, CategoryColors.ORANGE, "🔧")
                ), emoji = "🚗"),
                DefaultCategory(com.otakeeesen.byebyemoneylist.R.string.def_cat_services, CategoryColors.DEFAULT_COLOR, listOf(
                    DefaultChild(com.otakeeesen.byebyemoneylist.R.string.def_cat_utilities, CategoryColors.BLUE, "💡"),
                    DefaultChild(com.otakeeesen.byebyemoneylist.R.string.def_cat_rent, CategoryColors.BLUE, "🏢"),
                    DefaultChild(com.otakeeesen.byebyemoneylist.R.string.def_cat_subscriptions, CategoryColors.PURPLE, "🔁")
                ), emoji = "🧾"),
                DefaultCategory(com.otakeeesen.byebyemoneylist.R.string.def_cat_lifestyle, CategoryColors.DEFAULT_COLOR, listOf(
                    DefaultChild(com.otakeeesen.byebyemoneylist.R.string.def_cat_restaurants, CategoryColors.ORANGE, "🍽️"),
                    DefaultChild(com.otakeeesen.byebyemoneylist.R.string.def_cat_entertainment, CategoryColors.ORANGE, "🎬")
                ), emoji = "🎉"),
                DefaultCategory(com.otakeeesen.byebyemoneylist.R.string.def_cat_income, CategoryColors.GREEN, listOf(
                    DefaultChild(com.otakeeesen.byebyemoneylist.R.string.def_cat_salary, CategoryColors.GREEN, "💰"),
                    DefaultChild(com.otakeeesen.byebyemoneylist.R.string.def_cat_freelance, CategoryColors.GREEN, "💻")
                ), isIncome = true, emoji = "📈")
            )

            categories.forEach { def ->
                val parentName = context.getString(def.nameResId)
                val parentId = createCategory(
                    name = parentName,
                    color = def.color,
                    parentId = null,
                    isIncome = def.isIncome,
                    emoji = def.emoji
                )
                createdCategories[def.nameResId] = parentId

                def.children.forEach { child ->
                    val childName = context.getString(child.nameResId)
                    val childId = createCategory(
                        name = childName,
                        color = child.color,
                        parentId = parentId,
                        isIncome = def.isIncome,
                        emoji = child.emoji
                    )
                    createdCategories[child.nameResId] = childId
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
            val salaryProdId = productRepository.createProduct(
                name = context.getString(com.otakeeesen.byebyemoneylist.R.string.def_prod_salary),
                categoryId = createdCategories[com.otakeeesen.byebyemoneylist.R.string.def_cat_salary],
                status = "reviewed",
                isIncome = true
            )

            val rentProdId = productRepository.createProduct(
                name = context.getString(com.otakeeesen.byebyemoneylist.R.string.def_prod_rent),
                categoryId = createdCategories[com.otakeeesen.byebyemoneylist.R.string.def_cat_rent],
                status = "reviewed",
                isSubscription = true
            )

            val utilitiesProdId = productRepository.createProduct(
                name = context.getString(com.otakeeesen.byebyemoneylist.R.string.def_prod_utilities),
                categoryId = createdCategories[com.otakeeesen.byebyemoneylist.R.string.def_cat_utilities],
                status = "reviewed",
                isSubscription = true
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
}
