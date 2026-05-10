package com.otakeeesen.byebyemoneylist.data

data class ShoppingList(
    val id: Long,
    val title: String,
    val items: List<PurchaseItem>,
    val isFinished: Boolean = false,
    val finalTotal: Double? = null
) {
    val estimatedTotal: Double
        get() = items.sumOf { it.price }
}