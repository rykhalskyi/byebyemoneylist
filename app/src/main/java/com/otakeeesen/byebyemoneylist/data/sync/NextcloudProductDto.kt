package com.otakeeesen.byebyemoneylist.data.sync

import kotlinx.serialization.Serializable

@Serializable
data class NextcloudProductDto(
    val id: String? = null,
    val name: String,
    val barcode: String? = null,
    val categoryId: String? = null,
    val aliases: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val status: String? = null,
    val isSubscription: Boolean = false,
    val isIncome: Boolean = false
)

@Serializable
data class NextcloudProductsResponse(
    val products: List<NextcloudProductDto> = emptyList()
)

@Serializable
data class NextcloudProductResponse(
    val product: NextcloudProductDto? = null
)

@Serializable
data class NextcloudProductCreateRequest(
    val name: String,
    val categoryId: String? = null,
    val barcode: String? = null,
    val aliases: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val isSubscription: Boolean = false,
    val isIncome: Boolean = false
)

/**
 * A price record the user recorded for a product on a given date (optionally at a
 * store). The server keys each record by (product, store), matching the app's
 * one-current-price-per-store model, so re-pushing updates instead of duplicating.
 */
@Serializable
data class NextcloudProductPriceDto(
    val id: String? = null,
    val productId: String? = null,
    val storeId: String? = null,
    val value: Double = 0.0,
    val date: String? = null,
    val createdAt: String? = null
)

@Serializable
data class NextcloudProductPricesResponse(
    val prices: List<NextcloudProductPriceDto> = emptyList()
)

@Serializable
data class NextcloudProductPriceCreateRequest(
    val productId: String,
    val storeId: String? = null,
    val value: Double,
    val date: String
)

@Serializable
data class NextcloudProductPricesCreateRequest(
    val prices: List<NextcloudProductPriceCreateRequest>
)
