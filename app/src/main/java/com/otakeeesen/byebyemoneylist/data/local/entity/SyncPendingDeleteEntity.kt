package com.otakeeesen.byebyemoneylist.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Entity type stored in the pending-delete queue. */
const val PENDING_DELETE_ENTITY_SHOPPING_LIST = "shopping_list"

/**
 * A delete that still needs to be propagated to the Nextcloud server.
 *
 * When a local row is removed, its `serverId` is captured here *before* the
 * row is deleted; the mirror sync drains the queue by issuing `DELETE` calls
 * and clearing each entry only after the server acknowledges it. Without this
 * queue the server twin would be orphaned as soon as the local `serverId` is
 * gone.
 */
@Entity(tableName = "sync_pending_deletes")
data class SyncPendingDeleteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entity: String,
    val serverId: String,
)
