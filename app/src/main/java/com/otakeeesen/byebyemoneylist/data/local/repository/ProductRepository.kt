package com.otakeeesen.byebyemoneylist.data.local.repository

import androidx.room.Transaction
import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductAliasEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.util.ImageStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ProductRepository(private val database: AppDatabase) {

    fun getProducts(isSubscription: Boolean? = null, isIncome: Boolean? = null): Flow<List<ProductEntity>> {
        return when {
            isSubscription != null -> database.productDao().getProductsBySubscription(isSubscription)
            isIncome != null -> database.productDao().getProductsByIncome(isIncome)
            else -> database.productDao().getAllProducts()
        }
    }

    suspend fun getAllProductsOnce(): List<ProductEntity> {
        return withContext(Dispatchers.IO) {
            database.productDao().getAllProductsOnce()
        }
    }

    suspend fun getProductById(id: Long): ProductEntity? {
        return withContext(Dispatchers.IO) {
            database.productDao().getProductById(id)
        }
    }

    suspend fun getProductsByIds(ids: List<Long>): List<ProductEntity> {
        return withContext(Dispatchers.IO) {
            database.productDao().getProductsByIds(ids)
        }
    }

    fun searchProducts(query: String, isSubscription: Boolean? = null, isIncome: Boolean? = null): Flow<List<ProductEntity>> {
        return when {
            isSubscription != null -> database.productDao().searchProductsBySubscription(query, isSubscription)
            isIncome != null -> database.productDao().searchProductsByIncome(query, isIncome)
            else -> database.productDao().searchProducts(query)
        }
    }

    suspend fun insertProduct(product: ProductEntity) {
        database.productDao().insertProduct(product)
    }

    suspend fun updateProduct(product: ProductEntity) {
        database.productDao().updateProduct(product)
    }

    fun deleteProduct(product: ProductEntity) {
        database.productDao().deleteProduct(product)
    }

    fun updateFavoriteStatus(productId: Long, isFavorite: Boolean) {
        database.productDao().updateFavoriteStatus(productId, isFavorite)
    }

    @Transaction
    suspend fun mergeProducts(productA: ProductEntity, productB: ProductEntity, resultProduct: ProductEntity) {
        // 1. Update Product A with chosen result fields
        database.productDao().updateProduct(resultProduct)

        // 2. Remap all references from B to A
        database.priceDao().remapProductPrices(productB.id, productA.id)
        database.productAliasDao().remapProductAliases(productB.id, productA.id)
        database.shoppingListDao().remapProductInShoppingLists(productB.id, productA.id)
        database.productAnalogCrossRefDao().remapProductAnalogs(productB.id, productA.id)
        database.productAnalogCrossRefDao().remapAnalogProductAnalogs(productB.id, productA.id)
        
        // 3. Cleanup: remove self-referencing analogs that might have been created
        database.productAnalogCrossRefDao().removeSelfAnalogs(productA.id)

        // 4. Delete Product B
        database.productDao().deleteProductById(productB.id)

        // 5. Delete Product B's image if it's different from Product A's result image
        if (productB.picturePath != null && productB.picturePath != resultProduct.picturePath) {
            ImageStorageManager.deleteImage(productB.picturePath)
        }
    }

    fun getProductsByCategory(categoryId: Long): Flow<List<ProductEntity>> {
        return database.productDao().getProductsByCategory(categoryId)
    }

    suspend fun getProductByBarcode(barcode: String): ProductEntity? {
        return withContext(Dispatchers.IO) {
            database.productDao().getProductByBarcode(barcode)
        }
    }

    fun getAllAliases(): Flow<List<ProductAliasEntity>> {
        return database.productAliasDao().getAllAliases()
    }

    suspend fun getAliasesByProductId(productId: Long): List<ProductAliasEntity> {
        return withContext(Dispatchers.IO) {
            database.productAliasDao().getAliasesByProductId(productId)
        }
    }

    suspend fun findBestAliasMatch(aliasName: String, storeId: Long?): ProductAliasEntity? {
        return withContext(Dispatchers.IO) {
            database.productAliasDao().findBestMatch(aliasName, storeId)
        }
    }

    suspend fun insertAlias(alias: ProductAliasEntity) {
        withContext(Dispatchers.IO) {
            database.productAliasDao().insertAlias(alias)
        }
    }

    suspend fun deleteAlias(alias: ProductAliasEntity) {
        withContext(Dispatchers.IO) {
            database.productAliasDao().deleteAlias(alias)
        }
    }
    }