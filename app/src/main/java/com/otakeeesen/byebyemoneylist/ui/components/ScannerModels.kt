package com.otakeeesen.byebyemoneylist.ui.components

import android.graphics.Bitmap

data class ScannedItem(
    val name: String,
    val quantity: Double,
    val price: Double
)

data class ScannedReceipt(
    val storeName: String? = null,
    val items: List<ScannedItem> = emptyList(),
    val totalSum: Double? = null
)

interface ReceiptParser {
    suspend fun parse(bitmap: Bitmap): ScannedReceipt
}
