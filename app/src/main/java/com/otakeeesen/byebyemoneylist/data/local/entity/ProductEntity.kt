package com.otakeeesen.byebyemoneylist.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Junction

/**
 * Entity representing a product in the database.
 */
@Entity(
    tableName = "products"
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val barcode: String,
    val picturePath: String?,
    val categoryId: Long? = null,
    val status: String = "reviewed", // "added", "reviewed", "barcode"
    val changedAt: Long = System.currentTimeMillis(),
    val isSubscription: Boolean = false,
    val isFavorite: Boolean = false,
    val isIncome: Boolean = false,
) {
    constructor() : this(0, "", "", null, null, "reviewed", System.currentTimeMillis(), false, false, false)
}