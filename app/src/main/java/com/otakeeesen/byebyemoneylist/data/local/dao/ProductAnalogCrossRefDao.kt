package com.otakeeesen.byebyemoneylist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductAnalogCrossRef
import kotlinx.coroutines.flow.Flow

/**
 * DAO for product analog cross reference operations.
 */
@Dao
interface ProductAnalogCrossRefDao {
    
    @Query("SELECT * FROM product_analog_cross_ref")
    fun getAllProductAnalogCrossRefs(): Flow<List<ProductAnalogCrossRef>>
    
    @Query("SELECT * FROM product_analog_cross_ref WHERE productId = :productId")
    fun getAnalogRefsForProduct(productId: Long): List<ProductAnalogCrossRef>
    
    @Insert
    fun insertProductAnalogCrossRef(crossRef: ProductAnalogCrossRef)
    
    @Transaction
    fun insertProductAnalogCrossRefs(productId: Long, analogProductIds: List<Long>) {
        analogProductIds.forEach { analogId ->
            val crossRef = ProductAnalogCrossRef(productId, analogId)
            insertProductAnalogCrossRef(crossRef)
        }
    }
}