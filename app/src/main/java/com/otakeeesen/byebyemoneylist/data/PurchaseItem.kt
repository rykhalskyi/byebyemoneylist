package com.otakeeesen.byebyemoneylist.data

data class PurchaseItem(
    val id: Long,
    val productId: Long,
    val name: String,
    val price: Double?,
    val imageUrl: String,
    val checked: Boolean,
    val position: Int = 0,
)