package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiLanguageCategoryMatcherTest {

    private val matcher = MultiLanguageCategoryMatcher()

    private fun local(id: Long, name: String, parentId: Long? = null) =
        CategoryEntity(id = id, name = name, parentId = parentId)

    private fun server(id: String, name: String, parentId: String? = null) =
        NextcloudCategoryDto(id = id, name = name, parentId = parentId)

    // ============================================================
    // matchAllWithLlm
    // ============================================================

    @Test
    fun `llm prompt includes hierarchy paths for local and server categories`() = runTest {
        val locals = listOf(
            local(1, "Food & Drink"),
            local(2, "Fleisch", parentId = 1)
        )
        val servers = listOf(
            server("s-1", "Food"),
            server("s-2", "Meat", parentId = "s-1")
        )

        var capturedPrompt: String? = null
        val items = matcher.matchAllWithLlm(locals, servers) { prompt ->
            capturedPrompt = prompt
            """{"matches":[]}"""
        }

        assertTrue(items.isEmpty())
        val prompt = capturedPrompt.orEmpty()
        assertTrue(prompt.contains("Food & Drink / Fleisch"))
        assertTrue(prompt.contains("Food / Meat"))
        assertTrue(prompt.contains("id=2, name='Fleisch'"))
        assertTrue(prompt.contains("id='s-2', name='Meat'"))
    }

    @Test
    fun `llm response json is parsed into match items`() = runTest {
        val locals = listOf(local(1, "Food"), local(2, "Groceries"))
        val servers = listOf(server("s-1", "Lebensmittel"), server("s-2", "Drogerie"))

        val items = matcher.matchAllWithLlm(locals, servers) {
            """{"matches":[{"localId":2,"serverId":"s-1","reason":"German Lebensmittel matches Groceries"}]}"""
        }

        assertEquals(1, items.size)
        assertEquals(2L, items[0].localId)
        assertEquals("s-1", items[0].serverId)
    }

    @Test
    fun `invalid llm response returns empty list`() = runTest {
        val locals = listOf(local(1, "Food"))
        val servers = listOf(server("s-1", "Lebensmittel"))

        val items = matcher.matchAllWithLlm(locals, servers) { "not json at all" }

        assertTrue(items.isEmpty())
    }

    @Test
    fun `empty input lists return empty without calling llm`() = runTest {
        var called = false
        val items = matcher.matchAllWithLlm(emptyList(), emptyList()) {
            called = true
            "{}"
        }
        assertTrue(items.isEmpty())
        assertTrue(!called)
    }

    // ============================================================
    // buildSyncPlan child-overlap matching
    // ============================================================

    @Test
    fun `parents with same children match despite different names`() {
        val locals = listOf(
            local(1, "Supermarket"),
            local(2, "Bread", parentId = 1),
            local(3, "Milk", parentId = 1),
            local(4, "Eggs", parentId = 1)
        )
        val servers = listOf(
            server("s-1", "Food"),
            server("s-2", "Bread", parentId = "s-1"),
            server("s-3", "Milk", parentId = "s-1"),
            server("s-4", "Eggs", parentId = "s-1")
        )

        val plan = matcher.buildSyncPlan(locals, servers)

        val supermarketMatch = plan.matched.firstOrNull { it.localCategory.name == "Supermarket" }
        assertEquals("Food", supermarketMatch?.serverCategory?.name)
        assertTrue(supermarketMatch?.matchReason?.contains("child overlap") == true)
        assertTrue(plan.toPushToServer.isEmpty())
        assertTrue(plan.toPullToClient.isEmpty())
    }

    @Test
    fun `parents with too few shared children do not match`() {
        val locals = listOf(
            local(1, "Supermarket"),
            local(2, "Bread", parentId = 1),
            local(3, "Milk", parentId = 1),
            local(4, "Eggs", parentId = 1),
            local(5, "Video Games", parentId = 1)
        )
        val servers = listOf(
            server("s-1", "Food"),
            server("s-2", "Bread", parentId = "s-1"),
            server("s-3", "Milk", parentId = "s-1")
        )

        val plan = matcher.buildSyncPlan(locals, servers)

        val supermarketMatch = plan.matched.firstOrNull { it.localCategory.name == "Supermarket" }
        assertEquals(null, supermarketMatch)
        assertTrue(plan.toPushToServer.any { it.name == "Supermarket" })
    }

    // ============================================================
    // matchRemainingWithLlm
    // ============================================================

    @Test
    fun `remaining llm prompt is focused and includes hierarchy paths`() = runTest {
        val allLocal = listOf(
            local(1, "Service and Subs"),
            local(2, "Subscription", parentId = 1)
        )
        val allServer = listOf(
            server("s-1", "Service and Subs"),
            server("s-2", "Subscriptions", parentId = "s-1")
        )

        var capturedPrompt: String? = null
        val items = matcher.matchRemainingWithLlm(
            allLocal,
            allServer,
            listOf(local(2, "Subscription", parentId = 1)),
            listOf(server("s-2", "Subscriptions", parentId = "s-1"))
        ) { prompt ->
            capturedPrompt = prompt
            """{"matches":[]}"""
        }

        assertTrue(items.isEmpty())
        val prompt = capturedPrompt.orEmpty()
        assertTrue(prompt.contains("Service and Subs / Subscription"))
        assertTrue(prompt.contains("Service and Subs / Subscriptions"))
        assertTrue(prompt.contains("remain unmatched"))
    }

    // ============================================================
    // mergePlans
    // ============================================================

    @Test
    fun `merge keeps deterministic matches and adds llm matches`() {
        val locals = listOf(
            local(1, "Supermarket"),
            local(2, "Bread", parentId = 1),
            local(3, "Milk", parentId = 1),
            local(4, "Subscription")
        )
        val servers = listOf(
            server("s-1", "Food"),
            server("s-2", "Bread", parentId = "s-1"),
            server("s-3", "Milk", parentId = "s-1"),
            server("s-4", "Subscriptions")
        )

        val deterministic = matcher.buildSyncPlan(locals, servers)
        val llmPlan = matcher.buildSyncPlanFromLlm(
            deterministic.toPushToServer,
            deterministic.toPullToClient,
            listOf(LlmCategoryMatchItem(localId = 4, serverId = "s-4", reason = "plural form"))
        )
        val merged = matcher.mergePlans(deterministic, llmPlan)

        assertTrue(merged.matched.any { it.localCategory.name == "Supermarket" && it.serverCategory.name == "Food" })
        assertTrue(merged.matched.any { it.localCategory.name == "Bread" })
        assertTrue(merged.matched.any { it.localCategory.name == "Subscription" && it.serverCategory.name == "Subscriptions" })
        assertTrue(merged.toPushToServer.isEmpty())
        assertTrue(merged.toPullToClient.isEmpty())
    }

    // ============================================================
    // buildSyncPlanFromLlm
    // ============================================================

    @Test
    fun `builds plan from llm matches`() {
        val locals = listOf(local(1, "Food"), local(2, "Groceries"), local(3, "Pharmacy"))
        val servers = listOf(server("s-1", "Lebensmittel"), server("s-2", "Drogerie"), server("s-3", "Haushalt"))

        val plan = matcher.buildSyncPlanFromLlm(
            locals,
            servers,
            listOf(LlmCategoryMatchItem(localId = 2, serverId = "s-1", reason = "same meaning"))
        )

        assertEquals(1, plan.matched.size)
        assertEquals("Groceries", plan.matched[0].localCategory.name)
        assertEquals("Lebensmittel", plan.matched[0].serverCategory.name)
        assertEquals("LLM match: same meaning", plan.matched[0].matchReason)
        assertEquals(listOf(1L, 3L), plan.toPushToServer.map { it.id })
        assertEquals(listOf("s-2", "s-3"), plan.toPullToClient.map { it.id })
    }

    @Test
    fun `skips unresolved and duplicate llm matches`() {
        val locals = listOf(local(1, "Food"), local(2, "Groceries"))
        val servers = listOf(server("s-1", "Lebensmittel"))

        val plan = matcher.buildSyncPlanFromLlm(
            locals,
            servers,
            listOf(
                LlmCategoryMatchItem(localId = 99, serverId = "s-1", reason = "unknown local"),
                LlmCategoryMatchItem(localId = 1, serverId = "s-99", reason = "unknown server"),
                LlmCategoryMatchItem(localId = 1, serverId = "s-1", reason = "valid"),
                LlmCategoryMatchItem(localId = 1, serverId = "s-1", reason = "duplicate"),
                LlmCategoryMatchItem(localId = 2, serverId = "s-1", reason = "collision on server")
            )
        )

        assertEquals(1, plan.matched.size)
        assertEquals(1L, plan.matched[0].localCategory.id)
        assertEquals(listOf(2L), plan.toPushToServer.map { it.id })
        assertTrue(plan.toPullToClient.isEmpty())
    }
}
