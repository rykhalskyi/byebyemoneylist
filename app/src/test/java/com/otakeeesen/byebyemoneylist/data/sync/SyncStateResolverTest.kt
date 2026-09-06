package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncContentState
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncMatch
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncStateEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncStateResolverTest {

    // ---- resolveState: the 3-way comparison table --------------------------------

    @Test
    fun `base null means no history so state is in sync`() {
        assertEquals(
            SyncContentState.IN_SYNC,
            SyncStateResolver.resolveState(baseJson = null, localJson = "A", serverJson = "A")
        )
    }

    @Test
    fun `no side changed against base is in sync`() {
        assertEquals(
            SyncContentState.IN_SYNC,
            SyncStateResolver.resolveState(baseJson = "A", localJson = "A", serverJson = "A")
        )
    }

    @Test
    fun `local changed only is local changed`() {
        assertEquals(
            SyncContentState.LOCAL_CHANGED,
            SyncStateResolver.resolveState(baseJson = "A", localJson = "B", serverJson = "A")
        )
    }

    @Test
    fun `server changed only is server changed`() {
        assertEquals(
            SyncContentState.SERVER_CHANGED,
            SyncStateResolver.resolveState(baseJson = "A", localJson = "A", serverJson = "B")
        )
    }

    @Test
    fun `both sides made the same change is a converged edit (in sync)`() {
        assertEquals(
            SyncContentState.IN_SYNC,
            SyncStateResolver.resolveState(baseJson = "A", localJson = "B", serverJson = "B")
        )
    }

    @Test
    fun `both sides changed differently is a conflict`() {
        assertEquals(
            SyncContentState.CONFLICT,
            SyncStateResolver.resolveState(baseJson = "A", localJson = "B", serverJson = "C")
        )
    }

    // ---- annotate ----------------------------------------------------------------

    private fun local(id: Long, name: String, serverId: String? = null) =
        StoreEntity(id = id, name = name, logoPath = null, serverId = serverId)

    private fun server(id: String, name: String) = NextcloudStoreDto(id = id, name = name)

    private fun state(entityType: String, localId: Long, serverId: String, base: String) =
        SyncStateEntity(
            entityType = entityType,
            localId = localId,
            serverId = serverId,
            baseSnapshot = base,
            lastSyncAt = 0L
        )

    private val type = SyncStateEntity.TYPE_STORE
    private val now = 1_000L

    private fun annotate(
        matched: List<SyncMatch<StoreEntity, NextcloudStoreDto>>,
        existingByLocalId: Map<Long, SyncStateEntity> = emptyMap()
    ) = SyncStateResolver.annotate(
        matched = matched,
        existingByLocalId = existingByLocalId,
        entityType = type,
        localId = { it.id },
        serverId = { it.id },
        toLocalJson = { SyncProjection.storeLocal(it) },
        toServerJson = { SyncProjection.storeServer(it) },
        now = now
    )

    @Test
    fun `pair without a stored base is baselined to the local projection as in sync`() {
        val match = SyncMatch(local = local(1, "Rewe"), server = server("s-1", "Rewe"), reason = "x")

        val result = annotate(listOf(match))

        assertEquals(1, result.matches.size)
        assertEquals(SyncContentState.IN_SYNC, result.matches[0].contentState)
        assertEquals(1, result.baselines.size)
        val baseline = result.baselines[0]
        assertEquals(type, baseline.entityType)
        assertEquals(1L, baseline.localId)
        assertEquals("s-1", baseline.serverId)
        assertEquals(SyncProjection.storeLocal(local(1, "Rewe")), baseline.baseSnapshot)
        assertEquals(now, baseline.lastSyncAt)
    }

    @Test
    fun `already tracked pair with unchanged content stays in sync and no baseline is written`() {
        val match = SyncMatch(local = local(1, "Rewe"), server = server("s-1", "Rewe"), reason = "x")
        val existing = mapOf(
            1L to state(type, 1L, "s-1", base = SyncProjection.storeServer(server("s-1", "Rewe")))
        )

        val result = annotate(listOf(match), existing)

        assertEquals(SyncContentState.IN_SYNC, result.matches[0].contentState)
        assertTrue(result.baselines.isEmpty())
    }

    @Test
    fun `already tracked pair with a local rename is local changed`() {
        val match = SyncMatch(local = local(1, "Rewe City"), server = server("s-1", "Rewe"), reason = "x")
        val existing = mapOf(
            1L to state(type, 1L, "s-1", base = SyncProjection.storeServer(server("s-1", "Rewe")))
        )

        val result = annotate(listOf(match), existing)

        assertEquals(SyncContentState.LOCAL_CHANGED, result.matches[0].contentState)
        assertTrue(result.baselines.isEmpty())
    }

    @Test
    fun `already tracked pair with a remote rename is server changed`() {
        val match = SyncMatch(local = local(1, "Rewe"), server = server("s-1", "REWE"), reason = "x")
        val existing = mapOf(
            1L to state(type, 1L, "s-1", base = SyncProjection.storeServer(server("s-1", "Rewe")))
        )

        val result = annotate(listOf(match), existing)

        assertEquals(SyncContentState.SERVER_CHANGED, result.matches[0].contentState)
        assertTrue(result.baselines.isEmpty())
    }

    @Test
    fun `stale base row for a different server id is rebound to the matched server`() {
        val match = SyncMatch(local = local(1, "Rewe"), server = server("s-2", "Rewe"), reason = "x")
        val stale = state(type, 1L, "s-1", base = "old-base")

        val result = annotate(listOf(match), mapOf(1L to stale))

        assertEquals(SyncContentState.IN_SYNC, result.matches[0].contentState)
        assertEquals(1, result.baselines.size)
        assertEquals("s-2", result.baselines[0].serverId)
        assertEquals(SyncProjection.storeLocal(local(1, "Rewe")), result.baselines[0].baseSnapshot)
    }
}
