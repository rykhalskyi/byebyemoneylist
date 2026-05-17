package com.otakeeesen.byebyemoneylist.data.local.repository

import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

class ProductRepository(private val database: AppDatabase) {

    fun getProducts(): Flow<List<ProductEntity>> {
        return database.productDao().getAllProducts()
    }

    fun getProductById(id: Long): ProductEntity {
        return database.productDao().getProductById(id)
    }

    fun searchProducts(query: String): Flow<List<ProductEntity>> {
        return database.productDao().searchProducts(query)
    }

    suspend fun insertProduct(product: ProductEntity) {
        database.productDao().insertProduct(product)
    }
}