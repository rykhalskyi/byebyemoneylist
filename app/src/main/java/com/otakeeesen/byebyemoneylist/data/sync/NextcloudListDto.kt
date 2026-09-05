package com.otakeeesen.byebyemoneylist.data.sync

import kotlinx.serialization.Serializable

/**
 * A shopping list as serialized by the Nextcloud server
 * (`GET/POST/PUT /api/lists`). Dates arrive as ISO-8601 (ATOM) strings.
 */
@Serializable
data class NextcloudListDto(
    val id: String? = null,
    val name: String = "",
    val storeId: String? = null,
    val categoryId: String? = null,
    val categoryIds: List<String> = emptyList(),
    val status: String? = null,
    val finalTotal: Double? = null,
    val totalPrice: Double? = null,
    val createdAt: String? = null,
    val createDate: String? = null,
    val updatedAt: String? = null,
    val purchaseDate: String? = null,
    val position: Int = 0,
    val isFinished: Boolean = false,
    val isSubscription: Boolean = false,
    val isIncome: Boolean = false,
    val isRecurring: Boolean = false,
    val recurringPeriod: String = "MONTH",
    val isForwardEmpty: Boolean = true,
)

@Serializable
data class NextcloudListsResponse(
    val lists: List<NextcloudListDto> = emptyList()
)

@Serializable
data class NextcloudListResponse(
    val list: NextcloudListDto? = null
)

/**
 * A shopping list item as serialized by the Nextcloud server
 * (`GET/POST/PUT/DELETE /api/lists/{id}/items`). Items have no independent
 * identity in the mirror (no per-item serverId) — the client full-replaces the
 * item set on push.
 */
@Serializable
data class NextcloudListItemDto(
    val id: String? = null,
    val listId: String? = null,
    val productId: String? = null,
    val productName: String? = null,
    val price: Double? = null,
    val quantity: Double = 1.0,
    val isChecked: Boolean = false,
    val position: Int = 0,
    val discount: Double? = null,
    val customName: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class NextcloudListItemsResponse(
    val items: List<NextcloudListItemDto> = emptyList()
)

@Serializable
data class NextcloudListItemResponse(
    val item: NextcloudListItemDto? = null
)

/**
 * Full state needed to create a list on the server (client-authoritative).
 * Dates are ISO-8601; `createDate` preserves the client's original creation
 * date instead of letting the server stamp "now".
 */
@Serializable
data class NextcloudListCreateRequest(
    val name: String,
    val storeId: String? = null,
    val categoryIds: List<String> = emptyList(),
    val position: Int = 0,
    val purchaseDate: String? = null,
    val isFinished: Boolean = false,
    val finalTotal: Double? = null,
    val createDate: String? = null,
    val isRecurring: Boolean = false,
    val recurringPeriod: String = "MONTH",
    val isForwardEmpty: Boolean = true,
    val isSubscription: Boolean = false,
    val isIncome: Boolean = false,
)

/**
 * Full state push for an existing list (`PUT /api/lists/{id}`). Nullable
 * scalars (`storeId`, `purchaseDate`, `finalTotal`) are sent explicitly so the
 * server clears them; this request is encoded with `encodeDefaults = true`.
 */
@Serializable
data class NextcloudListUpdateRequest(
    val name: String,
    val storeId: String? = null,
    val categoryIds: List<String> = emptyList(),
    val position: Int = 0,
    val purchaseDate: String? = null,
    val finalTotal: Double? = null,
    val isFinished: Boolean = false,
    val isRecurring: Boolean = false,
    val recurringPeriod: String = "MONTH",
    val isForwardEmpty: Boolean = true,
    val isSubscription: Boolean = false,
    val isIncome: Boolean = false,
)

@Serializable
data class NextcloudListItemCreateRequest(
    val productId: String,
    val price: Double? = null,
    val quantity: Double = 1.0,
    val position: Int = 0,
    val discount: Double? = null,
    val customName: String? = null,
)

@Serializable
data class NextcloudListItemUpdateRequest(
    val price: Double? = null,
    val quantity: Double? = null,
    val position: Int? = null,
    val discount: Double? = null,
    val isChecked: Boolean? = null,
    val customName: String? = null,
)
