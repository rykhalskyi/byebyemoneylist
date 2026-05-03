package com.otakeeesen.byebyemoneylist.data

data class ShoppingList(
    val id: Long,
    val title: String,
    val items: List<PurchaseItem>
)