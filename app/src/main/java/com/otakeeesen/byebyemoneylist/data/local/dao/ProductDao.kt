package com.otakeeesen.byebyemoneylist.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Delete
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for ProductEntity operations.
 */
@Dao
interface ProductDao {
    
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE isSubscription = :isSubscription ORDER BY name ASC")
    fun getProductsBySubscription(isSubscription: Boolean): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE isIncome = :isIncome ORDER BY name ASC")
    fun getProductsByIncome(isIncome: Boolean): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE isSubscription = 0 AND isIncome = 0 ORDER BY name ASC")
    fun getNormalProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProductsOnce(): List<ProductEntity>
    
    @Query("SELECT * FROM products WHERE id = :id")
    fun getProductById(id: Long): ProductEntity?
    
    @Query("SELECT * FROM products WHERE barcode = :barcode")
    fun getProductByBarcode(barcode: String): ProductEntity?

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchProducts(query: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE (name LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%') AND isSubscription = :isSubscription ORDER BY name ASC")
    fun searchProductsBySubscription(query: String, isSubscription: Boolean): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE (name LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%') AND isIncome = :isIncome ORDER BY name ASC")
    fun searchProductsByIncome(query: String, isIncome: Boolean): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE (name LIKE '%' || :query || '%' OR barcode LIKE '%' || :query || '%') AND isSubscription = 0 AND isIncome = 0 ORDER BY name ASC")
    fun searchNormalProducts(query: String): Flow<List<ProductEntity>>
    
    @Query("SELECT * FROM products WHERE id IN (:ids)")

    fun getProductsByIds(ids: List<Long>): List<ProductEntity>

    @Query("SELECT categoryId FROM products WHERE id = :id")
    fun getCategoryIdForProduct(id: Long): Long?

    @Query("UPDATE products SET isFavorite = :isFavorite WHERE id = :productId")
    fun updateFavoriteStatus(productId: Long, isFavorite: Boolean)

    @Insert
    fun insertProduct(product: ProductEntity)
    
    @Update
    fun updateProduct(product: ProductEntity)
    
    @Delete
    fun deleteProduct(product: ProductEntity)
    
    @Query("SELECT * FROM products WHERE categoryId = :categoryId ORDER BY name ASC")
    fun getProductsByCategory(categoryId: Long): Flow<List<ProductEntity>>

    @Query("INSERT INTO products (id, name, barcode, picturePath, categoryId, isFavorite) VALUES (:id, :name, :barcode, :picturePath, :categoryId, :isFavorite)")
    fun insertProductDetails(id: Long, name: String, barcode: String, picturePath: String?, categoryId: Long?, isFavorite: Boolean)
    
    @Query("UPDATE products SET name = :name, barcode = :barcode, picturePath = :picturePath, categoryId = :categoryId, isFavorite = :isFavorite WHERE id = :id")
    fun updateProductDetails(id: Long, name: String, barcode: String, picturePath: String?, categoryId: Long?, isFavorite: Boolean)
    
    @Query("DELETE FROM products WHERE id = :id")
    fun deleteProductByLongId(id: Long)

    @Query("DELETE FROM products WHERE id = :id")
    fun deleteProductById(id: Long)
}