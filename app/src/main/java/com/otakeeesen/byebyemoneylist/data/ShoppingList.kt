package com.otakeeesen.byebyemoneylist.data

data class ShoppingList(
    val id: Long,
    val title: String,
    val items: List<PurchaseItem>,
    val isFinished: Boolean = false,
    val finalTotal: Double? = null,
    val storeName: String? = null,
    val createDate: Long = 0L,
    val categoryName: String? = null,
    val categoryColor: String? = null,
    val position: Int = 0,
    val storeId: Long?,
    val categoryId: Long? = null,
) {
    val itemsTotal: Double
        get() = items.sumOf { it.price ?: 0.0 }

    val purchasePrice: Double
        get() = finalTotal ?: 0.0

    val actualPrice: Double
        get() = maxOf(itemsTotal, purchasePrice)

    val checkedCount: Int
        get() = items.count { it.checked }

    val totalCount: Int
        get() = items.size
}