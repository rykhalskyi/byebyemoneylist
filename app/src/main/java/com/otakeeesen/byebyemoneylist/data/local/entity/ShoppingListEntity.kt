package com.otakeeesen.byebyemoneylist.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

/**
 * Entity representing a shopping list.
 *
 * @property id Unique identifier for the shopping list
 * @property name Name of the shopping list
 * @property createDate Timestamp when the list was created
 * @property purchaseDate Timestamp when the shopping was done
 * @property storeId Foreign key reference to StoreEntity
 * @property categoryId Foreign key reference to CategoryEntity
 * @property isFinished Whether the list is finished
 * @property finalTotal Final total amount
 * @property position Position for ordering within the list
 */
@Entity(
    tableName = "shopping_lists",
    foreignKeys = [
        ForeignKey(
            entity = StoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["storeId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ShoppingListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createDate: Long, // timestamp
    val purchaseDate: Long?, // timestamp
    val storeId: Long?,
    val isFinished: Boolean = false,
    val finalTotal: Double? = null,
    val position: Int = 0,
    val isRecurring: Boolean = false,
    val recurringPeriod: String = "MONTH",
    val isForwardEmpty: Boolean = true,
    val isArchived: Boolean = false,
    val isSubscription: Boolean = false,
    val isIncome: Boolean = false,
    val isShared: Boolean = false,
    val syncId: String? = null,
    val lastSyncTimestamp: Long = 0,
    val lastModifiedAt: Long = 0,
)

/**
  * Entity representing an item in a shopping list.
  *
  * @property id Unique identifier for the shopping list item
  * @property shoppingListId Foreign key reference to ShoppingListEntity
  * @property productId Foreign key reference to ProductEntity
  * @property quantity Quantity of the product in the shopping list
  * @property isChecked Whether the item is checked off the list
  * @property price Optional custom price for this item (if null, use product's latest price)
  */
@Entity(tableName = "shopping_list_items")
data class ShoppingListItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shoppingListId: Long,
    val productId: Long,
    val quantity: Double,
    val isChecked: Boolean,
    val position: Int = 0,
    val price: Double? = null,
    val discount: Double? = null,
    val customName: String? = null,
)