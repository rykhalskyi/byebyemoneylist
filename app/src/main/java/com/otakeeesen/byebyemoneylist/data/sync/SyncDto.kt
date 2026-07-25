package com.otakeeesen.byebyemoneylist.data.sync

import kotlinx.serialization.Serializable

@Serializable
data class SyncListDto(
    val syncVersion: Int = 1,
    val syncId: String,
    val title: String,
    val lastModified: Long,
    val modifiedByUserId: String,
    val modifiedByDisplayName: String? = null,
    val isDeleted: Boolean = false,
    val items: List<SyncItemDto> = emptyList()
)

@Serializable
data class SyncItemDto(
    val itemId: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val quantity: Double,
    val checked: Boolean,
    val position: Int,
    val lastModified: Long
)

data class SyncFileMeta(
    val name: String,
    val lastModified: Long
)
