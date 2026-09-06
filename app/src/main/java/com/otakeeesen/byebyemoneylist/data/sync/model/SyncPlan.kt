package com.otakeeesen.byebyemoneylist.data.sync.model

/**
 * Delta state of a matched pair relative to its last-synced base snapshot.
 * See the 3-way comparison in [com.otakeeesen.byebyemoneylist.data.sync.SyncStateResolver].
 */
enum class SyncContentState {
    IN_SYNC,
    LOCAL_CHANGED,
    SERVER_CHANGED,
    CONFLICT
}

data class SyncMatch<Local, Server>(
    val local: Local,
    val server: Server,
    val reason: String,
    val contentState: SyncContentState = SyncContentState.IN_SYNC
)

/**
 * A matched pair where both sides changed differently vs the base snapshot.
 * [resolvedTo] is null while unresolved and set to the chosen side once the user
 * picks "Use local" / "Use server" (Ticket 6).
 */
data class SyncConflict<Local, Server>(
    val match: SyncMatch<Local, Server>,
    val resolvedTo: SyncContentState? = null
)

/** The local item to push if this conflict was resolved with "Use local"; null otherwise. */
fun <Local, Server> SyncConflict<Local, Server>.pickedLocal(): Local? =
    if (resolvedTo == SyncContentState.LOCAL_CHANGED) match.local else null

/** The server item to pull if this conflict was resolved with "Use server"; null otherwise. */
fun <Local, Server> SyncConflict<Local, Server>.pickedServer(): Server? =
    if (resolvedTo == SyncContentState.SERVER_CHANGED) match.server else null

data class SyncPlan<Local, Server>(
    val matched: List<SyncMatch<Local, Server>>,
    val toPushToServer: List<Local>,
    val toPullToClient: List<Server>,
    /** Matched pairs whose local content changed vs base → propagate with a PUT (Ticket 5). */
    val toUpdateServer: List<Local> = emptyList(),
    /** Matched pairs whose server content changed vs base → overwrite local shared fields (Ticket 5). */
    val toUpdateLocal: List<Server> = emptyList(),
    /** Matched pairs where both sides changed differently → explicit resolution (Ticket 6). */
    val conflicts: List<SyncConflict<Local, Server>> = emptyList()
) {
    val matchedCount: Int get() = matched.size
    val uploadCount: Int get() = toPushToServer.size
    val downloadCount: Int get() = toPullToClient.size
}

/**
 * A single candidate item in an unmatched (upload/download) pool together with its
 * editable UI state. Every unmatched item — including one unlinked after a previous
 * sync — can be re-uploaded / re-downloaded (creating a new entry with a new id on the
 * destination side), re-matched, or left untouched.
 */
data class SyncCandidate<T>(
    val item: T,
    val selected: Boolean = true
)

/**
 * A matched pair with a user-editable "apply this update" flag. Only pairs whose
 * content changed on one side ([SyncContentState.LOCAL_CHANGED] / [SyncContentState.SERVER_CHANGED])
 * are toggleable; [isUpdate] mirrors that so the UI can hide the checkbox otherwise.
 * Selected by default so confirmed syncs propagate pending changes unless the user opts out.
 */
data class SyncMatchCandidate<Local, Server>(
    val match: SyncMatch<Local, Server>,
    val selected: Boolean = true
) {
    val isUpdate: Boolean
        get() = match.contentState == SyncContentState.LOCAL_CHANGED ||
            match.contentState == SyncContentState.SERVER_CHANGED
}

/** Generic counts for one group, displayed on the settings screen row. */
data class SyncGroupCounts(
    val matched: Int = 0,
    val upload: Int = 0,
    val download: Int = 0
)
