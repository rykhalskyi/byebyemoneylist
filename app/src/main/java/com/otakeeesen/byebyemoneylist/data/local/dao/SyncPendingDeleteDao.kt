package com.otakeeesen.byebyemoneylist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.otakeeesen.byebyemoneylist.data.local.entity.SyncPendingDeleteEntity

/**
 * Queue of local deletes that still need to reach the Nextcloud server.
 * Populated when a mirrored row is deleted locally; drained by the mirror
 * sync repositories for the matching [SyncPendingDeleteEntity.entity].
 */
@Dao
interface SyncPendingDeleteDao {

    @Query("SELECT * FROM sync_pending_deletes WHERE entity = :entity ORDER BY id ASC")
    fun getAllByEntity(entity: String): List<SyncPendingDeleteEntity>

    @Query("SELECT * FROM sync_pending_deletes ORDER BY id ASC")
    fun getAll(): List<SyncPendingDeleteEntity>

    @Insert
    fun insert(pendingDelete: SyncPendingDeleteEntity)

    @Query("DELETE FROM sync_pending_deletes WHERE id = :id")
    fun deleteById(id: Long)
}
