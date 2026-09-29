package com.otakeeesen.byebyemoneylist.data.agent

import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Matches items from a finalized purchase against the pending items of the active
 * "To Buy" list using the configured LLM, so matched items can be auto-checked.
 *
 * The matcher is side-effect free: it only returns the ids of pending items it is
 * confident were purchased. Persistence is the caller's responsibility.
 */
open class ToBuyAutoMatcher(
    private val preferencesManager: PreferencesManager,
    private val llm: LlmTextGenerator,
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val JSON_FENCE_REGEX = Regex("```(?:json)?\\s*|```")

        private val SYSTEM_INSTRUCTION = """
            You match items that were just purchased against a user's pending "To Buy" list in the ByeByeMoney app.

            Purchased item names and To Buy item names may be written in different languages or spellings.
            Match equivalent products across languages and word forms, e.g. "milk" ~ "Milch" ~ "молоко", "eggs" ~ "Eier" ~ "яйця".
            Only match when you are reasonably confident it is the same product. Never match unrelated products.

            Return ONLY a raw JSON object, without markdown code fences, in this exact shape:
            {"matchedIds":[<id>, ...]}

            The ids MUST be taken from the provided To Buy list. If nothing matches, return {"matchedIds":[]}.
        """.trimIndent()
    }

    /**
     * @param pending pending To Buy items as `(itemId, name)` pairs.
     * @param purchased names of the items that were purchased.
     * @return ids of the [pending] items that were purchased (possibly empty).
     */
    open suspend fun match(pending: List<Pair<Long, String>>, purchased: List<String>): List<Long> {
        if (pending.isEmpty() || purchased.isEmpty()) return emptyList()
        if (!isActiveProfileConfigured()) return emptyList()
    
        val pendingIds = pending.map { it.first }.toSet()
        val rawResponse = try {
            withContext(Dispatchers.IO) { llm.generate(SYSTEM_INSTRUCTION, buildPrompt(pending, purchased)) }
        } catch (e: Exception) {
            null
        } ?: return emptyList()

        return parseMatchedIds(rawResponse)
            .filter { it in pendingIds }
            .distinct()
    }

    private fun isActiveProfileConfigured(): Boolean {
        val activeId = preferencesManager.getActiveProfileId() ?: return false
        return preferencesManager.getLlmProfiles().any { it.id == activeId }
    }

    private fun buildPrompt(pending: List<Pair<Long, String>>, purchased: List<String>): String {
        val purchasedLines = purchased.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            .joinToString("\n") { "- $it" }
        val pendingLines = pending.joinToString("\n") { (id, name) -> "- $id: $name" }
        return """
            Purchased items:
            $purchasedLines

            Pending To Buy items (id: name):
            $pendingLines
        """.trimIndent()
    }

    private fun parseMatchedIds(raw: String): List<Long> {
        val cleaned = raw.trim().replace(JSON_FENCE_REGEX, "").trim()
        return try {
            json.decodeFromString<ToBuyMatchResponse>(cleaned).matchedIds
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Serializable
    private data class ToBuyMatchResponse(val matchedIds: List<Long> = emptyList())
}
