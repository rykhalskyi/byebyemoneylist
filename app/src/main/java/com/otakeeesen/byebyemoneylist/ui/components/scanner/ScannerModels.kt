package com.otakeeesen.byebyemoneylist.ui.components.scanner

import android.graphics.Bitmap

import kotlinx.serialization.Serializable

data class ScannedItem(
    val name: String,
    val quantity: Double,
    val price: Double,
    val pricePerUnit: Double? = null,
    val productId: Long? = null,
    val barcode: String? = null,
    val discount: Double? = null,
    val isCoupon: Boolean = false
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

@Serializable
data class ReceiptJson(
    val store_name: String? = null,
    val items: List<ItemJson> = emptyList(),
    val total_sum: Double? = null
)

@Serializable
data class ItemJson(
    val name: String,
    val quantity: Double,
    val price: Double,
    val discount: Double? = null,
    val isCoupon: Boolean? = false
)
