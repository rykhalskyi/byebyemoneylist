package com.otakeeesen.byebyemoneylist.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.otakeeesen.byebyemoneylist.data.local.dao.CategoryDao
import com.otakeeesen.byebyemoneylist.data.local.dao.PriceDao
import com.otakeeesen.byebyemoneylist.data.local.dao.ProductAnalogCrossRefDao
import com.otakeeesen.byebyemoneylist.data.local.dao.ProductDao
import com.otakeeesen.byebyemoneylist.data.local.dao.ShoppingListDao
import com.otakeeesen.byebyemoneylist.data.local.dao.StoreDao
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.PriceEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductAnalogCrossRef
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListItemEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity

@Database(
    entities = [
        CategoryEntity::class,
        StoreEntity::class,
        ProductEntity::class,
        PriceEntity::class,
        ShoppingListEntity::class,
        ShoppingListItemEntity::class,
        ProductAnalogCrossRef::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun storeDao(): StoreDao
    abstract fun productDao(): ProductDao
    abstract fun priceDao(): PriceDao
    abstract fun shoppingListDao(): ShoppingListDao
    abstract fun productAnalogCrossRefDao(): ProductAnalogCrossRefDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bye_bye_money_database",
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
