package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreSyncMatcherTest {

    private val matcher = StoreSyncMatcher()

    private fun local(id: Long, name: String, serverId: String? = null) =
        StoreEntity(id = id, name = name, logoPath = null, serverId = serverId)

    private fun server(id: String, name: String) =
        NextcloudStoreDto(id = id, name = name)

    @Test
    fun `stores already linked by server id are matched first`() {
        val locals = listOf(local(1, "Rewe", serverId = "s-1"))
        val servers = listOf(server("s-1", "REWE"))

        val plan = matcher.buildPlan(locals, servers)

        assertEquals(1, plan.matched.size)
        assertEquals(1L, plan.matched[0].local.id)
        assertEquals("s-1", plan.matched[0].server.id)
        assertEquals(0, plan.toPushToServer.size)
        assertEquals(0, plan.toPullToClient.size)
    }

    @Test
    fun `same name ignoring case is matched`() {
        val locals = listOf(local(1, "REWE City"))
        val servers = listOf(server("s-9", "rewe city"))

        val plan = matcher.buildPlan(locals, servers)

        assertEquals(1, plan.matched.size)
        assertEquals("Exact name match (REWE City)", plan.matched[0].reason)
        assertTrue(plan.toPushToServer.isEmpty())
        assertTrue(plan.toPullToClient.isEmpty())
    }

    @Test
    fun `different names stay unmatched on both sides`() {
        val locals = listOf(local(1, "Lidl"))
        val servers = listOf(server("s-1", "Aldi"))

        val plan = matcher.buildPlan(locals, servers)

        assertTrue(plan.matched.isEmpty())
        assertEquals(listOf(1L), plan.toPushToServer.map { it.id })
        assertEquals(listOf("s-1"), plan.toPullToClient.map { it.id })
    }

    @Test
    fun `one server store matches only one local when names collide`() {
        val locals = listOf(local(1, "Rewe"), local(2, "Rewe"))
        val servers = listOf(server("s-1", "REWE"))

        val plan = matcher.buildPlan(locals, servers)

        assertEquals(1, plan.matched.size)
        assertEquals(1, plan.toPushToServer.size)
    }
}
