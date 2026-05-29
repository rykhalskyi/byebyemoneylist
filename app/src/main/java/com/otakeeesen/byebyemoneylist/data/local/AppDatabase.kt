package com.otakeeesen.byebyemoneylist.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import android.content.Context
import com.otakeeesen.byebyemoneylist.data.local.dao.CategoryDao
import com.otakeeesen.byebyemoneylist.data.local.dao.PriceDao
import com.otakeeesen.byebyemoneylist.data.local.dao.ProductAnalogCrossRefDao
import com.otakeeesen.byebyemoneylist.data.local.dao.ProductDao
import com.otakeeesen.byebyemoneylist.data.local.dao.ShoppingListDao
import com.otakeeesen.byebyemoneylist.data.local.dao.StoreDao
import com.otakeeesen.byebyemoneylist.data.local.dao.ProductAliasDao
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.PriceEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductAliasEntity
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
        ProductAliasEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun storeDao(): StoreDao
    abstract fun productDao(): ProductDao
    abstract fun priceDao(): PriceDao
    abstract fun shoppingListDao(): ShoppingListDao
    abstract fun productAnalogCrossRefDao(): ProductAnalogCrossRefDao
    abstract fun productAliasDao(): ProductAliasDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_TO_3 = Migration(2, 3) { db ->
            db.execSQL("ALTER TABLE shopping_list_items ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
        }

        private val MIGRATION_3_TO_4 = Migration(3, 4) { db ->
            db.execSQL("ALTER TABLE categories ADD COLUMN color TEXT NOT NULL DEFAULT '#FF6B6B'")
        }

        private val MIGRATION_4_TO_5 = Migration(4, 5) { db ->
            db.execSQL("ALTER TABLE shopping_lists ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
        }

        private val MIGRATION_5_TO_6 = Migration(5, 6) { db ->
            db.execSQL("ALTER TABLE shopping_list_items ADD COLUMN price REAL")
        }

        private val MIGRATION_6_TO_7 = Migration(6, 7) { db ->
            // Recreate prices table with nullable storeId
            db.execSQL("""
                CREATE TABLE prices_new (
                    id INTEGER PRIMARY KEY NOT NULL,
                    productId INTEGER NOT NULL,
                    storeId INTEGER,
                    value REAL NOT NULL,
                    date INTEGER NOT NULL,
                    FOREIGN KEY(productId) REFERENCES products(id) ON DELETE CASCADE,
                    FOREIGN KEY(storeId) REFERENCES stores(id) ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("INSERT INTO prices_new (id, productId, storeId, value, date) SELECT id, productId, storeId, value, date FROM prices")
            db.execSQL("DROP TABLE prices")
            db.execSQL("ALTER TABLE prices_new RENAME TO prices")
        }

        private val MIGRATION_7_TO_8 = Migration(7, 8) { db ->
            db.execSQL("ALTER TABLE stores ADD COLUMN address TEXT")
            db.execSQL("ALTER TABLE stores ADD COLUMN receiptName TEXT")
            
            db.execSQL("""
                CREATE TABLE product_aliases (
                    id INTEGER PRIMARY KEY NOT NULL,
                    productId INTEGER NOT NULL,
                    aliasName TEXT NOT NULL,
                    storeId INTEGER,
                    FOREIGN KEY(productId) REFERENCES products(id) ON DELETE CASCADE,
                    FOREIGN KEY(storeId) REFERENCES stores(id) ON DELETE SET NULL
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX index_product_aliases_productId ON product_aliases(productId)")
            db.execSQL("CREATE INDEX index_product_aliases_storeId ON product_aliases(storeId)")
            db.execSQL("CREATE INDEX index_product_aliases_aliasName ON product_aliases(aliasName)")
        }

        private val MIGRATION_8_TO_9 = Migration(8, 9) { db ->
            db.execSQL("ALTER TABLE products ADD COLUMN status TEXT NOT NULL DEFAULT 'reviewed'")
            db.execSQL("ALTER TABLE products ADD COLUMN changedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE prices ADD COLUMN changedAt INTEGER NOT NULL DEFAULT 0")
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bye_bye_money_database",
                )
                    .addMigrations(MIGRATION_2_TO_3, MIGRATION_3_TO_4, MIGRATION_4_TO_5, MIGRATION_5_TO_6, MIGRATION_6_TO_7, MIGRATION_7_TO_8, MIGRATION_8_TO_9)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
