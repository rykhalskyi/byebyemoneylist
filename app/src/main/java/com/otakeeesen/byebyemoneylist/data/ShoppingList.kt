package com.otakeeesen.byebyemoneylist.data

import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity

data class ShoppingList(
    val id: Long,
    val title: String,
    val items: List<PurchaseItem>,
    val isFinished: Boolean = false,
    val finalTotal: Double? = null,
    val storeName: String? = null,
    val createDate: Long = 0L,
    val categories: List<CategoryEntity> = emptyList(),
    val position: Int = 0,
    val storeId: Long?,
    val purchaseDate: Long? = null,
    val isRecurring: Boolean = false,
    val recurringPeriod: String = "MONTH",
    val isForwardEmpty: Boolean = true,
    val isArchived: Boolean = false,
    val isSubscription: Boolean = false,
) {
    val itemsTotal: Double
        get() = items.filter { (it.checked || isSubscription) && it.quantity > 0 }.sumOf { ((it.price ?: 0.0) * it.quantity) - (it.discount ?: 0.0) }

    val purchasePrice: Double
        get() = finalTotal ?: 0.0

    fun calculateActualPrice(rule: String): Double {
        return when (rule) {
            "BIGGER_VALUE" -> maxOf(itemsTotal, purchasePrice)
            else -> { // PURCHASE_PRICE
                if (purchasePrice == 0.0) itemsTotal else purchasePrice
            }
        }
    }

    val actualPrice: Double
        get() = maxOf(itemsTotal, purchasePrice)

    val sortDate: Long
        get() = purchaseDate ?: createDate

    val checkedCount: Int
        get() = if (isSubscription) items.size else items.count { it.checked }

    val totalCount: Int
        get() = items.size
}