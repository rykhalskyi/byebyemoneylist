package com.otakeeesen.byebyemoneylist

import android.app.Application
import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ProductRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository

class ByeByeMoneyApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val productRepository by lazy { ProductRepository(database) }
    val shoppingListRepository by lazy { ShoppingListRepository(database) }
    val categoryRepository by lazy { CategoryRepository(database) }
}
