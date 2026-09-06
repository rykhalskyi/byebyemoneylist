package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncConflict
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncContentState
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncMatch
import com.otakeeesen.byebyemoneylist.data.sync.model.pickedLocal
import com.otakeeesen.byebyemoneylist.data.sync.model.pickedServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConflictResolutionTest {

    private fun local(id: Long, name: String, serverId: String? = "s-1") =
        StoreEntity(id = id, name = name, logoPath = null, serverId = serverId)

    private fun server(id: String, name: String) = NextcloudStoreDto(id = id, name = name)

    private fun conflictMatch(localName: String, serverName: String) = SyncMatch(
        local = local(1, localName),
        server = server("s-1", serverName),
        reason = "Matched by Server ID",
        contentState = SyncContentState.CONFLICT
    )

    @Test
    fun `both sides changed differently resolves to a conflict`() {
        assertEquals(
            SyncContentState.CONFLICT,
            SyncStateResolver.resolveState(
                baseJson = SyncProjection.storeServer(server("s-1", "Rewe")),
                localJson = SyncProjection.storeLocal(local(1, "Rewe City")),
                serverJson = SyncProjection.storeServer(server("s-1", "REWE Markt"))
            )
        )
    }

    @Test
    fun `unresolved conflict maps to neither update bucket`() {
        val conflict = SyncConflict(match = conflictMatch("Rewe City", "REWE Markt"), resolvedTo = null)

        assertNull(conflict.pickedLocal())
        assertNull(conflict.pickedServer())
    }

    @Test
    fun `picking local feeds the push bucket only`() {
        val conflict = SyncConflict(
            match = conflictMatch("Rewe City", "REWE Markt"),
            resolvedTo = SyncContentState.LOCAL_CHANGED
        )

        assertEquals(listOf(1L), listOf(conflict).mapNotNull { it.pickedLocal() }.map { it.id })
        assertEquals(emptyList<NextcloudStoreDto>(), listOf(conflict).mapNotNull { it.pickedServer() })
    }

    @Test
    fun `picking server feeds the pull bucket only`() {
        val conflict = SyncConflict(
            match = conflictMatch("Rewe City", "REWE Markt"),
            resolvedTo = SyncContentState.SERVER_CHANGED
        )

        assertEquals(emptyList<StoreEntity>(), listOf(conflict).mapNotNull { it.pickedLocal() })
        assertEquals(listOf("s-1"), listOf(conflict).mapNotNull { it.pickedServer() }.map { it.id })
    }

    @Test
    fun `mixed resolutions split into both buckets and unresolved ones are skipped`() {
        val resolvedLocal = SyncConflict(
            match = conflictMatch("Rewe City", "REWE Markt"),
            resolvedTo = SyncContentState.LOCAL_CHANGED
        )
        val resolvedServer = SyncConflict(
            match = SyncMatch(
                local = local(2, "Aldi", serverId = "s-2"),
                server = server("s-2", "ALDI"),
                reason = "x",
                contentState = SyncContentState.CONFLICT
            ),
            resolvedTo = SyncContentState.SERVER_CHANGED
        )
        val unresolved = SyncConflict(match = conflictMatch("Lidl", "LIDL"), resolvedTo = null)

        val conflicts = listOf(resolvedLocal, resolvedServer, unresolved)
        val toPush = conflicts.mapNotNull { it.pickedLocal() }
        val toPull = conflicts.mapNotNull { it.pickedServer() }

        assertEquals(listOf(1L), toPush.map { it.id })
        assertEquals(listOf("s-2"), toPull.map { it.id })
        assertEquals(1, conflicts.count { it.resolvedTo == null })
    }

    @Test
    fun `base advanced after pick local is in sync on the next run`() {
        val match = conflictMatch("Rewe City", "REWE Markt")
        // Pick local → PUT local content; the new base is the local projection.
        val base = SyncProjection.storeLocal(match.local)

        // Server echoes the pushed content back.
        val serverNow = SyncProjection.storeServer(server("s-1", "Rewe City"))

        assertEquals(base, serverNow)
        assertEquals(
            SyncContentState.IN_SYNC,
            SyncStateResolver.resolveState(baseJson = base, localJson = base, serverJson = serverNow)
        )
    }

    @Test
    fun `base advanced after pick server is in sync on the next run`() {
        val match = conflictMatch("Rewe City", "REWE Markt")
        // Pick server → pull the remote name; the new base is the server projection.
        val base = SyncProjection.storeServer(match.server)
        val localNow = SyncProjection.storeLocal(local(1, match.server.name))

        assertEquals(base, localNow)
        assertEquals(
            SyncContentState.IN_SYNC,
            SyncStateResolver.resolveState(baseJson = base, localJson = localNow, serverJson = base)
        )
    }
}
