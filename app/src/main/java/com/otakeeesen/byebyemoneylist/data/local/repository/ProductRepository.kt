package com.otakeeesen.byebyemoneylist.data.local.repository

import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductAliasEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

class ProductRepository(private val database: AppDatabase) {

    fun getProducts(): Flow<List<ProductEntity>> {
        return database.productDao().getAllProducts()
    }

    suspend fun getAllProductsOnce(): List<ProductEntity> {
        return database.productDao().getAllProductsOnce()
    }

    fun getProductById(id: Long): ProductEntity? {
        return database.productDao().getProductById(id)
    }

    fun searchProducts(query: String): Flow<List<ProductEntity>> {
        return database.productDao().searchProducts(query)
    }

    suspend fun insertProduct(product: ProductEntity) {
        database.productDao().insertProduct(product)
    }

    suspend fun updateProduct(product: ProductEntity) {
        database.productDao().updateProduct(product)
    }

    suspend fun deleteProduct(product: ProductEntity) {
        database.productDao().deleteProduct(product)
    }

    fun getProductsByCategory(category: String): Flow<List<ProductEntity>> {
        return database.productDao().getProductsByCategory(category)
    }

    fun getProductByBarcode(barcode: String): ProductEntity? {
        return database.productDao().getProductByBarcode(barcode)
    }

    fun getAllAliases(): Flow<List<ProductAliasEntity>> {
        return database.productAliasDao().getAllAliases()
    }

    suspend fun findBestAliasMatch(aliasName: String, storeId: Long?): ProductAliasEntity? {
        return database.productAliasDao().findBestMatch(aliasName, storeId)
    }

    suspend fun insertAlias(alias: ProductAliasEntity) {
        database.productAliasDao().insertAlias(alias)
    }
}