package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductSyncMatcherTest {

    private val matcher = ProductSyncMatcher()

    private fun local(
        id: Long,
        name: String,
        barcode: String = "",
        serverId: String? = null
    ) = ProductEntity(
        id = id,
        name = name,
        barcode = barcode,
        picturePath = null,
        serverId = serverId
    )

    private fun server(
        id: String,
        name: String,
        barcode: String? = null,
        aliases: List<String> = emptyList()
    ) = NextcloudProductDto(id = id, name = name, barcode = barcode, aliases = aliases)

    @Test
    fun `products already linked by server id are matched first`() {
        val locals = listOf(local(1, "Renamed product", serverId = "s-1"))
        val servers = listOf(server("s-1", "Old name on server"))

        val plan = matcher.buildPlan(locals, servers)

        assertEquals(1, plan.matched.size)
        assertEquals(1L, plan.matched[0].local.id)
        assertEquals("s-1", plan.matched[0].server.id)
        assertTrue(plan.toPushToServer.isEmpty())
        assertTrue(plan.toPullToClient.isEmpty())
    }

    @Test
    fun `barcode is a strong match even when names differ`() {
        val locals = listOf(local(1, "Milch 1L", barcode = "4001234567890"))
        val servers = listOf(server("s-1", "Some Milk", barcode = "4001234567890"))

        val plan = matcher.buildPlan(locals, servers)

        assertEquals(1, plan.matched.size)
        assertEquals(1L, plan.matched[0].local.id)
        assertTrue(plan.matched[0].reason.contains("Barcode"))
    }

    @Test
    fun `barcode does not match when a side has no barcode`() {
        val locals = listOf(local(1, "Milch", barcode = ""))
        val servers = listOf(server("s-1", "Milk", barcode = "4001234567890"))

        val plan = matcher.buildPlan(locals, servers)

        assertTrue(plan.matched.isEmpty())
        assertEquals(1, plan.toPushToServer.size)
        assertEquals(1, plan.toPullToClient.size)
    }

    @Test
    fun `exact name ignoring case is matched`() {
        val locals = listOf(local(1, "Coca Cola"))
        val servers = listOf(server("s-9", "coca cola"))

        val plan = matcher.buildPlan(locals, servers)

        assertEquals(1, plan.matched.size)
        assertEquals(1L, plan.matched[0].local.id)
        assertTrue(plan.matched[0].reason.contains("Exact name"))
    }

    @Test
    fun `local alias equal to server name is matched`() {
        val locals = listOf(local(1, "Coca"))
        val aliases = mapOf(1L to listOf("Coca Cola"))

        val plan = matcher.buildPlan(locals, listOf(server("s-1", "Coca Cola")), aliases)

        assertEquals(1, plan.matched.size)
        assertEquals(1L, plan.matched[0].local.id)
        assertTrue(plan.matched[0].reason.contains("Alias"))
    }

    @Test
    fun `server alias equal to local name is matched`() {
        val locals = listOf(local(1, "Coca Cola"))
        val servers = listOf(server("s-1", "Coke", aliases = listOf("Coca Cola")))

        val plan = matcher.buildPlan(locals, servers)

        assertEquals(1, plan.matched.size)
        assertEquals(1L, plan.matched[0].local.id)
        assertTrue(plan.matched[0].reason.contains("Alias"))
    }

    @Test
    fun `similar names fall back to fuzzy matching`() {
        val locals = listOf(local(1, "Birne Conference 1kg"))
        val servers = listOf(server("s-1", "BirneConference"))

        val plan = matcher.buildPlan(locals, servers)

        assertEquals(1, plan.matched.size)
        assertEquals(1L, plan.matched[0].local.id)
        assertTrue(plan.matched[0].reason.contains("Fuzzy"))
    }

    @Test
    fun `unrelated products stay unmatched on both sides`() {
        val locals = listOf(local(1, "Handy"))
        val servers = listOf(server("s-1", "Apfel"))

        val plan = matcher.buildPlan(locals, servers)

        assertTrue(plan.matched.isEmpty())
        assertEquals(listOf(1L), plan.toPushToServer.map { it.id })
        assertEquals(listOf("s-1"), plan.toPullToClient.map { it.id })
    }
}
