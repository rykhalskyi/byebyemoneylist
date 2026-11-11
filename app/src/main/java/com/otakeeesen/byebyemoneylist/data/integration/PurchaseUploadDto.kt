package com.otakeeesen.byebyemoneylist.data.integration

import kotlinx.serialization.Serializable

const val PURCHASE_UPLOAD_SCHEMA = "bbml.purchase-upload/1"

@Serializable
data class PurchaseUploadRequest(
    val schema: String = PURCHASE_UPLOAD_SCHEMA,
    val clientId: String? = null,
    val listId: String,
    val list: PurchaseUploadList,
    val items: List<PurchaseUploadItem> = emptyList(),
)

@Serializable
data class PurchaseUploadList(
    val name: String,
    val createdAt: String? = null,
    val purchaseDate: String? = null,
    val currency: String? = null,
    val finalTotal: Double? = null,
    val store: PurchaseUploadStore? = null,
    val categories: List<PurchaseUploadCategory> = emptyList(),
)

@Serializable
data class PurchaseUploadStore(
    val name: String,
    val address: String? = null,
    val receiptName: String? = null,
)

@Serializable
data class PurchaseUploadCategory(
    val name: String,
    val isIncome: Boolean = false,
)

@Serializable
data class PurchaseUploadItem(
    val lineId: String,
    val name: String,
    val quantity: Double,
    val unitPrice: Double? = null,
    val discount: Double? = null,
    val isCoupon: Boolean = false,
    val barcode: String? = null,
    val categoryHint: String? = null,
)

@Serializable
data class PurchaseImportUploadResponse(
    val import: PurchaseImportSummary? = null,
    val duplicate: Boolean = false,
)

@Serializable
data class PurchaseImportSummary(
    val id: String? = null,
    val clientListId: String? = null,
    val status: String? = null,
)

data class PurchaseUploadApiResult(
    val importId: String?,
    val alreadyImported: Boolean,
)
