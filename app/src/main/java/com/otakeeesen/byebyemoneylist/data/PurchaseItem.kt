package com.otakeeesen.byebyemoneylist.data

data class PurchaseItem(
    val id: Long,
    val productId: Long,
    val name: String,
    val price: Double?,
    val quantity: Double = 1.0,
    val imageUrl: String,
    val checked: Boolean,
    val position: Int = 0,
    val productStatus: String? = null,
)