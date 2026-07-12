package com.otakeeesen.byebyemoneylist

import android.app.Application
import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ProductRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.PriceRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.StoreRepository
import com.otakeeesen.byebyemoneylist.data.sync.ListSyncEngine
import com.otakeeesen.byebyemoneylist.data.sync.SyncFolderRepository

class ByeByeMoneyApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val preferencesManager by lazy { PreferencesManager(this) }
    val productRepository by lazy { ProductRepository(database) }
    val shoppingListRepository by lazy { ShoppingListRepository(database) }
    val categoryRepository by lazy { CategoryRepository(database) }
    val storeRepository by lazy { StoreRepository(database) }
    val priceRepository by lazy { PriceRepository(database) }
    val syncFolderRepository by lazy { SyncFolderRepository(this, preferencesManager) }
    val listSyncEngine by lazy { ListSyncEngine(this, syncFolderRepository, database, preferencesManager) }
}
