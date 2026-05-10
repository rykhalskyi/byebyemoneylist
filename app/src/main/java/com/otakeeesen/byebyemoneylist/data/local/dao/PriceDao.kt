package com.otakeeesen.byebyemoneylist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Delete
import androidx.room.Query
import com.otakeeesen.byebyemoneylist.data.local.entity.PriceEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for PriceEntity operations.
 */
@Dao
interface PriceDao {
    
    @Query("SELECT * FROM prices")
    fun getAllPrices(): Flow<List<PriceEntity>>
    
    @Query("SELECT * FROM prices WHERE id = :id")
    fun getPriceById(id: Long): PriceEntity
    
    @Query("SELECT * FROM prices WHERE productId = :productId")
    fun getPricesForProduct(productId: Long): Flow<List<PriceEntity>>
    
    @Insert
    fun insertPrice(price: PriceEntity)
    
    @Update
    fun updatePrice(price: PriceEntity)
    
    @Delete
    fun deletePrice(price: PriceEntity)
}