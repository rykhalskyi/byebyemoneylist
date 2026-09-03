package com.otakeeesen.byebyemoneylist.data.sync.model

data class SyncMatch<Local, Server>(
    val local: Local,
    val server: Server,
    val reason: String
)

data class SyncPlan<Local, Server>(
    val matched: List<SyncMatch<Local, Server>>,
    val toPushToServer: List<Local>,
    val toPullToClient: List<Server>
) {
    val matchedCount: Int get() = matched.size
    val uploadCount: Int get() = toPushToServer.size
    val downloadCount: Int get() = toPullToClient.size
}

/**
 * A single candidate item in an unmatched (upload/download) pool together with its
 * editable UI state. [canSync] is false for items that must not be re-uploaded or
 * re-downloaded (e.g. they were unlinked after a previous sync and carry a persisted
 * serverId) — such items can only be re-matched.
 */
data class SyncCandidate<T>(
    val item: T,
    val canSync: Boolean = true,
    val selected: Boolean = true
)

/** Generic counts for one group, displayed on the settings screen row. */
data class SyncGroupCounts(
    val matched: Int = 0,
    val upload: Int = 0,
    val download: Int = 0
)
