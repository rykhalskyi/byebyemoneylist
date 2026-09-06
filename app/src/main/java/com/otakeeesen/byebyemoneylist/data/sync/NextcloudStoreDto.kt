package com.otakeeesen.byebyemoneylist.data.sync

import kotlinx.serialization.Serializable

@Serializable
data class NextcloudStoreDto(
    val id: String? = null,
    val name: String
)

@Serializable
data class NextcloudStoresResponse(
    val stores: List<NextcloudStoreDto> = emptyList()
)

@Serializable
data class NextcloudStoreResponse(
    val store: NextcloudStoreDto? = null
)

@Serializable
data class NextcloudStoreCreateRequest(
    val name: String
)
