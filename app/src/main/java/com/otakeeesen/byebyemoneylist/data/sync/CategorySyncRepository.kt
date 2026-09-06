package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import com.otakeeesen.byebyemoneylist.data.local.dao.CategoryDao
import com.otakeeesen.byebyemoneylist.data.local.dao.SyncPendingDeleteDao
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.PENDING_DELETE_ENTITY_CATEGORY
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncConflict
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncContentState
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncMatch
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncPlan
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncStateEntity
import com.otakeeesen.byebyemoneylist.util.toLocalColorHex
import com.otakeeesen.byebyemoneylist.util.toServerColorHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CategorySyncRepository(
    private val categoryDao: CategoryDao,
    private val syncStateDao: SyncStateDao,
    private val preferencesManager: PreferencesManager,
    private val pendingDeleteDao: SyncPendingDeleteDao? = null,
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

            val localServerIdByParent = localCategories
                .mapNotNull { cat -> cat.serverId?.takeIf { it.isNotBlank() }?.let { cat.id to it } }
                .toMap()

            val existingStates = syncStateDao.getAll(SyncStateEntity.TYPE_CATEGORY)
                .associateBy { it.localId }
            val annotation = SyncStateResolver.annotate(
                matched = plan.matched.map {
                    SyncMatch(local = it.localCategory, server = it.serverCategory, reason = it.matchReason)
                },
                existingByLocalId = existingStates,
                entityType = SyncStateEntity.TYPE_CATEGORY,
                localId = { it.id },
                serverId = { it.id },
                toLocalJson = { cat ->
                    SyncProjection.categoryLocal(
                        category = cat,
                        parentServerId = cat.parentId?.let { localServerIdByParent[it] }
                    )
                },
                toServerJson = { SyncProjection.categoryServer(it) }
            )

            SyncPlan(
                matched = annotation.matches,
                toPushToServer = plan.toPushToServer,
                toPullToClient = plan.toPullToClient,
                toUpdateServer = annotation.matches
                    .filter { it.contentState == SyncContentState.LOCAL_CHANGED }
                    .map { it.local },
                toUpdateLocal = annotation.matches
                    .filter { it.contentState == SyncContentState.SERVER_CHANGED }
                    .map { it.server },
                conflicts = annotation.matches
                    .filter { it.contentState == SyncContentState.CONFLICT }
                    .map { SyncConflict(it) }
            )
        }
    }

    override suspend fun executeSyncPlan(
        plan: SyncPlan<CategoryEntity, NextcloudCategoryDto>,
        pushItems: List<CategoryEntity>,
        pullItems: List<NextcloudCategoryDto>,
        linkedPairs: List<Pair<CategoryEntity, NextcloudCategoryDto>>,
        updateToServer: List<CategoryEntity>,
        updateToLocal: List<NextcloudCategoryDto>
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val url = preferencesManager.getNextcloudUrl()
            val user = preferencesManager.getNextcloudUsername()
            val pass = preferencesManager.getNextcloudPassword()
            val now = System.currentTimeMillis()

            // 0. Drain pending deletes for categories
            if (pendingDeleteDao != null) {
                for (pending in pendingDeleteDao.getAllByEntity(PENDING_DELETE_ENTITY_CATEGORY)) {
                    apiClient.deleteCategory(url, user, pass, pending.serverId).getOrThrow()
                    pendingDeleteDao.deleteById(pending.id)
                }
            }

            // 1. Save matched serverId updates locally and persist baselines
            val localServerIdByParent = categoryDao.getAllCategoriesOnce()
                .mapNotNull { cat -> cat.serverId?.takeIf { it.isNotBlank() }?.let { cat.id to it } }
                .toMap()

            for ((local, server) in linkedPairs) {
                if (server.id != null) {
                    categoryDao.updateServerId(local.id, server.id)
                    val parentServerId = local.parentId?.let { localServerIdByParent[it] }
                    syncStateDao.upsert(
                        SyncStateEntity(
                            entityType = SyncStateEntity.TYPE_CATEGORY,
                            localId = local.id,
                            serverId = server.id,
                            baseSnapshot = SyncProjection.categoryLocal(local, parentServerId),
                            lastSyncAt = now
                        )
                    )
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

            // 4. Push pending local edits (rename / recolour / emoji / income / re-parent) as PUTs.
            //    Parents are pushed before children so a parent `parentId` move lands first.
            if (updateToServer.isNotEmpty()) {
                val serverIdByLocalId = categoryDao.getAllCategoriesOnce()
                    .mapNotNull { cat -> cat.serverId?.takeIf { it.isNotBlank() }?.let { cat.id to it } }
                    .toMap()

                for (cat in orderParentsBeforeChildren(updateToServer)) {
                    val serverId = cat.serverId?.takeIf { it.isNotBlank() } ?: continue
                    val parentServerId = cat.parentId?.let { serverIdByLocalId[it] }
                    apiClient.updateCategory(
                        url, user, pass, serverId,
                        buildCategoryUpdateRequest(cat, parentServerId)
                    ).getOrThrow()
                    syncStateDao.upsert(
                        SyncStateEntity(
                            entityType = SyncStateEntity.TYPE_CATEGORY,
                            localId = cat.id,
                            serverId = serverId,
                            baseSnapshot = SyncProjection.categoryLocal(cat, parentServerId),
                            lastSyncAt = now
                        )
                    )
                }
            }

            // 5. Pull pending remote edits: overwrite shared fields (name / colour / emoji /
            //    income / parent) from the server DTO; category has no local-only fields.
            if (updateToLocal.isNotEmpty()) {
                val localIdByServerId = categoryDao.getAllCategoriesOnce()
                    .mapNotNull { cat -> cat.serverId?.takeIf { it.isNotBlank() }?.let { it to cat.id } }
                    .toMap()

                for (dto in updateToLocal) {
                    val serverId = dto.id ?: continue
                    val localId = localIdByServerId[serverId] ?: continue
                    val parentLocalId = dto.parentId
                        ?.takeIf { it.isNotBlank() }
                        ?.let { localIdByServerId[it] }
                    categoryDao.updateFromServer(
                        id = localId,
                        name = dto.name,
                        color = toLocalColorHex(dto.color) ?: "#FF6B6B",
                        emoji = dto.emoji,
                        isIncome = dto.income,
                        parentId = parentLocalId
                    )
                    syncStateDao.upsert(
                        SyncStateEntity(
                            entityType = SyncStateEntity.TYPE_CATEGORY,
                            localId = localId,
                            serverId = serverId,
                            baseSnapshot = SyncProjection.categoryServer(dto),
                            lastSyncAt = now
                        )
                    )
                }
            }

            true
        }
    }
}

/**
 * Sorts categories slated for a push update so that ancestors precede descendants —
 * a parent's `parentId` move must reach the server before (or independently of) its
 * children's updates. Parents outside the update set are ignored.
 */
internal fun orderParentsBeforeChildren(categories: List<CategoryEntity>): List<CategoryEntity> {
    val byId = categories.associateBy { it.id }

    fun depth(category: CategoryEntity): Int {
        var depth = 0
        val seen = mutableSetOf<Long>()
        var cursor: CategoryEntity? = category
        while (cursor != null) {
            val parent = cursor.parentId?.let { byId[it] }
            if (parent == null || !seen.add(parent.id)) break
            depth++
            cursor = parent
        }
        return depth
    }

    return categories.sortedBy { depth(it) }
}

/**
 * Builds the PUT payload for a category push update. References are expressed in the
 * server domain (`parentId` = the parent category's `serverId`) and the colour is
 * normalised to the server hex form, mirroring `buildHierarchicalPushDtos`.
 */
internal fun buildCategoryUpdateRequest(
    category: CategoryEntity,
    parentServerId: String?
): NextcloudCategoryUpdateRequest = NextcloudCategoryUpdateRequest(
    name = category.name,
    color = toServerColorHex(category.color),
    emoji = category.emoji,
    parentId = parentServerId?.takeIf { it.isNotBlank() },
    income = category.isIncome
)

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
