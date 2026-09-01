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

    companion object {
        const val LLM_SYSTEM_INSTRUCTION = "You are a multi-language category matching assistant. Return only valid JSON in the requested format. No extra text, no markdown."
        const val CHILD_OVERLAP_THRESHOLD = 0.6
        const val MIN_SHARED_CHILDREN = 2
    }

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

        // 3. Match parent categories by shared leaf children (bottom-up inference).
        // "Supermarket" and "Food" are the same category if they share most of their leaf children.
        val localChildrenByParent = localCategories.groupBy { it.parentId }
        val serverChildrenByParent = serverCategories.groupBy { it.parentId }

        val parentIterator = unmatchedLocal.iterator()
        while (parentIterator.hasNext()) {
            val local = parentIterator.next()
            val localLeafNames = leafNames(local.id, localChildrenByParent)
            if (localLeafNames.size < MIN_SHARED_CHILDREN) continue

            var best: NextcloudCategoryDto? = null
            var bestOverlap = 0.0
            for (server in unmatchedServer) {
                val serverLeafNames = leafNames(server.id, serverChildrenByParent)
                if (serverLeafNames.size < MIN_SHARED_CHILDREN) continue
                val shared = localLeafNames.intersect(serverLeafNames).size
                if (shared < MIN_SHARED_CHILDREN) continue
                val overlap = shared.toDouble() / localLeafNames.union(serverLeafNames).size
                if (overlap >= CHILD_OVERLAP_THRESHOLD && overlap > bestOverlap) {
                    best = server
                    bestOverlap = overlap
                }
            }
            if (best != null) {
                matched.add(
                    CategoryMatchResult(
                        localCategory = local,
                        serverCategory = best,
                        isExactMatch = false,
                        matchReason = "Matched by child overlap (${(bestOverlap * 100).toInt()}% shared children)"
                    )
                )
                parentIterator.remove()
                unmatchedServer.remove(best)
            }
        }

        // 4. Fallback Tier: Multi-Language / Fuzzy (If LLM provider available)
        // (Note: LLM async call can be executed if provided, otherwise remaining are unlinked)

        return CategorySyncPlan(
            matched = matched,
            toPushToServer = unmatchedLocal,
            toPullToClient = unmatchedServer
        )
    }

    private fun leafNames(
        parentId: Long?,
        childrenByParent: Map<Long?, List<CategoryEntity>>
    ): Set<String> = childrenByParent[parentId].orEmpty()
        .filter { childrenByParent[it.id].isNullOrEmpty() }
        .map { it.name.trim().lowercase() }
        .toSet()

    private fun leafNames(
        parentId: String?,
        childrenByParent: Map<String?, List<NextcloudCategoryDto>>
    ): Set<String> = childrenByParent[parentId].orEmpty()
        .filter { childrenByParent[it.id].isNullOrEmpty() }
        .map { it.name.trim().lowercase() }
        .toSet()

    suspend fun matchAllWithLlm(
        allLocal: List<CategoryEntity>,
        allServer: List<NextcloudCategoryDto>,
        llmCall: suspend (prompt: String) -> String?
    ): List<LlmCategoryMatchItem> {
        if (allLocal.isEmpty() || allServer.isEmpty()) return emptyList()

        val localById = allLocal.associateBy { it.id }
        val serverById = allServer.associateBy { it.id }
        val localChildrenByParent = allLocal.groupBy { it.parentId }
        val serverChildrenByParent = allServer.groupBy { it.parentId }

        fun localChildren(c: CategoryEntity) =
            localChildrenByParent[c.id].orEmpty().joinToString(", ") { it.name }

        fun serverChildren(c: NextcloudCategoryDto) =
            serverChildrenByParent[c.id].orEmpty().joinToString(", ") { it.name }

        val prompt = """
            You are a multi-language financial category matching assistant.
            You are given the FULL list of the user's local categories and the FULL list of categories from their Nextcloud server.
            Match equivalent categories between the Local list and Server list, handling different languages (e.g. English, German, Ukrainian, etc.).

            CRITICAL — use this BOTTOM-UP matching strategy:
            1. First match LEAF categories (categories with children='') by name or translation.
               Singular and plural forms are the SAME category (e.g. 'Subscription' == 'Subscriptions',
               'Vegetable' == 'Vegetables').
            2. Decide whether two PARENT categories are the same by looking at their CHILDREN:
               - If parent A and parent B share most of the same child categories, they are THE SAME category,
                 even if their names are completely different.
                 Example: client 'Supermarket' with children=[Bread, Milk, Eggs] and server 'Food' with
                 children=[Bread, Milk, Eggs] are the SAME category because ~100% of their children match.
            3. The hierarchy path is ONLY a hint for disambiguating ambiguous leaf names. It is NOT a rejection
               rule: never refuse a match just because the full paths differ at the root.
            4. Match as many genuinely equivalent categories as you can, but do NOT force matches between unrelated categories.

            Local Categories:
            ${allLocal.joinToString("\n") { "id=${it.id}, name='${it.name}', income=${it.isIncome}, path='${localPath(it, localById)}', children='${localChildren(it)}'" }}

            Server Categories:
            ${allServer.joinToString("\n") { "id='${it.id}', name='${it.name}', income=${it.income}, path='${serverPath(it, serverById)}', children='${serverChildren(it)}'" }}

            Return JSON format only:
            {
              "matches": [
                { "localId": 123, "serverId": "uuid-xyz", "reason": "Both contain 'Bread', 'Milk', 'Eggs' -> 'Supermarket' and 'Food' are the same category" }
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

    fun buildSyncPlanFromLlm(
        localCategories: List<CategoryEntity>,
        serverCategories: List<NextcloudCategoryDto>,
        llmMatches: List<LlmCategoryMatchItem>
    ): CategorySyncPlan {
        val localById = localCategories.associateBy { it.id }
        val serverById = serverCategories.associateBy { it.id }

        val matched = mutableListOf<CategoryMatchResult>()
        val matchedLocalIds = mutableSetOf<Long>()
        val matchedServerIds = mutableSetOf<String?>()

        for (m in llmMatches) {
            val local = localById[m.localId] ?: continue
            val server = serverById[m.serverId] ?: continue
            if (local.id in matchedLocalIds || server.id in matchedServerIds) continue
            matchedLocalIds.add(local.id)
            matchedServerIds.add(server.id)
            matched.add(
                CategoryMatchResult(
                    localCategory = local,
                    serverCategory = server,
                    isExactMatch = false,
                    matchReason = "LLM match: ${m.reason}"
                )
            )
        }

        return CategorySyncPlan(
            matched = matched,
            toPushToServer = localCategories.filter { it.id !in matchedLocalIds },
            toPullToClient = serverCategories.filter { it.id !in matchedServerIds }
        )
    }

    private fun localPath(category: CategoryEntity, allById: Map<Long, CategoryEntity>): String {
        val parts = mutableListOf(category.name)
        var current = category.parentId?.let { allById[it] }
        while (current != null) {
            parts.add(0, current.name)
            current = current.parentId?.let { allById[it] }
        }
        return parts.joinToString(" / ")
    }

    private fun serverPath(category: NextcloudCategoryDto, allById: Map<String?, NextcloudCategoryDto>): String {
        val parts = mutableListOf(category.name)
        var current = category.parentId?.let { allById[it] }
        while (current != null) {
            parts.add(0, current.name)
            current = current.parentId?.let { allById[it] }
        }
        return parts.joinToString(" / ")
    }

    fun mergePlans(base: CategorySyncPlan, llmExtra: CategorySyncPlan): CategorySyncPlan {
        val matchedLocalIds = base.matched.mapTo(mutableSetOf()) { it.localCategory.id }
        val matchedServerIds = base.matched.mapTo(mutableSetOf()) { it.serverCategory.id }
        val matched = base.matched.toMutableList()
        for (m in llmExtra.matched) {
            if (m.localCategory.id in matchedLocalIds || m.serverCategory.id in matchedServerIds) continue
            matched.add(m)
            matchedLocalIds.add(m.localCategory.id)
            matchedServerIds.add(m.serverCategory.id)
        }
        return CategorySyncPlan(
            matched = matched,
            toPushToServer = llmExtra.toPushToServer.filter { it.id !in matchedLocalIds },
            toPullToClient = llmExtra.toPullToClient.filter { it.id !in matchedServerIds }
        )
    }

    suspend fun matchRemainingWithLlm(
        allLocal: List<CategoryEntity>,
        allServer: List<NextcloudCategoryDto>,
        unmatchedLocal: List<CategoryEntity>,
        unmatchedServer: List<NextcloudCategoryDto>,
        llmCall: suspend (prompt: String) -> String?
    ): List<LlmCategoryMatchItem> {
        if (unmatchedLocal.isEmpty() || unmatchedServer.isEmpty()) return emptyList()

        val localById = allLocal.associateBy { it.id }
        val serverById = allServer.associateBy { it.id }
        val localChildrenByParent = allLocal.groupBy { it.parentId }
        val serverChildrenByParent = allServer.groupBy { it.parentId }

        fun localChildren(c: CategoryEntity) =
            localChildrenByParent[c.id].orEmpty().joinToString(", ") { it.name }

        fun serverChildren(c: NextcloudCategoryDto) =
            serverChildrenByParent[c.id].orEmpty().joinToString(", ") { it.name }

        val prompt = """
            You are a multi-language financial category matching assistant.
            The categories below are the ONLY ones that remain unmatched after an exact-match pass.
            Decide which of them are equivalent and link them, handling different languages (e.g. English, German, Ukrainian, etc.).

            CRITICAL — use this BOTTOM-UP matching strategy:
            1. First match LEAF categories (categories with children='') by name or translation.
               Singular and plural forms are the SAME category (e.g. 'Subscription' == 'Subscriptions',
               'Vegetable' == 'Vegetables').
            2. Two PARENT categories are THE SAME if they share most of the same child categories, even when their
               names differ completely (e.g. client 'Supermarket' with children=[Bread, Milk, Eggs] vs server 'Food'
               with children=[Bread, Milk, Eggs]).
            3. The hierarchy path is ONLY a hint for ambiguous leaf names. NEVER reject a match just because the
               full paths differ at the root.
            4. Match as many of the remaining categories as you can, but do NOT force matches between unrelated ones.

            Unmatched Local Categories:
            ${unmatchedLocal.joinToString("\n") { "id=${it.id}, name='${it.name}', income=${it.isIncome}, path='${localPath(it, localById)}', children='${localChildren(it)}'" }}

            Unmatched Server Categories:
            ${unmatchedServer.joinToString("\n") { "id='${it.id}', name='${it.name}', income=${it.income}, path='${serverPath(it, serverById)}', children='${serverChildren(it)}'" }}

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
