package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.sync.model.SyncPlan

enum class SyncPhase { FETCHING, LLM_MATCHING }

interface SyncRepository<Local, Server> {
    suspend fun generateSyncPlan(
        useLlm: Boolean = false,
        llmCall: (suspend (prompt: String) -> String?)? = null,
        onPhase: (SyncPhase) -> Unit = {}
    ): Result<SyncPlan<Local, Server>>

    suspend fun executeSyncPlan(
        plan: SyncPlan<Local, Server>,
        pushItems: List<Local>,
        pullItems: List<Server>,
        linkedPairs: List<Pair<Local, Server>>,
        /** Matched local items with pending local changes to push via PUT (LOCAL_CHANGED, selected). */
        updateToServer: List<Local> = emptyList(),
        /** Matched server items with pending remote changes to pull into the local DB (SERVER_CHANGED, selected). */
        updateToLocal: List<Server> = emptyList()
    ): Result<Boolean>
}
