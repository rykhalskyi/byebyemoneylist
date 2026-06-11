package com.otakeeesen.byebyemoneylist

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: CategoryRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CategoryRepository(database)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun isCircularDependency_directLoop_returnsTrue() = runBlocking {
        assertTrue(repository.isCircularDependency(1L, 1L))
    }

    @Test
    fun isCircularDependency_nestedLoop_returnsTrue() = runBlocking {
        database.categoryDao().insertCategory(CategoryEntity(1L, "Cat 1", "#FFFFFF", null))
        database.categoryDao().insertCategory(CategoryEntity(2L, "Cat 2", "#FFFFFF", 1L))
        
        // Try to set 2 as parent of 1 -> loop 1 -> 2 -> 1
        assertTrue(repository.isCircularDependency(1L, 2L))
    }

    @Test
    fun isCircularDependency_deepNestedLoop_returnsTrue() = runBlocking {
        database.categoryDao().insertCategory(CategoryEntity(1L, "Cat 1", "#FFFFFF", null))
        database.categoryDao().insertCategory(CategoryEntity(2L, "Cat 2", "#FFFFFF", 1L))
        database.categoryDao().insertCategory(CategoryEntity(3L, "Cat 3", "#FFFFFF", 2L))
        database.categoryDao().insertCategory(CategoryEntity(4L, "Cat 4", "#FFFFFF", 3L))
        
        // Try to set 4 as parent of 1 -> loop 1 -> 2 -> 3 -> 4 -> 1
        assertTrue(repository.isCircularDependency(1L, 4L))
    }

    @Test
    fun isCircularDependency_validHierarchy_returnsFalse() = runBlocking {
        database.categoryDao().insertCategory(CategoryEntity(1L, "Cat 1", "#FFFFFF", null))
        database.categoryDao().insertCategory(CategoryEntity(2L, "Cat 2", "#FFFFFF", 1L))
        
        assertFalse(repository.isCircularDependency(3L, 2L))
    }

    @Test
    fun createDefaultCategories_addsCorrectCategories() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository.createDefaultCategories(context)
        
        val categories = repository.getAllCategoriesOnce()
        val names = categories.map { it.name }
        
        assertTrue(names.contains("Meat & Poultry"))
        assertTrue(names.contains("Seafood"))
        assertTrue(names.contains("Dairy"))
        assertTrue(names.contains("Eggs"))
        assertFalse(names.contains("Dairy & Eggs"))
        assertFalse(names.contains("Meat & Seafood"))

        val meat = categories.first { it.name == "Meat & Poultry" }
        val seafood = categories.first { it.name == "Seafood" }
        val dairy = categories.first { it.name == "Dairy" }
        val eggs = categories.first { it.name == "Eggs" }
        
        assertTrue(meat.color == com.otakeeesen.byebyemoneylist.data.local.entity.CategoryColors.RED)
        assertTrue(seafood.color == com.otakeeesen.byebyemoneylist.data.local.entity.CategoryColors.BLUE)
        assertTrue(dairy.color == com.otakeeesen.byebyemoneylist.data.local.entity.CategoryColors.YELLOW)
        assertTrue(eggs.color == com.otakeeesen.byebyemoneylist.data.local.entity.CategoryColors.YELLOW)
    }
}
