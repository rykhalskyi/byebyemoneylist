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
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListCategoryCrossRef
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListItemEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreCategoryCrossRef
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
        StoreCategoryCrossRef::class,
        ShoppingListCategoryCrossRef::class,
    ],
    version = 16,
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

        internal val MIGRATION_14_TO_15 = Migration(14, 15) { db ->
            db.execSQL("ALTER TABLE shopping_list_items ADD COLUMN discount REAL")
            db.execSQL("ALTER TABLE shopping_list_items ADD COLUMN customName TEXT")
        }

        internal val MIGRATION_15_TO_16 = Migration(15, 16) { db ->
            // 1. Create the new products table with categoryId instead of category
            // Exact schema from 16.json: id, name, barcode, picturePath, categoryId, status, changedAt, isSubscription
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `products_new` (
                    `id` INTEGER NOT NULL, 
                    `name` TEXT NOT NULL, 
                    `barcode` TEXT NOT NULL, 
                    `picturePath` TEXT, 
                    `categoryId` INTEGER, 
                    `status` TEXT NOT NULL, 
                    `changedAt` INTEGER NOT NULL, 
                    `isSubscription` INTEGER NOT NULL, 
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())

            // 2. Migrate data and map category names to IDs
            db.execSQL("""
                INSERT INTO products_new (id, name, barcode, picturePath, categoryId, status, changedAt, isSubscription)
                SELECT p.id, p.name, p.barcode, p.picturePath, c.id, p.status, p.changedAt, p.isSubscription
                FROM products p LEFT JOIN categories c ON p.category = c.name
            """.trimIndent())

            // 3. Drop old table and rename new one
            db.execSQL("DROP TABLE products")
            db.execSQL("ALTER TABLE products_new RENAME TO products")
        }

        internal val MIGRATION_2_TO_3 = Migration(2, 3) { db ->
            db.execSQL("ALTER TABLE shopping_list_items ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
        }

        internal val MIGRATION_3_TO_4 = Migration(3, 4) { db ->
            db.execSQL("ALTER TABLE categories ADD COLUMN color TEXT NOT NULL DEFAULT '#FF6B6B'")
        }

        internal val MIGRATION_4_TO_5 = Migration(4, 5) { db ->
            db.execSQL("ALTER TABLE shopping_lists ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
        }

        internal val MIGRATION_5_TO_6 = Migration(5, 6) { db ->
            db.execSQL("ALTER TABLE shopping_list_items ADD COLUMN price REAL")
        }

        internal val MIGRATION_6_TO_7 = Migration(6, 7) { db ->
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

        internal val MIGRATION_7_TO_8 = Migration(7, 8) { db ->
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

        internal val MIGRATION_8_TO_9 = Migration(8, 9) { db ->
            db.execSQL("ALTER TABLE products ADD COLUMN status TEXT NOT NULL DEFAULT 'reviewed'")
            db.execSQL("ALTER TABLE products ADD COLUMN changedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE prices ADD COLUMN changedAt INTEGER NOT NULL DEFAULT 0")
        }

        internal val MIGRATION_9_TO_10 = Migration(9, 10) { db ->
            // 1. Add parentId to categories
            db.execSQL("ALTER TABLE categories ADD COLUMN parentId INTEGER DEFAULT NULL")

            // 2. Create store_category_cross_ref table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `store_category_cross_ref` (
                    `storeId` INTEGER NOT NULL, 
                    `categoryId` INTEGER NOT NULL, 
                    PRIMARY KEY(`storeId`, `categoryId`), 
                    FOREIGN KEY(`storeId`) REFERENCES `stores`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , 
                    FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_store_category_cross_ref_storeId` ON `store_category_cross_ref` (`storeId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_store_category_cross_ref_categoryId` ON `store_category_cross_ref` (`categoryId`)")

            // 3. Create shopping_list_category_cross_ref table
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `shopping_list_category_cross_ref` (
                    `shoppingListId` INTEGER NOT NULL, 
                    `categoryId` INTEGER NOT NULL, 
                    PRIMARY KEY(`shoppingListId`, `categoryId`), 
                    FOREIGN KEY(`shoppingListId`) REFERENCES `shopping_lists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , 
                    FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_shopping_list_category_cross_ref_shoppingListId` ON `shopping_list_category_cross_ref` (`shoppingListId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_shopping_list_category_cross_ref_categoryId` ON `shopping_list_category_cross_ref` (`categoryId`)")

            // 4. Migrate data
            // Migrate stores (matching by category name)
            db.execSQL("""
                INSERT INTO store_category_cross_ref (storeId, categoryId)
                SELECT s.id, c.id FROM stores s JOIN categories c ON s.category = c.name
            """.trimIndent())

            // 5. Remove 'category' from stores and 'categoryId' from shopping_lists (Optional but cleaner)
            // Note: SQLite doesn't support DROP COLUMN easily before 3.35.0, 
            // but we can recreate the table if needed.
            // For now, let's at least recreate the 'stores' table without the 'category' column
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `stores_new` (
                    `id` INTEGER NOT NULL, 
                    `name` TEXT NOT NULL, 
                    `logoPath` TEXT, 
                    `address` TEXT, 
                    `receiptName` TEXT, 
                    PRIMARY KEY(`id`)
                )
            """.trimIndent())
            db.execSQL("INSERT INTO stores_new (id, name, logoPath, address, receiptName) SELECT id, name, logoPath, address, receiptName FROM stores")
            db.execSQL("DROP TABLE stores")
            db.execSQL("ALTER TABLE stores_new RENAME TO stores")

            // Recreate shopping_lists without categoryId
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS `shopping_lists_new` (
                    `id` INTEGER NOT NULL, 
                    `name` TEXT NOT NULL, 
                    `createDate` INTEGER NOT NULL, 
                    `purchaseDate` INTEGER, 
                    `storeId` INTEGER, 
                    `isFinished` INTEGER NOT NULL, 
                    `finalTotal` REAL, 
                    `position` INTEGER NOT NULL, 
                    PRIMARY KEY(`id`), 
                    FOREIGN KEY(`storeId`) REFERENCES `stores`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE 
                )
            """.trimIndent())
            db.execSQL("INSERT INTO shopping_list_category_cross_ref (shoppingListId, categoryId) SELECT id, categoryId FROM shopping_lists WHERE categoryId IS NOT NULL")
            db.execSQL("INSERT INTO shopping_lists_new (id, name, createDate, purchaseDate, storeId, isFinished, finalTotal, position) SELECT id, name, createDate, purchaseDate, storeId, isFinished, finalTotal, position FROM shopping_lists")
            db.execSQL("DROP TABLE shopping_lists")
            db.execSQL("ALTER TABLE shopping_lists_new RENAME TO shopping_lists")
        }

        internal val MIGRATION_10_TO_11 = Migration(10, 11) { db ->
            db.execSQL("ALTER TABLE shopping_lists ADD COLUMN isRecurring INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE shopping_lists ADD COLUMN recurringPeriod TEXT NOT NULL DEFAULT 'MONTH'")
            db.execSQL("ALTER TABLE shopping_lists ADD COLUMN isForwardEmpty INTEGER NOT NULL DEFAULT 1")
        }

        internal val MIGRATION_11_TO_12 = Migration(11, 12) { db ->
            db.execSQL("""
                CREATE TABLE shopping_list_items_new (
                    id INTEGER PRIMARY KEY NOT NULL,
                    shoppingListId INTEGER NOT NULL,
                    productId INTEGER NOT NULL,
                    quantity REAL NOT NULL,
                    isChecked INTEGER NOT NULL,
                    position INTEGER NOT NULL DEFAULT 0,
                    price REAL
                )
            """.trimIndent())
            db.execSQL("INSERT INTO shopping_list_items_new SELECT id, shoppingListId, productId, CAST(quantity AS REAL), isChecked, position, price FROM shopping_list_items")
            db.execSQL("DROP TABLE shopping_list_items")
            db.execSQL("ALTER TABLE shopping_list_items_new RENAME TO shopping_list_items")
        }

        internal val MIGRATION_12_TO_13 = Migration(12, 13) { db ->
            db.execSQL("ALTER TABLE shopping_lists ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
        }

        internal val MIGRATION_13_TO_14 = Migration(13, 14) { db ->
            db.execSQL("ALTER TABLE shopping_lists ADD COLUMN isSubscription INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE products ADD COLUMN isSubscription INTEGER NOT NULL DEFAULT 0")
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bye_bye_money_database",
                )
                    .addMigrations(MIGRATION_2_TO_3, MIGRATION_3_TO_4, MIGRATION_4_TO_5, MIGRATION_5_TO_6, MIGRATION_6_TO_7, MIGRATION_7_TO_8, MIGRATION_8_TO_9, MIGRATION_9_TO_10, MIGRATION_10_TO_11, MIGRATION_11_TO_12, MIGRATION_12_TO_13, MIGRATION_13_TO_14, MIGRATION_14_TO_15, MIGRATION_15_TO_16)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
