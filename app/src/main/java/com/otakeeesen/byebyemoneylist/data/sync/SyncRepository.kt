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
        linkedPairs: List<Pair<Local, Server>>
    ): Result<Boolean>
}
