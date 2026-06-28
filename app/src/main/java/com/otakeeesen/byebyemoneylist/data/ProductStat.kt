package com.otakeeesen.byebyemoneylist.data

data class ProductStat(
    val productId: Long,
    val name: String,
    val quantity: Double,
    val totalSpent: Double,
    val categoryId: Long?
)
