package com.otakeeesen.byebyemoneylist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreCategoryCrossRef
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

    @Query("SELECT * FROM stores WHERE serverId = :serverId LIMIT 1")
    fun getByServerId(serverId: String): StoreEntity?

    @Query("UPDATE stores SET serverId = :serverId WHERE id = :id")
    fun updateServerId(id: Long, serverId: String)

    @Query(
        "SELECT s.id FROM stores s " +
            "LEFT JOIN shopping_lists sl ON sl.storeId = s.id " +
            "GROUP BY s.id " +
            "ORDER BY MAX(COALESCE(sl.purchaseDate, sl.createDate)) DESC, s.id"
    )
    fun getStoreIdsByRecency(): List<Long>

    @Query(
        "SELECT s.id FROM stores s " +
            "LEFT JOIN shopping_lists sl ON sl.storeId = s.id " +
            "GROUP BY s.id " +
            "ORDER BY COUNT(sl.id) DESC, s.id"
    )
    fun getStoreIdsByFrequency(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertStore(store: StoreEntity)

    @Update
    fun updateStore(store: StoreEntity)

    @Query("DELETE FROM stores WHERE id = :id")
    fun deleteStore(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertStoreCategoryCrossRef(crossRef: StoreCategoryCrossRef)

    @Query("DELETE FROM store_category_cross_ref WHERE storeId = :storeId")
    fun deleteCategoriesForStore(storeId: Long)

    @Query("DELETE FROM store_category_cross_ref WHERE storeId = :targetStoreId AND categoryId IN (SELECT categoryId FROM store_category_cross_ref WHERE storeId = :sourceStoreId)")
    fun deleteConflictingStoreCategoryCrossRefs(sourceStoreId: Long, targetStoreId: Long)

    @Query("UPDATE store_category_cross_ref SET storeId = :targetStoreId WHERE storeId = :sourceStoreId")
    fun remapStoreCategoryCrossRefs(sourceStoreId: Long, targetStoreId: Long)

    @Query("SELECT * FROM store_category_cross_ref")
    fun getAllStoreCategoryCrossRefs(): Flow<List<StoreCategoryCrossRef>>
}
