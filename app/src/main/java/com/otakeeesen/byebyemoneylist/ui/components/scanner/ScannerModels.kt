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
    val isCoupon: Boolean = false,
    val categorySuggestion: String? = null
)

data class ScannedReceipt(
    val storeName: String? = null,
    val storeAddress: String? = null,
    val items: List<ScannedItem> = emptyList(),
    val totalSum: Double? = null,
    val errorMessage: String? = null
)

fun ScannedReceipt.isLikelyIncomplete(): Boolean {
    if (errorMessage != null) return true
    if (items.isEmpty() && totalSum != null) return true
    val itemPricesSum = items.sumOf { (it.price * it.quantity) - (it.discount ?: 0.0) }
    if (totalSum != null && totalSum > itemPricesSum * 1.5 && items.size < 3) return true
    return false
}

interface ReceiptParser {
    suspend fun parse(bitmap: Bitmap, categories: List<String> = emptyList(), stores: List<String> = emptyList()): ScannedReceipt
    suspend fun parseMultiPart(bitmaps: List<Bitmap>, categories: List<String> = emptyList(), stores: List<String> = emptyList()): ScannedReceipt =
        if (bitmaps.isEmpty()) ScannedReceipt(errorMessage = "No image provided") else parse(bitmaps.first(), categories, stores)
}

@Serializable
data class ReceiptJson(
    val store_name: String? = null,
    val store_address: String? = null,
    val items: List<ItemJson> = emptyList(),
    val total_sum: Double? = null
)

@Serializable
data class ItemJson(
    val name: String,
    val quantity: Double,
    val price: Double,
    val discount: Double? = null,
    val isCoupon: Boolean? = false,
    val category: String? = null
)
