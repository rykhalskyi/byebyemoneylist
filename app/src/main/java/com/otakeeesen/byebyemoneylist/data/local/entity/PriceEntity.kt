package com.otakeeesen.byebyemoneylist.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Entity representing a price entry for a product at a specific store.
 *
 * @property id Unique identifier for the price entry
 * @property productId Foreign key reference to ProductEntity
 * @property storeId Foreign key reference to StoreEntity
 * @property value Price value
 * @property date Date when this price was recorded
 */
@Entity(
    tableName = "prices",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["storeId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PriceEntity(
    @PrimaryKey val id: Long,
    val productId: Long,
    val storeId: Long,
    val value: Double,
    val date: Long // timestamp
)