package com.otakeeesen.byebyemoneylist.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductAliasEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductAliasDao {
    @Query("SELECT * FROM product_aliases")
    fun getAllAliases(): Flow<List<ProductAliasEntity>>

    @Query("SELECT * FROM product_aliases WHERE aliasName = :aliasName")
    fun getAliasesByName(aliasName: String): List<ProductAliasEntity>

    @Query("SELECT * FROM product_aliases WHERE productId = :productId")
    fun getAliasesByProductId(productId: Long): List<ProductAliasEntity>

    @Query("SELECT * FROM product_aliases WHERE aliasName = :aliasName AND (storeId = :storeId OR storeId IS NULL) LIMIT 1")
    fun findBestMatch(aliasName: String, storeId: Long?): ProductAliasEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAlias(alias: ProductAliasEntity)

    @Update
    fun updateAlias(alias: ProductAliasEntity)

    @Delete
    fun deleteAlias(alias: ProductAliasEntity)
}
