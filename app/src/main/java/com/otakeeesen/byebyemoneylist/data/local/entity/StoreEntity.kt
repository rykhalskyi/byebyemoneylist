package com.otakeeesen.byebyemoneylist.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a store where products can be purchased.
 *
 * @property id Unique identifier for the store
 * @property name Name of the store
 * @property logoPath Path to the store's logo image
 * @property category Category of the store
 */
@Entity(tableName = "stores")
data class StoreEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val logoPath: String?,
    val category: String,
    val address: String? = null,
    val receiptName: String? = null
)