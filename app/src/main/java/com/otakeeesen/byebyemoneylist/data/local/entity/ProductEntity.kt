package com.otakeeesen.byebyemoneylist.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Junction

/**
 * Entity representing a product in the database.
 */
@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ProductEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val barcode: String,
    val picturePath: String?,
    val category: String
) {
    constructor() : this(0, "", "", null, "")
}