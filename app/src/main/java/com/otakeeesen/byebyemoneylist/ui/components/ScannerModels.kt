package com.otakeeesen.byebyemoneylist.ui.components

import android.graphics.Bitmap

data class ScannedItem(
    val name: String,
    val quantity: Double,
    val price: Double,
    val pricePerUnit: Double? = null,
    val productId: Long? = null,
    val barcode: String? = null
)

data class ScannedReceipt(
    val storeName: String? = null,
    val storeAddress: String? = null,
    val items: List<ScannedItem> = emptyList(),
    val totalSum: Double? = null,
    val errorMessage: String? = null
)

interface ReceiptParser {
    suspend fun parse(bitmap: Bitmap): ScannedReceipt
}
