package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import com.otakeeesen.byebyemoneylist.data.local.dao.CategoryDao
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncMatch
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncPlan
import com.otakeeesen.byebyemoneylist.util.toLocalColorHex
import com.otakeeesen.byebyemoneylist.util.toServerColorHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CategorySyncRepository(
    private val categoryDao: CategoryDao,
    private val preferencesManager: PreferencesManager,
    private val apiClient: NextcloudApiClient = NextcloudApiClient(),
    private val matcher: MultiLanguageCategoryMatcher = MultiLanguageCategoryMatcher()
) : SyncRepository<CategoryEntity, NextcloudCategoryDto> {

    override suspend fun generateSyncPlan(
        useLlm: Boolean,
        llmCall: (suspend (prompt: String) -> String?)?,
        onPhase: (SyncPhase) -> Unit
    ): Result<SyncPlan<CategoryEntity, NextcloudCategoryDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = preferencesManager.getNextcloudUrl()
            val user = preferencesManager.getNextcloudUsername()
            val pass = preferencesManager.getNextcloudPassword()

            if (url.isBlank() || user.isBlank() || pass.isBlank()) {
                throw Exception("Nextcloud credentials are not fully configured in settings.")
            }

            onPhase(SyncPhase.FETCHING)
            val serverCategories = apiClient.fetchCategories(url, user, pass).getOrThrow()
            val localCategories = categoryDao.getAllCategoriesOnce()

            var plan = matcher.buildSyncPlan(localCategories, serverCategories)
            if (useLlm && llmCall != null) {
                onPhase(SyncPhase.LLM_MATCHING)
                val llmMatches = matcher.matchRemainingWithLlm(
                    allLocal = localCategories,
                    allServer = serverCategories,
                    unmatchedLocal = plan.toPushToServer,
                    unmatchedServer = plan.toPullToClient,
                    llmCall = llmCall
                )
                if (llmMatches.isNotEmpty()) {
                    val llmPlan = matcher.buildSyncPlanFromLlm(
                        plan.toPushToServer,
                        plan.toPullToClient,
                        llmMatches
                    )
                    plan = matcher.mergePlans(plan, llmPlan)
                }
            }
            SyncPlan(
                matched = plan.matched.map {
                    SyncMatch(local = it.localCategory, server = it.serverCategory, reason = it.matchReason)
                },
                toPushToServer = plan.toPushToServer,
                toPullToClient = plan.toPullToClient
            )
        }
    }

    override suspend fun executeSyncPlan(
        plan: SyncPlan<CategoryEntity, NextcloudCategoryDto>,
        pushItems: List<CategoryEntity>,
        pullItems: List<NextcloudCategoryDto>,
        linkedPairs: List<Pair<CategoryEntity, NextcloudCategoryDto>>
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val url = preferencesManager.getNextcloudUrl()
            val user = preferencesManager.getNextcloudUsername()
            val pass = preferencesManager.getNextcloudPassword()

            // 1. Save matched serverId updates locally
            for ((local, server) in linkedPairs) {
                if (server.id != null) {
                    categoryDao.updateServerId(local.id, server.id)
                }
            }

            // 2. Download missing categories from Server -> Client DB, rebuilding the local hierarchy.
            val serverIdToLocalId = categoryDao.getAllCategoriesOnce()
                .mapNotNull { cat -> cat.serverId?.takeIf { it.isNotBlank() }?.let { it to cat.id } }
                .toMap()
                .toMutableMap()

            val pullById = pullItems.mapNotNull { it.id?.let { id -> id to it } }.toMap()

            fun serverDepth(category: NextcloudCategoryDto): Int {
                var depth = 0
                val seen = mutableSetOf<String>()
                var cursor: NextcloudCategoryDto? = category
                while (cursor != null) {
                    val parent = cursor.parentId?.let { pullById[it] } ?: break
                    val parentServerId = parent.id ?: break
                    if (parentServerId in serverIdToLocalId) break
                    if (!seen.add(parentServerId)) break
                    depth++
                    cursor = parent
                }
                return depth
            }

            val sortedPullCategories = pullItems.sortedWith(
                compareBy({ serverDepth(it) }, { it.name.lowercase() })
            )

            for (serverCat in sortedPullCategories) {
                val serverId = serverCat.id ?: continue
                if (serverId in serverIdToLocalId) continue
                val parentLocalId = serverCat.parentId?.let { serverIdToLocalId[it] }
                val newLocal = CategoryEntity(
                    name = serverCat.name,
                    color = toLocalColorHex(serverCat.color) ?: "#FF6B6B",
                    emoji = serverCat.emoji,
                    isIncome = serverCat.income,
                    parentId = parentLocalId,
                    serverId = serverId
                )
                val newId = categoryDao.insertCategory(newLocal)
                serverIdToLocalId[serverId] = newId
            }

            // 3. Batch Upload missing categories from Client -> Server, preserving hierarchy.
            if (pushItems.isNotEmpty()) {
                val allLocalCategories = categoryDao.getAllCategoriesOnce()
                val dtoList = buildHierarchicalPushDtos(pushItems, allLocalCategories)

                val createdDtos = apiClient.createCategoryBatch(url, user, pass, dtoList).getOrThrow()

                // Update local Categories with assigned server UUIDs returned by Nextcloud
                for (created in createdDtos) {
                    val tempIdLong = created.tempId?.toLongOrNull()
                    if (tempIdLong != null && created.id != null) {
                        categoryDao.updateServerId(tempIdLong, created.id)
                    }
                }
            }

            true
        }
    }
}

internal fun buildHierarchicalPushDtos(
    pushCategories: List<CategoryEntity>,
    allLocalCategories: List<CategoryEntity>
): List<NextcloudCategoryDto> {
    val allById = allLocalCategories.associateBy { it.id }
    val pushOrder = mutableListOf<CategoryEntity>()
    val pushedIds = mutableSetOf<Long>()

    fun addWithUnsyncedAncestors(category: CategoryEntity) {
        if (category.id in pushedIds) return
        val parent = category.parentId?.let { allById[it] }
        if (parent != null && parent.serverId.isNullOrBlank()) {
            addWithUnsyncedAncestors(parent)
        }
        pushedIds.add(category.id)
        pushOrder.add(category)
    }

    pushCategories.forEach { addWithUnsyncedAncestors(it) }

    return pushOrder.map { cat ->
        val parentRef = cat.parentId?.let { parentId -> allById[parentId] }?.let { parent ->
            if (parent.id in pushedIds) {
                // The parent is (re)created in this batch, so point at its tempId.
                // Its stale serverId, if any, must not be reused.
                parent.id.toString()
            } else {
                parent.serverId?.takeIf { it.isNotBlank() }
            }
        }
        NextcloudCategoryDto(
            name = cat.name,
            color = toServerColorHex(cat.color),
            emoji = cat.emoji,
            income = cat.isIncome,
            parentId = parentRef,
            tempId = cat.id.toString()
        )
    }
}
