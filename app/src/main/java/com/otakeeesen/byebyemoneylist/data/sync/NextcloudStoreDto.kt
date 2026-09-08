package com.otakeeesen.byebyemoneylist.data.sync

import kotlinx.serialization.Serializable

@Serializable
data class NextcloudStoreDto(
    val id: String? = null,
    val name: String,
    val address: String? = null,
    val categoryIds: List<String> = emptyList()
)

@Serializable
data class NextcloudStoresResponse(
    val stores: List<NextcloudStoreDto> = emptyList()
)

@Serializable
data class NextcloudStoreResponse(
    val store: NextcloudStoreDto? = null
)

/**
 * Full-state store payload shared by create (POST) and update (PUT). A null/blank
 * `address` clears the server field; `categoryIds` is fully replaced on the server.
 */
@Serializable
data class NextcloudStoreCreateRequest(
    val name: String,
    val address: String? = null,
    val categoryIds: List<String> = emptyList()
)
