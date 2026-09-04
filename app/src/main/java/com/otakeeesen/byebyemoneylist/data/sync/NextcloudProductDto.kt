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
