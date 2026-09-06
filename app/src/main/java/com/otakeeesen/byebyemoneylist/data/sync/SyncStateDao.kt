package com.otakeeesen.byebyemoneylist.data.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncStateEntity

@Dao
interface SyncStateDao {

    @Query("SELECT * FROM sync_state WHERE entityType = :entityType AND localId = :localId")
    fun get(entityType: String, localId: Long): SyncStateEntity?

    @Query("SELECT * FROM sync_state WHERE entityType = :entityType AND serverId = :serverId")
    fun getByServerId(entityType: String, serverId: String): SyncStateEntity?

    @Query("SELECT * FROM sync_state WHERE entityType = :entityType")
    fun getAll(entityType: String): List<SyncStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(state: SyncStateEntity)

    @Query("DELETE FROM sync_state WHERE entityType = :entityType AND localId = :localId")
    fun delete(entityType: String, localId: Long)

    @Query("DELETE FROM sync_state WHERE entityType = :entityType AND serverId = :serverId")
    fun deleteByServerId(entityType: String, serverId: String)
}
