package com.otakeeesen.byebyemoneylist

import android.app.Application
import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository

class ByeByeMoneyApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val shoppingListRepository by lazy { ShoppingListRepository(database) }
}
