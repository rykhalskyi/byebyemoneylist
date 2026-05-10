package com.otakeeesen.byebyemoneylist.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for StoreEntity operations.
 */
@Dao
interface StoreDao {
    
    @Query("SELECT * FROM stores")
    fun getAllStores(): Flow<List<StoreEntity>>
    
    @Query("SELECT * FROM stores WHERE id = :id")
    fun getStoreById(id: Long): StoreEntity
    
    @Query("INSERT INTO stores (id, name, logoPath, category) VALUES (:id, :name, :logoPath, :category)")
    fun insertStore(id: Long, name: String, logoPath: String?, category: String)
    
    @Query("UPDATE stores SET name = :name, logoPath = :logoPath, category = :category WHERE id = :id")
    fun updateStore(id: Long, name: String, logoPath: String?, category: String)
    
    @Query("DELETE FROM stores WHERE id = :id")
    fun deleteStore(id: Long)
}