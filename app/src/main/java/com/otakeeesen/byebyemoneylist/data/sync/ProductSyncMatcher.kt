package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncMatch
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncPlan
import com.otakeeesen.byebyemoneylist.util.ProductMatcher

/**
 * Flat (non-hierarchical) matcher for products, with decreasing match strength:
 * 1. Already linked through a previous sync (local `serverId` == server `id`).
 * 2. Barcode equality (strong match) when both sides carry a barcode.
 * 3. Exact name match (case-insensitive) or alias match (a local alias equals the server
 *    name, or the local name appears in the server's alias list).
 * 4. Fuzzy name match via [ProductMatcher].
 */
class ProductSyncMatcher : SyncMatcher<ProductEntity, NextcloudProductDto> {

    override fun buildPlan(
        localItems: List<ProductEntity>,
        serverItems: List<NextcloudProductDto>
    ): SyncPlan<ProductEntity, NextcloudProductDto> {
        return buildPlan(localItems, serverItems, emptyMap())
    }

    fun buildPlan(
        localItems: List<ProductEntity>,
        serverItems: List<NextcloudProductDto>,
        aliasesByProductId: Map<Long, List<String>>
    ): SyncPlan<ProductEntity, NextcloudProductDto> {
        val matched = mutableListOf<SyncMatch<ProductEntity, NextcloudProductDto>>()
        val unmatchedLocal = localItems.toMutableList()
        val unmatchedServer = serverItems.toMutableList()

        // 1. Already linked via a previous sync.
        matchLocalToServer(unmatchedLocal, unmatchedServer, matched) { local ->
            local.serverId?.takeIf { it.isNotBlank() }?.let { id ->
                unmatchedServer.firstOrNull { it.id == id }
            }?.let { server ->
                server to "Matched by Server ID"
            }
        }

        // 2. Barcode equality (strong match).
        matchLocalToServer(unmatchedLocal, unmatchedServer, matched) { local ->
            val localBarcode = local.barcode.trim().takeIf { it.isNotBlank() }
            if (localBarcode == null) return@matchLocalToServer null
            unmatchedServer.firstOrNull { server ->
                server.barcode?.trim()?.equals(localBarcode, ignoreCase = true) == true
            }?.let { server ->
                server to "Barcode match (${local.barcode})"
            }
        }

        // 3. Exact name match.
        matchLocalToServer(unmatchedLocal, unmatchedServer, matched) { local ->
            unmatchedServer.firstOrNull { server ->
                server.name.equals(local.name, ignoreCase = true)
            }?.let { server ->
                server to "Exact name match (${local.name})"
            }
        }

        // 4. Alias match: a local alias equals the server name, or the local name appears
        //    in the server product's alias list.
        matchLocalToServer(unmatchedLocal, unmatchedServer, matched) { local ->
            val localAliases = (aliasesByProductId[local.id] ?: emptyList())
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
            val localName = local.name.trim().lowercase()
            unmatchedServer.firstOrNull { server ->
                localAliases.contains(server.name.trim().lowercase()) ||
                    server.aliases.any { alias ->
                        alias.trim().lowercase() == localName && localName.isNotEmpty()
                    }
            }?.let { server ->
                server to "Alias match (${local.name})"
            }
        }

        // 5. Fuzzy name match via ProductMatcher.
        val serversIterator = unmatchedServer.iterator()
        while (serversIterator.hasNext()) {
            val server = serversIterator.next()
            val bestLocal = ProductMatcher.findBestMatch(server.name, unmatchedLocal)
            if (bestLocal != null) {
                matched.add(
                    SyncMatch(
                        local = bestLocal,
                        server = server,
                        reason = "Fuzzy name match (${server.name})"
                    )
                )
                unmatchedLocal.remove(bestLocal)
                serversIterator.remove()
            }
        }

        return SyncPlan(
            matched = matched,
            toPushToServer = unmatchedLocal,
            toPullToClient = unmatchedServer
        )
    }

    private fun matchLocalToServer(
        unmatchedLocal: MutableList<ProductEntity>,
        unmatchedServer: MutableList<NextcloudProductDto>,
        matched: MutableList<SyncMatch<ProductEntity, NextcloudProductDto>>,
        findServer: (ProductEntity) -> Pair<NextcloudProductDto, String>?
    ) {
        val iterator = unmatchedLocal.iterator()
        while (iterator.hasNext()) {
            val local = iterator.next()
            val found = findServer(local)
            if (found != null) {
                matched.add(SyncMatch(local = local, server = found.first, reason = found.second))
                iterator.remove()
                unmatchedServer.remove(found.first)
            }
        }
    }
}
