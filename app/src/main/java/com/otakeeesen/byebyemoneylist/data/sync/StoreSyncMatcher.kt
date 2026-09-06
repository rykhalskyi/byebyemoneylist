package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncMatch
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncPlan

/**
 * Flat (non-hierarchical) matcher for stores. Two passes:
 * 1. Stores already linked through a previous sync (local `serverId` == server `id`).
 * 2. Same name, compared case-insensitively.
 */
class StoreSyncMatcher : SyncMatcher<StoreEntity, NextcloudStoreDto> {

    override fun buildPlan(
        localItems: List<StoreEntity>,
        serverItems: List<NextcloudStoreDto>
    ): SyncPlan<StoreEntity, NextcloudStoreDto> {
        val matched = mutableListOf<SyncMatch<StoreEntity, NextcloudStoreDto>>()
        val unmatchedLocal = localItems.toMutableList()
        val unmatchedServer = serverItems.toMutableList()

        // 1. Already linked via a previous sync.
        matchAndRemove(unmatchedLocal, unmatchedServer, matched, reason = { local, _ ->
            "Matched by Server ID"
        }) { local ->
            local.serverId?.takeIf { it.isNotBlank() }?.let { id ->
                unmatchedServer.firstOrNull { it.id == id }
            }
        }

        // 2. Same name (case-insensitive).
        matchAndRemove(unmatchedLocal, unmatchedServer, matched, reason = { local, _ ->
            "Exact name match (${local.name})"
        }) { local ->
            unmatchedServer.firstOrNull { it.name.equals(local.name, ignoreCase = true) }
        }

        return SyncPlan(
            matched = matched,
            toPushToServer = unmatchedLocal,
            toPullToClient = unmatchedServer
        )
    }

    private fun matchAndRemove(
        unmatchedLocal: MutableList<StoreEntity>,
        unmatchedServer: MutableList<NextcloudStoreDto>,
        matched: MutableList<SyncMatch<StoreEntity, NextcloudStoreDto>>,
        reason: (StoreEntity, NextcloudStoreDto) -> String,
        findServer: (StoreEntity) -> NextcloudStoreDto?
    ) {
        val iterator = unmatchedLocal.iterator()
        while (iterator.hasNext()) {
            val local = iterator.next()
            val server = findServer(local)
            if (server != null) {
                matched.add(SyncMatch(local = local, server = server, reason = reason(local, server)))
                iterator.remove()
                unmatchedServer.remove(server)
            }
        }
    }
}
