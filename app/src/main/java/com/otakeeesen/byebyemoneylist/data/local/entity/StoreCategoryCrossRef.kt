package com.otakeeesen.byebyemoneylist.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Cross-reference entity for many-to-many relationship between Store and Category.
 */
@Entity(
    tableName = "store_category_cross_ref",
    primaryKeys = ["storeId", "categoryId"],
    foreignKeys = [
        ForeignKey(
            entity = StoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["storeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["storeId"]),
        Index(value = ["categoryId"])
    ]
)
data class StoreCategoryCrossRef(
    val storeId: Long,
    val categoryId: Long
)
