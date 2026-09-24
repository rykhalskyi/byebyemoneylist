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

    @Test
    @Throws(IOException::class)
    fun migrate22To23() {
        // Create database with version 22
        var db = helper.createDatabase(TEST_DB, 22)

        // Insert data using version 22 schema (includes isArchived)
        db.execSQL("""
            INSERT INTO shopping_lists (id, name, createDate, purchaseDate, storeId, isFinished, finalTotal, position, isRecurring, recurringPeriod, isForwardEmpty, isArchived, isSubscription, isIncome, isShared, syncId, lastSyncTimestamp, lastModifiedAt)
            VALUES (1, 'Weekly', 123456, NULL, NULL, 1, 42.5, 0, 1, 'MONTH', 1, 1, 0, 0, 0, NULL, 123456, 123456)
        """.trimIndent())

        db.close()

        // Migrate to version 23
        db = helper.runMigrationsAndValidate(TEST_DB, 23, true, AppDatabase.MIGRATION_22_TO_23)

        // Verify 'isArchived' column removed
        val listCursor = db.query("SELECT * FROM shopping_lists")
        assert(listCursor.columnNames.indexOf("isArchived") == -1) { "'isArchived' column should be removed from 'shopping_lists'" }

        // Verify data preserved
        assert(listCursor.moveToFirst())
        assert(listCursor.getLong(listCursor.getColumnIndexOrThrow("id")) == 1L)
        assert(listCursor.getString(listCursor.getColumnIndexOrThrow("name")) == "Weekly")
        assert(listCursor.getDouble(listCursor.getColumnIndexOrThrow("finalTotal")) == 42.5)
        assert(listCursor.getInt(listCursor.getColumnIndexOrThrow("isFinished")) == 1)
        listCursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate24To25() {
        // Create database with version 24
        var db = helper.createDatabase(TEST_DB, 24)

        // Insert data using version 24 schema
        db.execSQL("INSERT INTO stores (id, name, logoPath, address, receiptName) VALUES (1, 'Rewe', NULL, NULL, NULL)")
        db.execSQL("INSERT INTO categories (id, name, color, parentId, isIncome, emoji, serverId) VALUES (1, 'Food', '#FF0000', NULL, 0, NULL, NULL)")

        db.close()

        // Migrate to version 25
        db = helper.runMigrationsAndValidate(TEST_DB, 25, true, AppDatabase.MIGRATION_24_TO_25)

        // Verify 'serverId' column added to 'stores' and data preserved
        val storeCursor = db.query("SELECT * FROM stores")
        val serverIdIndex = storeCursor.getColumnIndexOrThrow("serverId")
        assert(storeCursor.moveToFirst())
        assert(storeCursor.getString(storeCursor.getColumnIndexOrThrow("name")) == "Rewe")
        assert(storeCursor.isNull(serverIdIndex)) { "'serverId' should default to NULL" }
        storeCursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate25To26() {
        // Create database with version 25 (schema already has 'stores.serverId')
        var db = helper.createDatabase(TEST_DB, 25)

        // Insert data using version 25 schema
        db.execSQL("INSERT INTO stores (id, name, logoPath, address, receiptName, serverId) VALUES (1, 'Rewe', NULL, NULL, NULL, 's-1')")
        db.execSQL("INSERT INTO products (id, name, barcode, picturePath, categoryId, status, changedAt, isSubscription, isFavorite, isIncome) VALUES (1, 'Milk', '123', NULL, NULL, 'reviewed', 123456, 0, 0, 0)")

        db.close()

        // Migrate to version 26
        db = helper.runMigrationsAndValidate(TEST_DB, 26, true, AppDatabase.MIGRATION_25_TO_26)

        // Verify 'serverId' column added to 'products' and data preserved
        val productCursor = db.query("SELECT * FROM products")
        val serverIdIndex = productCursor.getColumnIndexOrThrow("serverId")
        assert(productCursor.moveToFirst())
        assert(productCursor.getString(productCursor.getColumnIndexOrThrow("name")) == "Milk")
        assert(productCursor.isNull(serverIdIndex)) { "'serverId' should default to NULL" }
        productCursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate26To27() {
        // Create database with version 26 (no 'shopping_lists.serverId' yet)
        var db = helper.createDatabase(TEST_DB, 26)

        // Insert data using version 26 schema
        db.execSQL("""
            INSERT INTO shopping_lists (id, name, createDate, purchaseDate, storeId, isFinished, finalTotal, position, isRecurring, recurringPeriod, isForwardEmpty, isSubscription, isIncome, isShared, syncId, lastSyncTimestamp, lastModifiedAt)
            VALUES (1, 'Weekly', 123456, NULL, NULL, 1, 42.5, 0, 1, 'MONTH', 1, 0, 0, 0, NULL, 123456, 123456)
        """.trimIndent())

        db.close()

        // Migrate to version 27
        db = helper.runMigrationsAndValidate(TEST_DB, 27, true, AppDatabase.MIGRATION_26_TO_27)

        // Verify 'serverId' column added to 'shopping_lists' and data preserved
        val listCursor = db.query("SELECT * FROM shopping_lists")
        val serverIdIndex = listCursor.getColumnIndexOrThrow("serverId")
        assert(listCursor.moveToFirst())
        assert(listCursor.getString(listCursor.getColumnIndexOrThrow("name")) == "Weekly")
        assert(listCursor.isNull(serverIdIndex)) { "'serverId' should default to NULL" }
        listCursor.close()

        // Verify the pending-delete queue table exists
        val pendingCursor = db.query("SELECT * FROM sync_pending_deletes")
        assert(!pendingCursor.moveToFirst()) { "'sync_pending_deletes' should start empty" }
        pendingCursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate27To28() {
        // Create database with version 27 (no 'sync_state' table yet)
        var db = helper.createDatabase(TEST_DB, 27)

        db.close()

        // Migrate to version 28
        db = helper.runMigrationsAndValidate(TEST_DB, 28, true, AppDatabase.MIGRATION_27_TO_28)

        // Verify the sync_state table exists with the expected columns
        val stateCursor = db.query("SELECT * FROM sync_state")
        val columns = stateCursor.columnNames
        assert(columns.contains("entityType")) { "'entityType' column missing" }
        assert(columns.contains("localId")) { "'localId' column missing" }
        assert(columns.contains("serverId")) { "'serverId' column missing" }
        assert(columns.contains("baseSnapshot")) { "'baseSnapshot' column missing" }
        assert(columns.contains("lastSyncAt")) { "'lastSyncAt' column missing" }
        assert(!stateCursor.moveToFirst()) { "'sync_state' should start empty" }
        stateCursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate28To29() {
        // Create database with version 28 (schema already has 'sync_state')
        var db = helper.createDatabase(TEST_DB, 28)

        // Insert stale store/category base snapshots
        db.execSQL("""
            INSERT INTO sync_state (entityType, localId, serverId, baseSnapshot, lastSyncAt)
            VALUES ('store', 1, 's-1', '{"name":"Rewe"}', 1)
        """.trimIndent())
        db.execSQL("""
            INSERT INTO sync_state (entityType, localId, serverId, baseSnapshot, lastSyncAt)
            VALUES ('category', 1, 'c-1', '{"name":"Food"}', 1)
        """.trimIndent())

        db.close()

        // Migrate to version 29
        db = helper.runMigrationsAndValidate(TEST_DB, 29, true, AppDatabase.MIGRATION_28_TO_29)

        // Store rows are dropped (projection shape changed) so the next plan re-baselines
        val storeCursor = db.query("SELECT * FROM sync_state WHERE entityType = 'store'")
        assert(!storeCursor.moveToFirst()) { "stale 'store' sync_state rows should be cleared" }
        storeCursor.close()

        // Other entity types are untouched
        val categoryCursor = db.query("SELECT * FROM sync_state WHERE entityType = 'category'")
        assert(categoryCursor.moveToFirst()) { "'category' sync_state rows must survive" }
        assert(categoryCursor.getString(categoryCursor.getColumnIndexOrThrow("serverId")) == "c-1")
        categoryCursor.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrate29To30() {
        var db = helper.createDatabase(TEST_DB, 29)

        db.execSQL("INSERT INTO shopping_lists (id, name, createDate, purchaseDate, isFinished, position, isRecurring, recurringPeriod, isForwardEmpty, isSubscription, isIncome, isShared, lastSyncTimestamp, lastModifiedAt) VALUES (1, 'Weekly', 100, NULL, 0, 0, 0, 'MONTH', 1, 0, 0, 0, 0, 0)")
        db.execSQL("INSERT INTO shopping_lists (id, name, createDate, purchaseDate, isFinished, position, isRecurring, recurringPeriod, isForwardEmpty, isSubscription, isIncome, isShared, lastSyncTimestamp, lastModifiedAt) VALUES (2, 'Groceries', 200, 250, 1, 1, 0, 'MONTH', 1, 0, 0, 0, 0, 0)")
        db.execSQL("INSERT INTO shopping_lists (id, name, createDate, purchaseDate, isFinished, position, isRecurring, recurringPeriod, isForwardEmpty, isSubscription, isIncome, isShared, lastSyncTimestamp, lastModifiedAt) VALUES (3, 'Salary', 300, NULL, 0, 2, 0, 'MONTH', 1, 0, 1, 0, 0, 0)")
        db.execSQL("INSERT INTO shopping_lists (id, name, createDate, purchaseDate, isFinished, position, isRecurring, recurringPeriod, isForwardEmpty, isSubscription, isIncome, isShared, lastSyncTimestamp, lastModifiedAt) VALUES (4, 'Netflix', 400, NULL, 0, 3, 1, 'MONTH', 1, 1, 0, 0, 0, 0)")

        db.close()

        db = helper.runMigrationsAndValidate(TEST_DB, 30, true, AppDatabase.MIGRATION_29_TO_30)

        val cursor = db.query("SELECT id, kind, isActive FROM shopping_lists ORDER BY id")
        assert(cursor.moveToFirst())
        // list 1 (open list -> NEED_TO_BUY)
        assert(cursor.getLong(cursor.getColumnIndexOrThrow("id")) == 1L)
        assert(cursor.getString(cursor.getColumnIndexOrThrow("kind")) == "NEED_TO_BUY")
        assert(cursor.getInt(cursor.getColumnIndexOrThrow("isActive")) == 0)

        // list 2 (finished list -> PURCHASE)
        assert(cursor.moveToNext())
        assert(cursor.getLong(cursor.getColumnIndexOrThrow("id")) == 2L)
        assert(cursor.getString(cursor.getColumnIndexOrThrow("kind")) == "PURCHASE")

        // list 3 (income list -> INCOME)
        assert(cursor.moveToNext())
        assert(cursor.getLong(cursor.getColumnIndexOrThrow("id")) == 3L)
        assert(cursor.getString(cursor.getColumnIndexOrThrow("kind")) == "INCOME")

        // list 4 (subscription list -> SUBSCRIPTION)
        assert(cursor.moveToNext())
        assert(cursor.getLong(cursor.getColumnIndexOrThrow("id")) == 4L)
        assert(cursor.getString(cursor.getColumnIndexOrThrow("kind")) == "SUBSCRIPTION")

        cursor.close()
    }
}
