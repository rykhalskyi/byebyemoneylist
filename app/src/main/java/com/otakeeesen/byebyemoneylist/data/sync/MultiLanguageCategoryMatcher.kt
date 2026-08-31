package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LlmCategoryMatchItem(
    val localId: Long,
    val serverId: String,
    val reason: String = "Semantic / Translation match"
)

@Serializable
data class LlmCategoryMatchResponse(
    val matches: List<LlmCategoryMatchItem> = emptyList()
)

data class CategoryMatchResult(
    val localCategory: CategoryEntity,
    val serverCategory: NextcloudCategoryDto,
    val isExactMatch: Boolean,
    val matchReason: String
)

data class CategorySyncPlan(
    val matched: List<CategoryMatchResult>,
    val toPushToServer: List<CategoryEntity>,
    val toPullToClient: List<NextcloudCategoryDto>
)

class MultiLanguageCategoryMatcher {

    private val json = Json { ignoreUnknownKeys = true }

    fun buildSyncPlan(
        localCategories: List<CategoryEntity>,
        serverCategories: List<NextcloudCategoryDto>,
        llmMatchProvider: (suspend (prompt: String) -> String?)? = null
    ): CategorySyncPlan {
        val matched = mutableListOf<CategoryMatchResult>()
        val unmatchedLocal = localCategories.toMutableList()
        val unmatchedServer = serverCategories.toMutableList()

        // 1. Match by serverId
        val iteratorByServerId = unmatchedLocal.iterator()
        while (iteratorByServerId.hasNext()) {
            val local = iteratorByServerId.next()
            if (!local.serverId.isNullOrBlank()) {
                val serverMatch = unmatchedServer.find { it.id == local.serverId }
                if (serverMatch != null) {
                    matched.add(
                        CategoryMatchResult(
                            localCategory = local,
                            serverCategory = serverMatch,
                            isExactMatch = true,
                            matchReason = "Matched by Server ID"
                        )
                    )
                    iteratorByServerId.remove()
                    unmatchedServer.remove(serverMatch)
                }
            }
        }

        // 2. Match by case-insensitive name & income flag
        val iteratorByName = unmatchedLocal.iterator()
        while (iteratorByName.hasNext()) {
            val local = iteratorByName.next()
            val serverMatch = unmatchedServer.find { 
                it.name.equals(local.name, ignoreCase = true) && it.income == local.isIncome
            }
            if (serverMatch != null) {
                matched.add(
                    CategoryMatchResult(
                        localCategory = local,
                        serverCategory = serverMatch,
                        isExactMatch = true,
                        matchReason = "Exact name match (${local.name})"
                    )
                )
                iteratorByName.remove()
                unmatchedServer.remove(serverMatch)
            }
        }

        // 3. Fallback Tier: Multi-Language / Fuzzy (If LLM provider available)
        // (Note: LLM async call can be executed if provided, otherwise remaining are unlinked)

        return CategorySyncPlan(
            matched = matched,
            toPushToServer = unmatchedLocal,
            toPullToClient = unmatchedServer
        )
    }

    suspend fun matchRemainingWithLlm(
        unmatchedLocal: List<CategoryEntity>,
        unmatchedServer: List<NextcloudCategoryDto>,
        llmCall: suspend (prompt: String) -> String?
    ): List<LlmCategoryMatchItem> {
        if (unmatchedLocal.isEmpty() || unmatchedServer.isEmpty()) return emptyList()

        val prompt = """
            You are a multi-language financial category matching assistant.
            Match equivalent categories between the Local list and Server list, handling different languages (e.g. English, German, Ukrainian, etc.).
            
            Local Categories:
            ${unmatchedLocal.joinToString("\n") { "id=${it.id}, name='${it.name}', income=${it.isIncome}" }}
            
            Server Categories:
            ${unmatchedServer.joinToString("\n") { "id='${it.id}', name='${it.name}', income=${it.income}" }}
            
            Return JSON format only:
            {
              "matches": [
                { "localId": 123, "serverId": "uuid-xyz", "reason": "German 'Lebensmittel' matches English 'Groceries'" }
              ]
            }
        """.trimIndent()

        val rawResponse = llmCall(prompt) ?: return emptyList()
        return try {
            val cleaned = rawResponse.substringAfter("{").substringBeforeLast("}")
            val jsonStr = "{$cleaned}"
            json.decodeFromString<LlmCategoryMatchResponse>(jsonStr).matches
        } catch (e: Exception) {
            emptyList()
        }
    }
}
