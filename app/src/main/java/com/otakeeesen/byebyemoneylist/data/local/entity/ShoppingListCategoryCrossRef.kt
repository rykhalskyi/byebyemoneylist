package com.otakeeesen.byebyemoneylist.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Cross-reference entity for many-to-many relationship between ShoppingList and Category.
 */
@Entity(
    tableName = "shopping_list_category_cross_ref",
    primaryKeys = ["shoppingListId", "categoryId"],
    foreignKeys = [
        ForeignKey(
            entity = ShoppingListEntity::class,
            parentColumns = ["id"],
            childColumns = ["shoppingListId"],
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
        Index(value = ["shoppingListId"]),
        Index(value = ["categoryId"])
    ]
)
data class ShoppingListCategoryCrossRef(
    val shoppingListId: Long,
    val categoryId: Long
)
