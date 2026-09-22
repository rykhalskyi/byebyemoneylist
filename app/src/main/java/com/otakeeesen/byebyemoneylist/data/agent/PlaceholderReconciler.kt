package com.otakeeesen.byebyemoneylist.data.agent

import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.util.ProductMatcher
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A free-text list row that should be matched against a purchase. */
data class ReconcileItem(
    val itemId: Long,
    val name: String,
    val quantity: Double = 1.0,
)

/** One item of the purchase the list is being reconciled against. */
data class ReconcilePurchase(
    val index: Int,
    val name: String,
    val quantity: Double = 1.0,
)

/**
 * Matches free-text ("placeholder") list rows to the items of a purchase.
 *
 * LLM-first via [TextCompletion], with a deterministic [ProductMatcher] fallback for
 * rows the LLM did not match (no active profile, failure, or low confidence). Only
 * unique matches above [confidenceThreshold] are returned; everything else is left
 * unmatched (caller marks it "not bought"). Matching is one-to-one: a purchase item
 * can be claimed by at most one list row.
 */
class PlaceholderReconciler(
    private val textCompletion: TextCompletion?,
    private val confidenceThreshold: Double = 0.5,
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class MatchJson(
        val listItem: String? = null,
        val purchasedItem: String? = null,
        val confidence: Double? = null,
    )

    private data class Proposal(val listItem: String, val purchasedItem: String, val confidence: Double)

    suspend fun reconcile(
        items: List<ReconcileItem>,
        purchaseItems: List<ReconcilePurchase>,
    ): Map<Long, Int> {
        if (items.isEmpty() || purchaseItems.isEmpty()) return emptyMap()

        val matched = LinkedHashMap<Long, Int>()
        val claimed = mutableSetOf<Int>()

        // 1. LLM-first: high-confidence proposals claim their purchase item first.
        val proposals = textCompletion
            ?.let { completion -> runCatching { llmReconcile(completion, items, purchaseItems) }.getOrNull() }
            .orEmpty()
        proposals.sortedByDescending { it.confidence }.forEach { proposal ->
            if (proposal.confidence < confidenceThreshold) return@forEach
            val item = items.firstOrNull {
                it.name.equals(proposal.listItem, ignoreCase = true) && it.itemId !in matched
            } ?: return@forEach
            val purchase = purchaseItems.firstOrNull {
                it.name.equals(proposal.purchasedItem, ignoreCase = true) && it.index !in claimed
            } ?: return@forEach
            matched[item.itemId] = purchase.index
            claimed.add(purchase.index)
        }

        // 2. Deterministic fallback for the rows still unmatched.
        if (items.any { it.itemId !in matched }) {
            val products = purchaseItems.map {
                ProductEntity(id = it.index.toLong(), name = it.name, barcode = "", picturePath = null)
            }
            items.forEach { item ->
                if (item.itemId in matched) return@forEach
                val best = ProductMatcher.findBestMatch(item.name, products) ?: return@forEach
                val index = best.id.toInt()
                if (!claimed.add(index)) return@forEach
                matched[item.itemId] = index
            }
        }

        return matched
    }

    private suspend fun llmReconcile(
        completion: TextCompletion,
        items: List<ReconcileItem>,
        purchaseItems: List<ReconcilePurchase>,
    ): List<Proposal> {
        val listText = items.joinToString("\n") { "- ${it.name}" }
        val purchaseText = purchaseItems.joinToString("\n") { "- ${it.name}" }
        val userMessage = "Shopping list items:\n$listText\n\nPurchased items:\n$purchaseText"
        val raw = completion.complete(SYSTEM_INSTRUCTION, userMessage) ?: return emptyList()
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        val parsed = runCatching { json.decodeFromString<List<MatchJson>>(cleaned) }.getOrNull()
            ?: return emptyList()
        return parsed.mapNotNull { match ->
            val listItem = match.listItem?.trim().orEmpty()
            val purchasedItem = match.purchasedItem?.trim().orEmpty()
            if (listItem.isEmpty() || purchasedItem.isEmpty()) {
                null
            } else {
                Proposal(listItem, purchasedItem, match.confidence ?: 1.0)
            }
        }
    }

    companion object {
        val SYSTEM_INSTRUCTION = """
            You match free-text items from a user's shopping list to the items the user
            actually purchased. For each list item, choose at most ONE purchased item that
            is the same product (an exact match, a brand/size variant, or a clear synonym).

            Rules:
            1. Use the EXACT strings from the provided lists — never paraphrase.
            2. A purchased item may be used by at most one list item.
            3. If a list item has no plausible purchased counterpart, OMIT it.
            4. Return ONLY a raw JSON array, without markdown fences:
               [{"listItem": "<exact list item>", "purchasedItem": "<exact purchased item>", "confidence": 0.0-1.0}]
            5. "confidence" is how sure you are the two are the same product (0.0-1.0).
            6. Do not output anything else.
        """.trimIndent()
    }
}
