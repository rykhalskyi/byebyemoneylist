package com.otakeeesen.byebyemoneylist.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Junction

/**
 * Entity representing a product analog cross reference in the database.
 */
@Entity(
    tableName = "product_analog_cross_ref",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ProductAnalogCrossRef(
    @PrimaryKey val id: Long,
    val productId: Long,
    val analogProductId: Long
) {
    constructor() : this(0, 0, 0)
}