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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Aggregated outcome of one shopping-list mirror sync. */
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

/**
 * Mirrors the client's shopping lists to the Nextcloud app server.
 *
 * Unlike the match-based groups (categories/stores/products), lists have **no
 * match routine** — a list is linked to its server twin purely by
 * [ShoppingListEntity.serverId]. This repository implements the mirror:
 *
 * ```
 * server lists ──fetch──►  map by id
 * local lists  ──read───►  map by serverId (null = new)
 *   ├─ server id ∉ local serverIds          → PULL: create local list + items
 *   ├─ local serverId == null               → PUSH: create server list + items, store serverId
 *   ├─ local serverId != null (dirty)       → PUSH: update server list (client wins), replace items
 *   └─ pending deletes (queued serverIds)   → DELETE on server, clear queue
 * ```
 *
 * **Item mirroring = full replace.** Items have no independent identity on the
 * server, so on push the remote item set is replaced with the local item set
 * (delete all + recreate). Local items whose referenced product has no
 * `serverId` yet cannot reference a not-yet-synced entity — they are **skipped
 * and counted** in [ShoppingListsSyncResult.skippedItems].
 *
 * Change detection reuses the client's `lastModifiedAt`: after every pull/push
 * it is anchored to the server's `updated_at`, so a linked list is only pushed
 * when a local edit happened after the last server contact.
 */
class ShoppingListsSyncRepository(
    private val shoppingListDao: ShoppingListDao,
    private val storeDao: StoreDao,
    private val categoryDao: CategoryDao,
    private val productDao: ProductDao,
    private val pendingDeleteDao: SyncPendingDeleteDao,
    private val preferencesManager: PreferencesManager,
    private val apiClient: NextcloudApiClient = NextcloudApiClient(),
) {
    suspend fun sync(): Result<ShoppingListsSyncResult> = withContext(Dispatchers.IO) {
        runCatching {
            val url = preferencesManager.getNextcloudUrl()
            val user = preferencesManager.getNextcloudUsername()
            val pass = preferencesManager.getNextcloudPassword()

            if (url.isBlank() || user.isBlank() || pass.isBlank()) {
                throw Exception("Nextcloud credentials are not fully configured in settings.")
            }

            val serverLists = apiClient.fetchLists(url, user, pass).getOrThrow()
            val serverByServerId = serverLists.mapNotNull { it.id?.let { id -> id to it } }.toMap()

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

            var pulled = 0
            var created = 0
            var updated = 0
            var deleted = 0
            var skippedItems = 0

            // 1. Drain the pending-delete queue (deleted locally before a sync).
            for (pending in pendingDeleteDao.getAllByEntity(PENDING_DELETE_ENTITY_SHOPPING_LIST)) {
                apiClient.deleteList(url, user, pass, pending.serverId).getOrThrow()
                pendingDeleteDao.deleteById(pending.id)
                deleted++
            }

            // 2. PULL: server lists with no local twin.
            val localLists = shoppingListDao.getAllShoppingListsSynchronous()
            val knownServerIds = localLists.mapNotNull { it.serverId?.takeIf { id -> id.isNotBlank() } }.toSet()

            for (serverList in serverLists) {
                val serverId = serverList.id ?: continue
                if (serverId in knownServerIds) continue
                skippedItems += pullServerList(
                    url, user, pass, serverList,
                    localStoreIdByServerId = localStoreIdByServerId,
                    localCategoryIdByServerId = localCategoryIdByServerId,
                    localProductIdByServerId = localProductIdByServerId,
                )
                pulled++
            }

            // 3. PUSH: local lists that are new, or linked and dirty (client wins).
            val afterPull = shoppingListDao.getAllShoppingListsSynchronous()
            for (local in afterPull) {
                val serverId = local.serverId?.takeIf { it.isNotBlank() }
                if (serverId == null) {
                    skippedItems += pushCreateList(
                        url, user, pass, local,
                        storeServerIdById = storeServerIdById,
                        categoryServerIdById = categoryServerIdById,
                        productServerIdById = productServerIdById,
                    )
                    created++
                } else {
                    val serverUpdatedAt = serverByServerId[serverId]
                        ?.updatedAt
                        ?.let { NextcloudSyncDates.parseIsoToEpochMillis(it) }
                        ?: 0L
                    if (shouldPushLinkedList(local.lastModifiedAt, serverUpdatedAt)) {
                        skippedItems += pushUpdateList(
                            url, user, pass, local, serverId,
                            storeServerIdById = storeServerIdById,
                            categoryServerIdById = categoryServerIdById,
                            productServerIdById = productServerIdById,
                        )
                        updated++
                    }
                }
            }

            ShoppingListsSyncResult(
                listsOnServer = serverLists.size,
                listsOnClient = afterPull.size,
                pulled = pulled,
                created = created,
                updated = updated,
                deleted = deleted,
                skippedItems = skippedItems,
            )
        }
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
    ): Int {
        val serverId = serverList.id ?: return 0
        val updatedAt = NextcloudSyncDates.parseIsoToEpochMillis(serverList.updatedAt)
            ?: System.currentTimeMillis()

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

        var skipped = 0
        for (serverItem in apiClient.fetchListItems(url, user, pass, serverId).getOrThrow()) {
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
        return skipped
    }

    /** @return number of items skipped because their product has no serverId yet */
    private suspend fun pushCreateList(
        url: String,
        user: String,
        pass: String,
        local: ShoppingListEntity,
        storeServerIdById: Map<Long, String>,
        categoryServerIdById: Map<Long, String>,
        productServerIdById: Map<Long, String>,
    ): Int {
        val created = apiClient.createList(url, user, pass, buildCreateRequest(local, storeServerIdById, categoryServerIdById))
            .getOrThrow()
        val serverId = created.id ?: throw Exception("Server did not return an id for the created list.")
        shoppingListDao.updateServerId(local.id, serverId)

        val skipped = replaceServerItems(url, user, pass, serverId, local, productServerIdById, createMissing = true)

        created.updatedAt
            ?.let { NextcloudSyncDates.parseIsoToEpochMillis(it) }
            ?.let { shoppingListDao.updateModifiedAt(local.id, it) }
        return skipped
    }

    /** @return number of items skipped because their product has no serverId yet */
    private suspend fun pushUpdateList(
        url: String,
        user: String,
        pass: String,
        local: ShoppingListEntity,
        serverId: String,
        storeServerIdById: Map<Long, String>,
        categoryServerIdById: Map<Long, String>,
        productServerIdById: Map<Long, String>,
    ): Int {
        val updated = apiClient.updateList(url, user, pass, serverId, buildUpdateRequest(local, storeServerIdById, categoryServerIdById))
            .getOrThrow()

        val skipped = replaceServerItems(url, user, pass, serverId, local, productServerIdById, createMissing = false)

        updated.updatedAt
            ?.let { NextcloudSyncDates.parseIsoToEpochMillis(it) }
            ?.let { shoppingListDao.updateModifiedAt(local.id, it) }
        return skipped
    }

    /**
     * Full item replace: the remote item set is discarded and recreated from
     * the local items. Local items whose product has no `serverId` cannot be
     * pushed and are skipped.
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
    ): Int {
        val remoteItems = if (createMissing) {
            emptyList()
        } else {
            apiClient.fetchListItems(url, user, pass, serverListId).getOrThrow()
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
}
