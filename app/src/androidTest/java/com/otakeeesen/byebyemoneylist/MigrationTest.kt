package com.otakeeesen.byebyemoneylist

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName!!,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate9To10() {
        // Create database with version 9
        var db = helper.createDatabase(TEST_DB, 9)

        // Insert data using version 9 schema
        db.execSQL("INSERT INTO categories (id, name, color) VALUES (1, 'Food', '#FF0000')")
        db.execSQL("INSERT INTO stores (id, name, logoPath, category) VALUES (1, 'Rewe', NULL, 'Food')")
        db.execSQL("INSERT INTO shopping_lists (id, name, createDate, purchaseDate, storeId, categoryId, isFinished, finalTotal, position) VALUES (1, 'Weekly', 123456, NULL, 1, 1, 0, NULL, 0)")

        db.close()

        // Migrate to version 10
        db = helper.runMigrationsAndValidate(TEST_DB, 10, true, AppDatabase.MIGRATION_9_TO_10)

        // 1. Verify Store Category Migration
        val storeCategoryCursor = db.query("SELECT * FROM store_category_cross_ref")
        assert(storeCategoryCursor.moveToFirst()) { "store_category_cross_ref should not be empty" }
        assert(storeCategoryCursor.getLong(storeCategoryCursor.getColumnIndexOrThrow("storeId")) == 1L)
        assert(storeCategoryCursor.getLong(storeCategoryCursor.getColumnIndexOrThrow("categoryId")) == 1L)
        storeCategoryCursor.close()

        // 2. Verify Shopping List Category Migration
        val listCategoryCursor = db.query("SELECT * FROM shopping_list_category_cross_ref")
        assert(listCategoryCursor.moveToFirst()) { "shopping_list_category_cross_ref should not be empty" }
        assert(listCategoryCursor.getLong(listCategoryCursor.getColumnIndexOrThrow("shoppingListId")) == 1L)
        assert(listCategoryCursor.getLong(listCategoryCursor.getColumnIndexOrThrow("categoryId")) == 1L)
        listCategoryCursor.close()

        // 3. Verify 'category' column removed from 'stores'
        val storeCursor = db.query("SELECT * FROM stores")
        assert(storeCursor.columnNames.indexOf("category") == -1) { "'category' column should be removed from 'stores'" }
        storeCursor.close()

        // 4. Verify 'categoryId' column removed from 'shopping_lists'
        val listCursor = db.query("SELECT * FROM shopping_lists")
        assert(listCursor.columnNames.indexOf("categoryId") == -1) { "'categoryId' column should be removed from 'shopping_lists'" }
        listCursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate16To17() {
        // Create database with version 16
        var db = helper.createDatabase(TEST_DB, 16)

        // Insert data using version 16 schema
        db.execSQL("INSERT INTO products (id, name, barcode, picturePath, categoryId, status, changedAt, isSubscription) VALUES (1, 'Milk', '123', NULL, NULL, 'reviewed', 123456, 0)")

        db.close()

        // Migrate to version 17
        db = helper.runMigrationsAndValidate(TEST_DB, 17, true, AppDatabase.MIGRATION_16_TO_17)

        // Verify 'isFavorite' column added
        val productCursor = db.query("SELECT * FROM products")
        val isFavoriteIndex = productCursor.getColumnIndexOrThrow("isFavorite")
        assert(productCursor.moveToFirst())
        assert(productCursor.getInt(isFavoriteIndex) == 0) // Default value 0
        productCursor.close()
    }
}
