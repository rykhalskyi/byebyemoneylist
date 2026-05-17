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
    
    @Query("SELECT * FROM products")
    fun getAllProducts(): Flow<List<ProductEntity>>
    
    @Query("SELECT * FROM products WHERE id = :id")
    fun getProductById(id: Long): ProductEntity
    
    @Query("SELECT * FROM products WHERE barcode = :barcode")
    fun getProductByBarcode(barcode: String): ProductEntity?

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%'")
    fun searchProducts(query: String): Flow<List<ProductEntity>>
    
    @Insert
    fun insertProduct(product: ProductEntity)
    
    @Update
    fun updateProduct(product: ProductEntity)
    
    @Delete
    fun deleteProduct(product: ProductEntity)
    
    @Query("INSERT INTO products (id, name, barcode, picturePath, category) VALUES (:id, :name, :barcode, :picturePath, :category)")
    fun insertProduct(id: Long, name: String, barcode: String, picturePath: String?, category: String)
    
    @Query("UPDATE products SET name = :name, barcode = :barcode, picturePath = :picturePath, category = :category WHERE id = :id")
    fun updateProduct(id: Long, name: String, barcode: String, picturePath: String?, category: String)
    
    @Query("DELETE FROM products WHERE id = :id")
    fun deleteProduct(id: Long)
}