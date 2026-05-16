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
    primaryKeys = ["productId", "analogProductId"],
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["analogProductId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ProductAnalogCrossRef(
    val productId: Long,
    val analogProductId: Long
)