package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.sync.model.SyncContentState
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncMatch
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncStateEntity

/**
 * Git-like 3-way change detection. For a linked pair with a stored base snapshot,
 * compares the current local and server canonical projections against the base:
 *
 * | local vs base | server vs base | state |
 * |---|---|---|
 * | equal | equal | IN_SYNC |
 * | changed | equal | LOCAL_CHANGED (push) |
 * | equal | changed | SERVER_CHANGED (pull) |
 * | changed | changed, same change | IN_SYNC (converged edit) |
 * | changed | changed, different | CONFLICT |
 */
object SyncStateResolver {

    fun resolveState(baseJson: String?, localJson: String, serverJson: String): SyncContentState {
        if (baseJson == null) return SyncContentState.IN_SYNC
        val localChanged = hash(localJson) != hash(baseJson)
        val serverChanged = hash(serverJson) != hash(baseJson)
        return when {
            !localChanged && !serverChanged -> SyncContentState.IN_SYNC
            localChanged && !serverChanged -> SyncContentState.LOCAL_CHANGED
            !localChanged && serverChanged -> SyncContentState.SERVER_CHANGED
            localJson == serverJson -> SyncContentState.IN_SYNC
            else -> SyncContentState.CONFLICT
        }
    }

    private fun hash(json: String): String = SyncProjection.hash(json)

    /** Result of annotating a plan's matched pairs with their content state. */
    data class Annotation<Local, Server>(
        /** Matched pairs carrying their [SyncContentState]. */
        val matches: List<SyncMatch<Local, Server>>,
        /**
         * `sync_state` rows the caller must persist (upsert): one per matched pair
         * that had no valid base snapshot. Baselined as IN_SYNC against the current
         * *local* projection (rebase-on-local) so no spurious update is emitted.
         */
        val baselines: List<SyncStateEntity>
    )

    /**
     * Classifies every matched pair against its stored base snapshot and derives
     * the baseline rows for pairs that are not yet tracked (first sync after
     * upgrade, fresh name/LLM/manual match, or a stale row whose `serverId` no
     * longer matches the matched server item).
     *
     * Pure — the caller persists [Annotation.baselines] and assembles the buckets.
     */
    fun <Local, Server> annotate(
        matched: List<SyncMatch<Local, Server>>,
        existingByLocalId: Map<Long, SyncStateEntity>,
        entityType: String,
        localId: (Local) -> Long,
        serverId: (Server) -> String?,
        toLocalJson: (Local) -> String,
        toServerJson: (Server) -> String,
        now: Long = System.currentTimeMillis()
    ): Annotation<Local, Server> {
        val annotated = ArrayList<SyncMatch<Local, Server>>(matched.size)
        val baselines = ArrayList<SyncStateEntity>()
        for (match in matched) {
            val sId = serverId(match.server)
            if (sId == null) {
                annotated.add(match.copy(contentState = SyncContentState.IN_SYNC))
                continue
            }
            val existing = existingByLocalId[localId(match.local)]
            if (existing != null && existing.serverId == sId) {
                val state = resolveState(
                    baseJson = existing.baseSnapshot,
                    localJson = toLocalJson(match.local),
                    serverJson = toServerJson(match.server)
                )
                annotated.add(match.copy(contentState = state))
            } else {
                val localJson = toLocalJson(match.local)
                val serverJson = toServerJson(match.server)
                val state = if (localJson == serverJson) {
                    SyncContentState.IN_SYNC
                } else {
                    SyncContentState.CONFLICT
                }
                baselines.add(
                    SyncStateEntity(
                        entityType = entityType,
                        localId = localId(match.local),
                        serverId = sId,
                        baseSnapshot = localJson,
                        lastSyncAt = now
                    )
                )
                annotated.add(match.copy(contentState = state))
            }
        }
        return Annotation(matches = annotated, baselines = baselines)
    }
}
