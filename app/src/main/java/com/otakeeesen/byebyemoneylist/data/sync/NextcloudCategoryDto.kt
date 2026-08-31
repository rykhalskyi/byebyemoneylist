package com.otakeeesen.byebyemoneylist.data.sync

import kotlinx.serialization.Serializable

@Serializable
data class NextcloudCategoryDto(
    val id: String? = null,
    val name: String,
    val color: String? = null,
    val emoji: String? = null,
    val parentId: String? = null,
    val income: Boolean = false,
    val status: String? = null,
    val tempId: String? = null
)

@Serializable
data class NextcloudCategoriesResponse(
    val categories: List<NextcloudCategoryDto> = emptyList()
)

@Serializable
data class OcsDataWrapper<T>(
    val data: T
)

@Serializable
data class OcsResponseWrapper<T>(
    val ocs: OcsDataWrapper<T>
)

@Serializable
data class NextcloudBatchCategoriesRequest(
    val categories: List<NextcloudCategoryDto>
)
