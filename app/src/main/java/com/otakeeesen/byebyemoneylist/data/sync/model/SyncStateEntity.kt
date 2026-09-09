package com.otakeeesen.byebyemoneylist.data.sync.model

import androidx.room.Entity
import androidx.room.Index

/**
 * Last-synced snapshot for one linked local/server pair (the git-like "base").
 *
 * [baseSnapshot] holds the canonical projection JSON of the shared fields at the
 * moment the pair was last synced. Change detection compares the current local and
 * server projections against it — no server timestamps needed.
 */
@Entity(
    tableName = "sync_state",
    primaryKeys = ["entityType", "localId"],
    indices = [Index(value = ["entityType", "serverId"], name = "index_sync_state_serverId")]
)
data class SyncStateEntity(
    val entityType: String,
    val localId: Long,
    val serverId: String,
    val baseSnapshot: String,
    val lastSyncAt: Long
) {
    companion object {
        const val TYPE_CATEGORY = "category"
        const val TYPE_STORE = "store"
        const val TYPE_PRODUCT = "product"
        const val TYPE_SHOPPING_LIST = "shopping_list"
    }
}
