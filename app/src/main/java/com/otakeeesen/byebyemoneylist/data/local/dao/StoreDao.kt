package com.otakeeesen.byebyemoneylist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreDao {

    @Query("SELECT * FROM stores")
    fun getAllStores(): Flow<List<StoreEntity>>

    @Query("SELECT * FROM stores")
    fun getAllStoresOnce(): List<StoreEntity>

    @Query("SELECT * FROM stores WHERE id = :id")
    fun getStoreById(id: Long): StoreEntity?

    @Query("SELECT * FROM stores WHERE name = :name LIMIT 1")
    fun getStoreByName(name: String): StoreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertStore(store: StoreEntity)

    @Query("DELETE FROM stores WHERE id = :id")
    fun deleteStore(id: Long)
}
