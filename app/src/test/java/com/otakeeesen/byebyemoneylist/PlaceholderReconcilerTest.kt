package com.otakeeesen.byebyemoneylist

import com.otakeeesen.byebyemoneylist.data.agent.PlaceholderReconciler
import com.otakeeesen.byebyemoneylist.data.agent.ReconcileItem
import com.otakeeesen.byebyemoneylist.data.agent.ReconcilePurchase
import com.otakeeesen.byebyemoneylist.data.agent.TextCompletion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceholderReconcilerTest {

    private class FakeTextCompletion(private val response: String?) : TextCompletion {
        override suspend fun complete(systemInstruction: String, userMessage: String): String? = response
    }

    @Test
    fun `uses the LLM match and leaves unmatched rows alone`() = runBlocking {
        val reconciler = PlaceholderReconciler(
            FakeTextCompletion("""[{"listItem":"Milk","purchasedItem":"Whole Milk 3.5%","confidence":0.9}]""")
        )

        val result = reconciler.reconcile(
            items = listOf(ReconcileItem(1L, "Milk"), ReconcileItem(2L, "Bread")),
            purchaseItems = listOf(
                ReconcilePurchase(0, "Whole Milk 3.5%"),
                ReconcilePurchase(1, "Sourdough"),
            ),
        )

        assertEquals(mapOf(1L to 0), result)
    }

    @Test
    fun `ignores low-confidence LLM matches`() = runBlocking {
        val reconciler = PlaceholderReconciler(
            FakeTextCompletion("""[{"listItem":"Milk","purchasedItem":"Whole Milk 3.5%","confidence":0.2}]""")
        )

        val result = reconciler.reconcile(
            items = listOf(ReconcileItem(1L, "Milk")),
            purchaseItems = listOf(ReconcilePurchase(0, "Whole Milk 3.5%")),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `falls back to the deterministic matcher without an LLM`() = runBlocking {
        val reconciler = PlaceholderReconciler(null)

        val result = reconciler.reconcile(
            items = listOf(ReconcileItem(1L, "Bread")),
            purchaseItems = listOf(ReconcilePurchase(0, "Bread"), ReconcilePurchase(1, "Milk")),
        )

        assertEquals(mapOf(1L to 0), result)
    }

    @Test
    fun `claims a purchase item for at most one list row`() = runBlocking {
        val reconciler = PlaceholderReconciler(
            FakeTextCompletion(
                """[{"listItem":"Milk","purchasedItem":"Milk","confidence":0.9},""" +
                    """{"listItem":"Milch","purchasedItem":"Milk","confidence":0.9}]"""
            )
        )

        val result = reconciler.reconcile(
            items = listOf(ReconcileItem(1L, "Milk"), ReconcileItem(2L, "Milch")),
            purchaseItems = listOf(ReconcilePurchase(0, "Milk")),
        )

        assertEquals(mapOf(1L to 0), result)
    }
}
