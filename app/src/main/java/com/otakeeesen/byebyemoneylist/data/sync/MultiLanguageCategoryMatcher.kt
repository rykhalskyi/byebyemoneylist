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
        serverCategories: List<NextcloudCategoryDto>
    ): CategorySyncPlan {
        val matched = mutableListOf<CategoryMatchResult>()
        val unmatchedLocal = localCategories.toMutableList()
        val unmatchedServer = serverCategories.toMutableList()

        // 1. Already linked via a previous sync.
        matchAndRemove(unmatchedLocal, unmatchedServer, matched, isExactMatch = true, reason = { _, _ ->
            "Matched by Server ID"
        }) { local ->
            local.serverId?.takeIf { it.isNotBlank() }?.let { id ->
                unmatchedServer.firstOrNull { it.id == id }
            }
        }

        // 2. Same name (case-insensitive) and same income flag.
        matchAndRemove(unmatchedLocal, unmatchedServer, matched, isExactMatch = true, reason = { local, _ ->
            "Exact name match (${local.name})"
        }) { local ->
            unmatchedServer.firstOrNull {
                it.name.equals(local.name, ignoreCase = true) && it.income == local.isIncome
            }
        }

        // 3. Parent categories with shared leaf children ("Supermarket" and "Food" are the same
        // category if both contain Bread, Milk, Eggs), even when their names differ completely.
        val localChildrenByParent = localCategories.groupBy { it.parentId }
        val serverChildrenByParent = serverCategories.groupBy { it.parentId }

        matchAndRemove(unmatchedLocal, unmatchedServer, matched, isExactMatch = false, reason = { local, server ->
            val overlap = childOverlap(
                leafNames(local.id, localChildrenByParent),
                leafNames(server.id, serverChildrenByParent)
            )
            "Matched by child overlap (${(overlap * 100).toInt()}% shared children)"
        }) { local ->
            val localLeaves = leafNames(local.id, localChildrenByParent)
            unmatchedServer
                .mapNotNull { server ->
                    val overlap = childOverlap(localLeaves, leafNames(server.id, serverChildrenByParent))
                    if (overlap >= CHILD_OVERLAP_THRESHOLD) server to overlap else null
                }
                .maxByOrNull { it.second }
                ?.first
        }

        return CategorySyncPlan(
            matched = matched,
            toPushToServer = unmatchedLocal,
            toPullToClient = unmatchedServer
        )
    }

    private fun matchAndRemove(
        unmatchedLocal: MutableList<CategoryEntity>,
        unmatchedServer: MutableList<NextcloudCategoryDto>,
        matched: MutableList<CategoryMatchResult>,
        isExactMatch: Boolean,
        reason: (CategoryEntity, NextcloudCategoryDto) -> String,
        findServer: (CategoryEntity) -> NextcloudCategoryDto?
    ) {
        val iterator = unmatchedLocal.iterator()
        while (iterator.hasNext()) {
            val local = iterator.next()
            val server = findServer(local)
            if (server != null) {
                matched.add(CategoryMatchResult(local, server, isExactMatch, reason(local, server)))
                iterator.remove()
                unmatchedServer.remove(server)
            }
        }
    }

    private fun childOverlap(localLeaves: Set<String>, serverLeaves: Set<String>): Double {
        if (localLeaves.size < MIN_SHARED_CHILDREN || serverLeaves.size < MIN_SHARED_CHILDREN) return 0.0
        val shared = localLeaves.intersect(serverLeaves).size
        if (shared < MIN_SHARED_CHILDREN) return 0.0
        return shared.toDouble() / localLeaves.union(serverLeaves).size
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
        return requestLlmMatches(
            buildLlmPrompt(
                allLocal, allServer, allLocal, allServer,
                intro = "You are given the FULL list of the user's local categories and the FULL list of categories from their Nextcloud server.",
                localSection = "Local Categories:",
                serverSection = "Server Categories:"
            ),
            llmCall
        )
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
        return requestLlmMatches(
            buildLlmPrompt(
                allLocal, allServer, unmatchedLocal, unmatchedServer,
                intro = "The categories below are the ONLY ones that remain unmatched after an exact-match pass.",
                localSection = "Unmatched Local Categories:",
                serverSection = "Unmatched Server Categories:"
            ),
            llmCall
        )
    }

    private fun buildLlmPrompt(
        allLocal: List<CategoryEntity>,
        allServer: List<NextcloudCategoryDto>,
        listedLocal: List<CategoryEntity>,
        listedServer: List<NextcloudCategoryDto>,
        intro: String,
        localSection: String,
        serverSection: String
    ): String {
        val localById = allLocal.associateBy { it.id }
        val serverById = allServer.associateBy { it.id }
        val localChildrenByParent = allLocal.groupBy { it.parentId }
        val serverChildrenByParent = allServer.groupBy { it.parentId }

        fun localChildren(c: CategoryEntity) =
            localChildrenByParent[c.id].orEmpty().joinToString(", ") { it.name }

        fun serverChildren(c: NextcloudCategoryDto) =
            serverChildrenByParent[c.id].orEmpty().joinToString(", ") { it.name }

        return """
            You are a multi-language financial category matching assistant.
            Match equivalent categories between the Local list and Server list, handling different languages (e.g. English, German, Ukrainian, etc.).
            $intro

            CRITICAL — use this BOTTOM-UP matching strategy:
            1. First match LEAF categories (categories with children='') by name or translation.
               Singular and plural forms are the SAME category (e.g. 'Subscription' == 'Subscriptions',
               'Vegetable' == 'Vegetables').
            2. Two PARENT categories are THE SAME if they share most of the same child categories, even when their
               names differ completely (e.g. client 'Supermarket' with children=[Bread, Milk, Eggs] vs server 'Food'
               with children=[Bread, Milk, Eggs]).
            3. The hierarchy path is ONLY a hint for disambiguating ambiguous leaf names. It is NOT a rejection
               rule: never refuse a match just because the full paths differ at the root.
            4. Match as many genuinely equivalent categories as you can, but do NOT force matches between unrelated categories.

            $localSection
            ${listedLocal.joinToString("\n") { "id=${it.id}, name='${it.name}', income=${it.isIncome}, path='${localPath(it, localById)}', children='${localChildren(it)}'" }}

            $serverSection
            ${listedServer.joinToString("\n") { "id='${it.id}', name='${it.name}', income=${it.income}, path='${serverPath(it, serverById)}', children='${serverChildren(it)}'" }}

            Return JSON format only:
            {
              "matches": [
                { "localId": 123, "serverId": "uuid-xyz", "reason": "Both contain 'Bread', 'Milk', 'Eggs' -> 'Supermarket' and 'Food' are the same category" }
              ]
            }
        """.trimIndent()
    }

    private suspend fun requestLlmMatches(
        prompt: String,
        llmCall: suspend (prompt: String) -> String?
    ): List<LlmCategoryMatchItem> {
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
