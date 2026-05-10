package com.otakeeesen.byebyemoneylist.data.local.repository

import com.otakeeesen.byebyemoneylist.data.local.database.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for product-related database operations.
 *
 * This repository provides a clean API for accessing product data
 * from the local database.
 */
class ProductRepository(private val database: AppDatabase) {
    
    // Product DAO operations
    fun getProducts(): Flow<List<ProductEntity>> {
        return database.productDao().getAllProducts()
    }
    
    fun getProductById(id: Long): ProductEntity {
        return database.productDao().getProductById(id)
    }
    
    // Add more repository methods as needed
}