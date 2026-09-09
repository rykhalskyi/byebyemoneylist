package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import com.otakeeesen.byebyemoneylist.data.local.dao.CategoryDao
import com.otakeeesen.byebyemoneylist.data.local.dao.ProductDao
import com.otakeeesen.byebyemoneylist.data.local.dao.ShoppingListDao
import com.otakeeesen.byebyemoneylist.data.local.dao.StoreDao
import com.otakeeesen.byebyemoneylist.data.local.dao.SyncPendingDeleteDao
import com.otakeeesen.byebyemoneylist.data.local.entity.PENDING_DELETE_ENTITY_SHOPPING_LIST
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListCategoryCrossRef
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListItemEntity
import com.otakeeesen.byebyemoneylist.data.sync.SyncProjection.ShoppingListItemDigest
import com.otakeeesen.byebyemoneylist.data.sync.SyncStateDao
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncContentState
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Aggregated outcome of one shopping-list sync run. */
data class ShoppingListsSyncResult(
    val listsOnServer: Int = 0,
    val listsOnClient: Int = 0,
    val pulled: Int = 0,
    val created: Int = 0,
    val updated: Int = 0,
    val deleted: Int = 0,
    val skippedItems: Int = 0,
) {
    val pushed: Int get() = created + updated
}

/** Content state of one serverId-linked local/server list pair (git-style). */
data class LinkedShoppingListState(
    val local: ShoppingListEntity,
    val server: NextcloudListDto,
    val state: SyncContentState,
    /** Canonical local projection used to advance the base after a push. */
    val localJson: String = "",
    /** Canonical server projection used to advance the base after a pull. */
    val serverJson: String = "",
    /** Fetched server twin items (so execution need not re-fetch to delete/reconcile). */
    val serverItems: List<NextcloudListItemDto> = emptyList(),
)

/** Per-list decision taken by plan execution. */
enum class ShoppingListLinkAction {
    /** Leave the pair alone (state stays as-is). */
    SKIP,
    /** Push the local list to the server (full-state PUT + item full-replace). */
    PUSH_LOCAL,
    /** Overwrite the local list from the server (header + category + item reconcile). */
    PULL_SERVER,
}

/**
 * Explicit user choice for a conflicted list pair (from the per-list sync screen).
 * Unlike the match-based groups there is no matcher, so a "conflict" is resolved
 * wholesale towards one side — no per-item merge.
 */
enum class ShoppingListResolution {
    USE_LOCAL,
    USE_SERVER,
}

/**
 * Auto-sync default: everything is applied as the plan suggests, except that an
 * unresolved [SyncContentState.CONFLICT] is **skipped** (never auto-resolved) so no
 * side's data is silently overwritten. The caller applies explicit user resolutions
 * ([ShoppingListResolution]) on top of this default.
 */
fun defaultShoppingListAction(state: SyncContentState): ShoppingListLinkAction = when (state) {
    SyncContentState.IN_SYNC -> ShoppingListLinkAction.SKIP
    SyncContentState.LOCAL_CHANGED -> ShoppingListLinkAction.PUSH_LOCAL
    SyncContentState.SERVER_CHANGED -> ShoppingListLinkAction.PULL_SERVER
    SyncContentState.CONFLICT -> ShoppingListLinkAction.SKIP
}

/**
 * Plan over the shopping-list mirror without a matcher: lists are linked purely by
 * `serverId`. Linked pairs carry a git-style content state (vs the `sync_state` base
 * snapshot); unknown server lists form the pull-create pool and local lists without a
 * `serverId` the push-create pool. Remote deletions are not handled (documented open
 * question) — a local list whose server twin vanished is omitted.
 */
data class ShoppingListSyncPlan(
    val linked: List<LinkedShoppingListState> = emptyList(),
    val pullToCreate: List<NextcloudListDto> = emptyList(),
    val pushToCreate: List<ShoppingListEntity> = emptyList(),
) {
    val linkedCount: Int get() = linked.size
    val localChangedCount: Int get() = linked.count { it.state == SyncContentState.LOCAL_CHANGED }
    val serverChangedCount: Int get() = linked.count { it.state == SyncContentState.SERVER_CHANGED }
    val conflictCount: Int get() = linked.count { it.state == SyncContentState.CONFLICT }
    val inSyncCount: Int get() = linked.count { it.state == SyncContentState.IN_SYNC }

    /**
     * Conflicted pairs for which the user has not yet chosen a side
     * ([ShoppingListResolution]). These are skipped (never auto-applied) by
     * plan execution and surfaced as a count to the caller.
     */
    fun unresolvedConflictCount(resolutions: Map<Long, ShoppingListResolution>): Int =
        linked.count { it.state == SyncContentState.CONFLICT && resolutions[it.local.id] == null }
}

/**
 * Git-style shopping-list sync.
 *
 * Unlike the match-based groups (categories/stores/products), lists have **no match
 * routine** — a list is linked to its server twin purely by [ShoppingListEntity.serverId].
 * C2 reimplements the mirror on the git-state engine:
 *
 * ```
 * plan generation:  fetch server + read local + read sync_state bases
 *   ├─ server list without local twin        → pull-create pool
 *   ├─ local list with serverId == null      → push-create pool
 *   └─ linked pairs                          → IN_SYNC / LOCAL_CHANGED /
 *                                               SERVER_CHANGED / CONFLICT
 * execution (per list):  IN_SYNC skip · LOCAL_CHANGED push ·
 *                        SERVER_CHANGED pull/reconcile · CONFLICT per caller
 * ```
 *
 * **Item sync = full replace on push.** Items have no independent identity on the
 * server, so a push discards the remote item set and recreates it from the local set.
 * Local items whose product has no `serverId` (or that are coupons with a non-positive
 * quantity) cannot be pushed and are skipped/counted. A pull (SERVER_CHANGED) instead
 * *reconciles* the syncable local items to the fetched server set, leaving non-syncable
 * local items (coupons, products not yet on the server) untouched.
 *
 * Every successful push/pull advances the `sync_state` base snapshot to the applied
 * canonical projection, so the next run compares against actual content — not server
 * timestamps. Remote list deletions are deferred (documented open question).
 */
class ShoppingListsSyncRepository(
    private val shoppingListDao: ShoppingListDao,
    private val storeDao: StoreDao,
    private val categoryDao: CategoryDao,
    private val productDao: ProductDao,
    private val pendingDeleteDao: SyncPendingDeleteDao,
    private val syncStateDao: SyncStateDao,
    private val preferencesManager: PreferencesManager,
    private val apiClient: NextcloudApiClient = NextcloudApiClient(),
) {
    /**
     * Bounded-concurrency dispatcher for the per-list server-item fetches during plan
     * generation — avoids N sequential round-trips while capping connection fan-out.
     */
    private val linkedItemFetchDispatcher =
        Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_LIST_ITEM_FETCHES)

    /**
     * Generates the git-style plan: classifies every serverId-linked pair against its
     * stored `sync_state` base snapshot and buckets the pull/push create pools. Pairs
     * that were never baselined (first run after adopting the state model) are baselined
     * against the current *local* projection as `IN_SYNC` — the documented rebase-on-local
     * backfill — so change detection is armed for subsequent runs without a spurious
     * first push.
     */
    suspend fun generateSyncPlan(): Result<ShoppingListSyncPlan> = withContext(Dispatchers.IO) {
        runCatching {
            val url = preferencesManager.getNextcloudUrl()
            val user = preferencesManager.getNextcloudUsername()
            val pass = preferencesManager.getNextcloudPassword()
            checkCredentials(url, user, pass)
            buildPlan(url, user, pass)
        }
    }

    /**
     * Executes a generated plan with a per-list decision. The default applies
     * [defaultShoppingListAction] (auto-apply one-sided changes; unresolved conflicts
     * are skipped); callers with explicit user resolutions — e.g. the per-list sync
     * screen — pass an [action] that overrides it for conflicted pairs.
     */
    suspend fun executeSyncPlan(
        plan: ShoppingListSyncPlan,
        action: (LinkedShoppingListState) -> ShoppingListLinkAction =
            { defaultShoppingListAction(it.state) },
    ): Result<ShoppingListsSyncResult> = withContext(Dispatchers.IO) {
        runCatching {
            val url = preferencesManager.getNextcloudUrl()
            val user = preferencesManager.getNextcloudUsername()
            val pass = preferencesManager.getNextcloudPassword()
            checkCredentials(url, user, pass)
            executePlan(url, user, pass, plan, action)
        }
    }

    /**
     * Deletes the locally queued lists (their server twin) and clears the queue. Must run
     * before a plan is generated so a deleted list is not re-pulled from the server.
     */
    suspend fun deletePendingServerLists(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val url = preferencesManager.getNextcloudUrl()
            val user = preferencesManager.getNextcloudUsername()
            val pass = preferencesManager.getNextcloudPassword()
            checkCredentials(url, user, pass)
            drainPendingDeletes(url, user, pass)
        }
    }

    private fun checkCredentials(url: String, user: String, pass: String) {
        if (url.isBlank() || user.isBlank() || pass.isBlank()) {
            throw Exception("Nextcloud credentials are not fully configured in settings.")
        }
    }

    // ---- Plan generation -----------------------------------------------------------

    private suspend fun buildPlan(url: String, user: String, pass: String): ShoppingListSyncPlan {
        val serverLists = apiClient.fetchLists(url, user, pass).getOrThrow()
        val serverByServerId = serverLists.mapNotNull { it.id?.let { id -> id to it } }.toMap()
        val localLists = shoppingListDao.getAllShoppingListsSynchronous()

        val products = productDao.getAllProductsOnce()
        val productServerIdByLocalId = products.asSequence()
            .mapNotNull { p -> p.serverId?.takeIf { it.isNotBlank() }?.let { p.id to it } }
            .toMap()
        val storeServerIdByLocalId = storeDao.getAllStoresOnce().asSequence()
            .mapNotNull { s -> s.serverId?.takeIf { it.isNotBlank() }?.let { s.id to it } }
            .toMap()
        val categoryServerIdByLocalId = categoryDao.getAllCategoriesOnce().asSequence()
            .mapNotNull { c -> c.serverId?.takeIf { it.isNotBlank() }?.let { c.id to it } }
            .toMap()

        val localListIds = localLists.map { it.id }
        val itemsByListId = shoppingListDao.getItemsForListsSync(localListIds)
            .groupBy { it.shoppingListId }
        val categoryIdsByListId = shoppingListDao.getCategoryCrossRefsForListsSync(localListIds)
            .groupBy { it.shoppingListId }
            .mapValues { (_, refs) -> refs.map { it.categoryId } }

        val existingByLocalId = syncStateDao.getAll(SyncStateEntity.TYPE_SHOPPING_LIST)
            .associateBy { it.localId }
        val knownServerIds = localLists.mapNotNull { it.serverId?.takeIf { id -> id.isNotBlank() } }.toSet()

        val pullToCreate = serverLists.filter { it.id !in knownServerIds }
        val pushToCreate = localLists.filter { it.serverId.isNullOrBlank() }

        // Linked pairs need their server twins' items to detect remote item edits; the
        // list header alone is not enough. Fetching one-by-one below would be N
        // sequential round-trips, so prefetch all linked item sets concurrently.
        val serverItemsByLocalId =
            fetchLinkedServerItems(url, user, pass, localLists, serverByServerId)

        val now = System.currentTimeMillis()
        val linked = mutableListOf<LinkedShoppingListState>()
        val baselines = mutableListOf<SyncStateEntity>()

        for (local in localLists) {
            val serverId = local.serverId?.takeIf { it.isNotBlank() } ?: continue
            val server = serverByServerId[serverId] ?: continue
            val existing = existingByLocalId[local.id]
            val base = if (existing != null && existing.serverId == serverId) {
                existing.baseSnapshot
            } else {
                null
            }

            val localItems = itemsByListId[local.id].orEmpty()
                .mapNotNull { item -> syncableItemDigest(item, productServerIdByLocalId) }
            val localJson = SyncProjection.shoppingListLocal(
                list = local,
                storeServerId = local.storeId?.let { storeServerIdByLocalId[it] },
                categoryServerIds = categoryIdsByListId[local.id].orEmpty()
                    .mapNotNull { categoryServerIdByLocalId[it] },
                items = localItems
            )

            // Prefetched concurrently during plan generation (fetchLinkedServerItems).
            val serverItems = serverItemsByLocalId[local.id].orEmpty()
            val serverJson = SyncProjection.shoppingListServer(
                server,
                serverItems.mapNotNull { serverItemDigest(it) }
            )

            val state = SyncStateResolver.resolveState(base, localJson, serverJson)
            if (base == null) {
                baselines.add(
                    SyncStateEntity(
                        entityType = SyncStateEntity.TYPE_SHOPPING_LIST,
                        localId = local.id,
                        serverId = serverId,
                        baseSnapshot = localJson,
                        lastSyncAt = now
                    )
                )
            }
            linked.add(
                LinkedShoppingListState(
                    local = local,
                    server = server,
                    state = state,
                    localJson = localJson,
                    serverJson = serverJson,
                    serverItems = serverItems,
                )
            )
        }

        baselines.forEach { syncStateDao.upsert(it) }

        return ShoppingListSyncPlan(
            linked = linked,
            pullToCreate = pullToCreate,
            pushToCreate = pushToCreate,
        )
    }

    /**
     * Fetches the server items of every linked list (a local list with a server twin)
     * concurrently on a bounded dispatcher. Fail-fast: one failing fetch fails the whole
     * plan generation, mirroring the previous sequential behaviour.
     *
     * @return server items keyed by the local list id.
     */
    private suspend fun fetchLinkedServerItems(
        url: String,
        user: String,
        pass: String,
        localLists: List<ShoppingListEntity>,
        serverByServerId: Map<String, NextcloudListDto>,
    ): Map<Long, List<NextcloudListItemDto>> {
        val toFetch = localLists.mapNotNull { local ->
            val serverId = local.serverId?.takeIf { it.isNotBlank() }
            if (serverId != null && serverId in serverByServerId) local.id to serverId else null
        }
        if (toFetch.isEmpty()) return emptyMap()
        return coroutineScope {
            toFetch.map { (localId, serverId) ->
                async(linkedItemFetchDispatcher) {
                    localId to apiClient.fetchListItems(url, user, pass, serverId).getOrThrow()
                }
            }.awaitAll().toMap()
        }
    }

    // ---- Execution ----------------------------------------------------------------

    private suspend fun executePlan(
        url: String,
        user: String,
        pass: String,
        plan: ShoppingListSyncPlan,
        action: (LinkedShoppingListState) -> ShoppingListLinkAction,
    ): ShoppingListsSyncResult {
        val stores = storeDao.getAllStoresOnce()
        val categories = categoryDao.getAllCategoriesOnce()
        val products = productDao.getAllProductsOnce()

        val localStoreIdByServerId = stores.asSequence()
            .mapNotNull { s -> s.serverId?.takeIf { it.isNotBlank() }?.let { it to s.id } }
            .toMap()
        val localCategoryIdByServerId = categories.asSequence()
            .mapNotNull { c -> c.serverId?.takeIf { it.isNotBlank() }?.let { it to c.id } }
            .toMap()
        val localProductIdByServerId = products.asSequence()
            .mapNotNull { p -> p.serverId?.takeIf { it.isNotBlank() }?.let { it to p.id } }
            .toMap()
        val storeServerIdById = stores.mapNotNull { s -> s.serverId?.takeIf { it.isNotBlank() }?.let { s.id to it } }.toMap()
        val categoryServerIdById = categories.mapNotNull { c -> c.serverId?.takeIf { it.isNotBlank() }?.let { c.id to it } }.toMap()
        val productServerIdById = products.mapNotNull { p -> p.serverId?.takeIf { it.isNotBlank() }?.let { p.id to it } }.toMap()

        val now = System.currentTimeMillis()
        var pulled = 0
        var created = 0
        var updated = 0
        var skippedItems = 0

        // 1. Pull-create: server lists with no local twin.
        for (serverList in plan.pullToCreate) {
            val serverId = serverList.id ?: continue
            if (shoppingListDao.getByServerId(serverId) != null) continue
            skippedItems += pullServerList(
                url = url,
                user = user,
                pass = pass,
                serverList = serverList,
                localStoreIdByServerId = localStoreIdByServerId,
                localCategoryIdByServerId = localCategoryIdByServerId,
                localProductIdByServerId = localProductIdByServerId,
                now = now,
            )
            pulled++
        }

        // 2. Linked pairs: apply the per-list decision.
        for (link in plan.linked) {
            when (action(link)) {
                ShoppingListLinkAction.SKIP -> Unit
                ShoppingListLinkAction.PUSH_LOCAL -> {
                    val serverId = link.server.id?.takeIf { it.isNotBlank() } ?: continue
                    skippedItems += pushUpdateList(
                        url = url,
                        user = user,
                        pass = pass,
                        local = link.local,
                        serverId = serverId,
                        storeServerIdById = storeServerIdById,
                        categoryServerIdById = categoryServerIdById,
                        productServerIdById = productServerIdById,
                        remoteItems = link.serverItems,
                    )
                    advanceBase(link.local.id, serverId, link.localJson, now)
                    updated++
                }
                ShoppingListLinkAction.PULL_SERVER -> {
                    val serverId = link.server.id?.takeIf { it.isNotBlank() } ?: continue
                    pullUpdateList(
                        url = url,
                        user = user,
                        pass = pass,
                        local = link.local,
                        server = link.server,
                        serverItems = link.serverItems,
                        localStoreIdByServerId = localStoreIdByServerId,
                        localCategoryIdByServerId = localCategoryIdByServerId,
                        localProductIdByServerId = localProductIdByServerId,
                        productServerIdByLocalId = productServerIdById,
                        now = now,
                    )
                    advanceBase(link.local.id, serverId, link.serverJson, now)
                }
            }
        }

        // 3. Push-create: local lists without a serverId (deferred until at least one
        //    item can reference a synced product).
        for (local in plan.pushToCreate) {
            val outcome = pushCreateList(
                url, user, pass, local,
                storeServerIdById = storeServerIdById,
                categoryServerIdById = categoryServerIdById,
                productServerIdById = productServerIdById,
            )
            if (outcome.pushed) {
                created++
                val serverId = shoppingListDao.getShoppingListById(local.id)?.serverId
                    ?.takeIf { it.isNotBlank() }
                if (serverId != null) {
                    val base = localJsonFor(
                        local = local,
                        productServerIdByLocalId = productServerIdById,
                        storeServerIdByLocalId = storeServerIdById,
                        categoryServerIdByLocalId = categoryServerIdById,
                    )
                    advanceBase(local.id, serverId, base, now)
                }
            }
            skippedItems += outcome.skippedItems
        }

        return ShoppingListsSyncResult(
            listsOnServer = plan.linked.size + plan.pullToCreate.size,
            listsOnClient = shoppingListDao.getAllShoppingListsSynchronous().size,
            pulled = pulled,
            created = created,
            updated = updated,
            deleted = 0,
            skippedItems = skippedItems,
        )
    }

    private suspend fun drainPendingDeletes(url: String, user: String, pass: String): Int {
        var deleted = 0
        for (pending in pendingDeleteDao.getAllByEntity(PENDING_DELETE_ENTITY_SHOPPING_LIST)) {
            apiClient.deleteList(url, user, pass, pending.serverId).getOrThrow()
            pendingDeleteDao.deleteById(pending.id)
            deleted++
        }
        return deleted
    }

    private fun advanceBase(localId: Long, serverId: String, baseSnapshot: String, now: Long) {
        syncStateDao.upsert(
            SyncStateEntity(
                entityType = SyncStateEntity.TYPE_SHOPPING_LIST,
                localId = localId,
                serverId = serverId,
                baseSnapshot = baseSnapshot,
                lastSyncAt = now,
            )
        )
    }

    private fun localJsonFor(
        local: ShoppingListEntity,
        productServerIdByLocalId: Map<Long, String>,
        storeServerIdByLocalId: Map<Long, String>,
        categoryServerIdByLocalId: Map<Long, String>,
    ): String {
        val items = shoppingListDao.getItemsForListSync(local.id)
            .mapNotNull { syncableItemDigest(it, productServerIdByLocalId) }
        val categoryServerIds = shoppingListDao.getCategoriesForShoppingListSync(local.id)
            .mapNotNull { categoryServerIdByLocalId[it] }
        return SyncProjection.shoppingListLocal(
            list = local,
            storeServerId = local.storeId?.let { storeServerIdByLocalId[it] },
            categoryServerIds = categoryServerIds,
            items = items,
        )
    }

    /** @return number of items skipped because their product is unknown locally */
    private suspend fun pullServerList(
        url: String,
        user: String,
        pass: String,
        serverList: NextcloudListDto,
        localStoreIdByServerId: Map<String, Long>,
        localCategoryIdByServerId: Map<String, Long>,
        localProductIdByServerId: Map<String, Long>,
        now: Long,
    ): Int {
        val serverId = serverList.id ?: return 0
        val updatedAt = NextcloudSyncDates.parseIsoToEpochMillis(serverList.updatedAt)
            ?: now

        val newId = shoppingListDao.insertShoppingList(
            ShoppingListEntity(
                name = serverList.name,
                createDate = NextcloudSyncDates.parseIsoToEpochMillis(serverList.createDate)
                    ?: NextcloudSyncDates.parseIsoToEpochMillis(serverList.createdAt)
                    ?: updatedAt,
                purchaseDate = NextcloudSyncDates.parseIsoToEpochMillis(serverList.purchaseDate),
                storeId = serverList.storeId?.let { localStoreIdByServerId[it] },
                isFinished = serverList.isFinished,
                finalTotal = serverList.finalTotal,
                position = serverList.position,
                isRecurring = serverList.isRecurring,
                recurringPeriod = serverList.recurringPeriod,
                isForwardEmpty = serverList.isForwardEmpty,
                isSubscription = serverList.isSubscription,
                isIncome = serverList.isIncome,
                lastModifiedAt = updatedAt,
                serverId = serverId,
            )
        )

        val categoryIds = serverList.categoryIds.mapNotNull { localCategoryIdByServerId[it] }
        for (categoryId in categoryIds) {
            shoppingListDao.insertShoppingListCategoryCrossRef(
                ShoppingListCategoryCrossRef(shoppingListId = newId, categoryId = categoryId)
            )
        }

        val serverItems = apiClient.fetchListItems(url, user, pass, serverId).getOrThrow()

        var skipped = 0
        for (serverItem in serverItems) {
            val localProductId = serverItem.productId?.let { localProductIdByServerId[it] }
            if (localProductId == null) {
                skipped++
                continue
            }
            shoppingListDao.insertShoppingListItem(
                ShoppingListItemEntity(
                    shoppingListId = newId,
                    productId = localProductId,
                    quantity = serverItem.quantity,
                    isChecked = serverItem.isChecked,
                    position = serverItem.position,
                    price = serverItem.price,
                    discount = serverItem.discount,
                    customName = serverItem.customName?.takeIf { it.isNotBlank() },
                )
            )
        }

        // Arm change detection on the freshly pulled list.
        val base = SyncProjection.shoppingListServer(
            serverList,
            serverItems.mapNotNull { serverItemDigest(it) }
        )
        advanceBase(newId, serverId, base, now)

        return skipped
    }

    /**
     * Outcome of attempting to create a server list. [pushed] is false when the
     * list was **deferred**: it has items but none of them can be referenced on
     * the server yet (all their products lack a `serverId`). Such a list stays
     * unlinked locally and is retried on the next run — creating it now would
     * push an empty list whose items would only reappear after a local edit.
     */
    private data class PushOutcome(
        val pushed: Boolean,
        val skippedItems: Int = 0,
    )

    private suspend fun pushCreateList(
        url: String,
        user: String,
        pass: String,
        local: ShoppingListEntity,
        storeServerIdById: Map<Long, String>,
        categoryServerIdById: Map<Long, String>,
        productServerIdById: Map<Long, String>,
    ): PushOutcome {
        val items = shoppingListDao.getItemsForListSync(local.id)
        val hasSyncableProductPending = items.any {
            it.productId > 0L && productServerIdById[it.productId] == null
        }
        val anyPushable = items.any {
            it.productId > 0L && productServerIdById[it.productId] != null && it.quantity > 0
        }
        if (hasSyncableProductPending && !anyPushable) {
            // Defer: the list is not created until at least one of its items can
            // reference a synced product (no serverId is stored, so the next run
            // picks it up again).
            return PushOutcome(pushed = false)
        }

        val created = apiClient.createList(url, user, pass, buildCreateRequest(local, storeServerIdById, categoryServerIdById))
            .getOrThrow()
        val serverId = created.id ?: throw Exception("Server did not return an id for the created list.")
        shoppingListDao.updateServerId(local.id, serverId)

        val skipped = replaceServerItems(url, user, pass, serverId, local, productServerIdById, createMissing = true)

        created.updatedAt
            ?.let { NextcloudSyncDates.parseIsoToEpochMillis(it) }
            ?.let { shoppingListDao.updateModifiedAt(local.id, it) }
        return PushOutcome(pushed = true, skippedItems = skipped)
    }

    /**
     * @return number of items skipped because their product has no serverId yet
     */
    private suspend fun pushUpdateList(
        url: String,
        user: String,
        pass: String,
        local: ShoppingListEntity,
        serverId: String,
        storeServerIdById: Map<Long, String>,
        categoryServerIdById: Map<Long, String>,
        productServerIdById: Map<Long, String>,
        remoteItems: List<NextcloudListItemDto>,
    ): Int {
        val updated = apiClient.updateList(url, user, pass, serverId, buildUpdateRequest(local, storeServerIdById, categoryServerIdById))
            .getOrThrow()

        val skipped = replaceServerItems(
            url, user, pass, serverId, local, productServerIdById,
            createMissing = false,
            existingRemoteItems = remoteItems,
        )

        updated.updatedAt
            ?.let { NextcloudSyncDates.parseIsoToEpochMillis(it) }
            ?.let { shoppingListDao.updateModifiedAt(local.id, it) }
        return skipped
    }

    /**
     * Server-authoritative pull of a linked list (SERVER_CHANGED). Overwrites the
     * shared header fields, replaces the category cross-refs with the server set, and
     * reconciles the *syncable* item set to the fetched server items. Non-syncable
     * local items (coupons / products not yet on the server) are left untouched.
     */
    private suspend fun pullUpdateList(
        url: String,
        user: String,
        pass: String,
        local: ShoppingListEntity,
        server: NextcloudListDto,
        serverItems: List<NextcloudListItemDto>,
        localStoreIdByServerId: Map<String, Long>,
        localCategoryIdByServerId: Map<String, Long>,
        localProductIdByServerId: Map<String, Long>,
        productServerIdByLocalId: Map<Long, String>,
        now: Long,
    ) {
        val updatedAt = NextcloudSyncDates.parseIsoToEpochMillis(server.updatedAt)
            ?: now

        shoppingListDao.updateShoppingList(
            local.copy(
                name = server.name,
                purchaseDate = NextcloudSyncDates.parseIsoToEpochMillis(server.purchaseDate),
                storeId = server.storeId?.let { localStoreIdByServerId[it] },
                isFinished = server.isFinished,
                finalTotal = server.finalTotal,
                position = server.position,
                isRecurring = server.isRecurring,
                recurringPeriod = server.recurringPeriod,
                isForwardEmpty = server.isForwardEmpty,
                isSubscription = server.isSubscription,
                isIncome = server.isIncome,
                lastModifiedAt = updatedAt,
            )
        )

        // Categories: full replace with the server set (unresolvable ones are dropped).
        shoppingListDao.deleteCategoriesForShoppingList(local.id)
        server.categoryIds
            .mapNotNull { localCategoryIdByServerId[it] }
            .forEach { categoryId ->
                shoppingListDao.insertShoppingListCategoryCrossRef(
                    ShoppingListCategoryCrossRef(shoppingListId = local.id, categoryId = categoryId)
                )
            }

        // Items: reconcile only the syncable set. Anything the server does not know
        // (products without a server id, coupons, non-positive quantities) survives.
        shoppingListDao.getItemsForListSync(local.id)
            .filter { it.productId > 0L && productServerIdByLocalId[it.productId] != null }
            .forEach { shoppingListDao.deleteShoppingListItemById(it.id) }

        for (serverItem in serverItems) {
            val localProductId = serverItem.productId?.let { localProductIdByServerId[it] } ?: continue
            shoppingListDao.insertShoppingListItem(
                ShoppingListItemEntity(
                    shoppingListId = local.id,
                    productId = localProductId,
                    quantity = serverItem.quantity,
                    isChecked = serverItem.isChecked,
                    position = serverItem.position,
                    price = serverItem.price,
                    discount = serverItem.discount,
                    customName = serverItem.customName?.takeIf { it.isNotBlank() },
                )
            )
        }
    }

    /**
     * Full item replace: the remote item set is discarded and recreated from
     * the local items. Local items whose product has no `serverId` cannot be
     * pushed and are skipped.
     *
     * @param existingRemoteItems the fetched remote items (from plan generation);
     *   when null (create path) there is nothing to delete.
     *
     * @return number of skipped local items
     */
    private suspend fun replaceServerItems(
        url: String,
        user: String,
        pass: String,
        serverListId: String,
        local: ShoppingListEntity,
        productServerIdById: Map<Long, String>,
        createMissing: Boolean,
        existingRemoteItems: List<NextcloudListItemDto> = emptyList(),
    ): Int {
        val remoteItems = if (createMissing) {
            emptyList()
        } else {
            existingRemoteItems.ifEmpty {
                apiClient.fetchListItems(url, user, pass, serverListId).getOrThrow()
            }
        }
        for (remoteItem in remoteItems) {
            val itemId = remoteItem.id ?: continue
            apiClient.deleteListItem(url, user, pass, serverListId, itemId).getOrThrow()
        }

        var skipped = 0
        for (item in shoppingListDao.getItemsForListSync(local.id)) {
            val serverProductId = productServerIdById[item.productId]
            // The server rejects non-positive quantities and over-long names; a
            // coupon (productId 0) has no server product either.
            if (serverProductId == null || item.quantity <= 0) {
                skipped++
                continue
            }
            apiClient.createListItem(
                url, user, pass, serverListId,
                NextcloudListItemCreateRequest(
                    productId = serverProductId,
                    price = item.price,
                    quantity = item.quantity,
                    position = item.position,
                    discount = item.discount,
                    customName = item.customName?.takeIf { it.isNotBlank() }?.take(255),
                )
            ).getOrThrow()
        }
        return skipped
    }

    private fun buildCreateRequest(
        local: ShoppingListEntity,
        storeServerIdById: Map<Long, String>,
        categoryServerIdById: Map<Long, String>,
    ): NextcloudListCreateRequest {
        return NextcloudListCreateRequest(
            name = local.name,
            storeId = local.storeId?.let { storeServerIdById[it] },
            categoryIds = categoryIdsForPush(local.id, categoryServerIdById),
            position = local.position,
            purchaseDate = NextcloudSyncDates.formatEpochToIso(local.purchaseDate),
            isFinished = local.isFinished,
            finalTotal = local.finalTotal,
            createDate = NextcloudSyncDates.formatEpochToIso(local.createDate),
            isRecurring = local.isRecurring,
            recurringPeriod = local.recurringPeriod,
            isForwardEmpty = local.isForwardEmpty,
            isSubscription = local.isSubscription,
            isIncome = local.isIncome,
        )
    }

    private fun buildUpdateRequest(
        local: ShoppingListEntity,
        storeServerIdById: Map<Long, String>,
        categoryServerIdById: Map<Long, String>,
    ): NextcloudListUpdateRequest {
        return NextcloudListUpdateRequest(
            name = local.name,
            storeId = local.storeId?.let { storeServerIdById[it] },
            categoryIds = categoryIdsForPush(local.id, categoryServerIdById),
            position = local.position,
            purchaseDate = NextcloudSyncDates.formatEpochToIso(local.purchaseDate),
            finalTotal = local.finalTotal,
            isFinished = local.isFinished,
            isRecurring = local.isRecurring,
            recurringPeriod = local.recurringPeriod,
            isForwardEmpty = local.isForwardEmpty,
            isSubscription = local.isSubscription,
            isIncome = local.isIncome,
        )
    }

    private fun categoryIdsForPush(
        localListId: Long,
        categoryServerIdById: Map<Long, String>,
    ): List<String> {
        return shoppingListDao.getCategoriesForShoppingListSync(localListId)
            .mapNotNull { categoryServerIdById[it] }
    }

    /**
     * Canonical digest of a local item, or null when it cannot be represented on the
     * server yet (its product has no `serverId`, it is a coupon with `productId == 0`,
     * or its quantity is not positive).
     */
    private fun syncableItemDigest(
        item: ShoppingListItemEntity,
        productServerIdByLocalId: Map<Long, String>,
    ): ShoppingListItemDigest? {
        val serverProductId = productServerIdByLocalId[item.productId] ?: return null
        if (item.quantity <= 0) return null
        return SyncProjection.shoppingListItemDigest(
            productServerId = serverProductId,
            quantity = item.quantity,
            price = item.price,
            discount = item.discount,
            customName = item.customName,
            position = item.position
        )
    }

    private fun serverItemDigest(item: NextcloudListItemDto): ShoppingListItemDigest? {
        val serverProductId = item.productId?.takeIf { it.isNotBlank() } ?: return null
        if (item.quantity <= 0) return null
        return SyncProjection.shoppingListItemDigest(
            productServerId = serverProductId,
            quantity = item.quantity,
            price = item.price,
            discount = item.discount,
            customName = item.customName,
            position = item.position
        )
    }

    private companion object {
        const val MAX_CONCURRENT_LIST_ITEM_FETCHES = 4
    }
}
