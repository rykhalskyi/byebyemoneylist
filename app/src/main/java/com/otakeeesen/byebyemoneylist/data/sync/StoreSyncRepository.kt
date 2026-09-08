package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import com.otakeeesen.byebyemoneylist.data.local.dao.CategoryDao
import com.otakeeesen.byebyemoneylist.data.local.dao.StoreDao
import com.otakeeesen.byebyemoneylist.data.local.dao.SyncPendingDeleteDao
import com.otakeeesen.byebyemoneylist.data.local.entity.PENDING_DELETE_ENTITY_STORE
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreCategoryCrossRef
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncConflict
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncContentState
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncPlan
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Git-style sync for stores. The shared (synced) store domain is
 * `name + address + category links`; `logoPath` / `receiptName` stay local-only.
 * Category links are expressed as server category ids on the wire and resolved to
 * local category ids (via the category `serverId` map, populated by the category
 * sync that always runs first) when writing the local cross-refs.
 */
class StoreSyncRepository(
    private val storeDao: StoreDao,
    private val syncStateDao: SyncStateDao,
    private val categoryDao: CategoryDao,
    private val preferencesManager: PreferencesManager,
    private val pendingDeleteDao: SyncPendingDeleteDao? = null,
    private val apiClient: NextcloudApiClient = NextcloudApiClient(),
    private val matcher: StoreSyncMatcher = StoreSyncMatcher()
) : SyncRepository<StoreEntity, NextcloudStoreDto> {

    private fun categoryServerIdByLocalId(): Map<Long, String> =
        categoryDao.getAllCategoriesOnce()
            .mapNotNull { cat -> cat.serverId?.takeIf { it.isNotBlank() }?.let { cat.id to it } }
            .toMap()

    private fun localCategoryIdByServerId(): Map<String, Long> =
        categoryDao.getAllCategoriesOnce()
            .mapNotNull { cat -> cat.serverId?.takeIf { it.isNotBlank() }?.let { it to cat.id } }
            .toMap()

    private fun storeCategoryIdsByStoreId(): Map<Long, List<Long>> =
        storeDao.getAllStoreCategoryCrossRefsOnce()
            .groupBy({ it.storeId }, { it.categoryId })

    /** Server ids of a local store's linked categories; unresolvable ones are omitted. */
    private fun categoryServerIdsFor(
        storeId: Long,
        localCategoryIdsByStoreId: Map<Long, List<Long>>,
        categoryServerIdByLocalId: Map<Long, String>
    ): List<String> = localCategoryIdsByStoreId[storeId].orEmpty()
        .mapNotNull { categoryServerIdByLocalId[it] }

    override suspend fun generateSyncPlan(
        useLlm: Boolean,
        llmCall: (suspend (prompt: String) -> String?)?,
        onPhase: (SyncPhase) -> Unit
    ): Result<SyncPlan<StoreEntity, NextcloudStoreDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = preferencesManager.getNextcloudUrl()
            val user = preferencesManager.getNextcloudUsername()
            val pass = preferencesManager.getNextcloudPassword()

            if (url.isBlank() || user.isBlank() || pass.isBlank()) {
                throw Exception("Nextcloud credentials are not fully configured in settings.")
            }

            onPhase(SyncPhase.FETCHING)
            val serverStores = apiClient.fetchStores(url, user, pass).getOrThrow()
            val localStores = storeDao.getAllStoresOnce()
            val plan = matcher.buildPlan(localStores, serverStores)

            val categoryServerIdByLocalId = categoryServerIdByLocalId()
            val localCategoryIdsByStoreId = storeCategoryIdsByStoreId()

            val existingStates = syncStateDao.getAll(SyncStateEntity.TYPE_STORE)
                .associateBy { it.localId }
            val annotation = SyncStateResolver.annotate(
                matched = plan.matched,
                existingByLocalId = existingStates,
                entityType = SyncStateEntity.TYPE_STORE,
                localId = { it.id },
                serverId = { it.id },
                toLocalJson = { store ->
                    SyncProjection.storeLocal(
                        store = store,
                        categoryServerIds = categoryServerIdsFor(
                            store.id,
                            localCategoryIdsByStoreId,
                            categoryServerIdByLocalId
                        )
                    )
                },
                toServerJson = { SyncProjection.storeServer(it) }
            )
            plan.copy(
                matched = annotation.matches,
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
        plan: SyncPlan<StoreEntity, NextcloudStoreDto>,
        pushItems: List<StoreEntity>,
        pullItems: List<NextcloudStoreDto>,
        linkedPairs: List<Pair<StoreEntity, NextcloudStoreDto>>,
        updateToServer: List<StoreEntity>,
        updateToLocal: List<NextcloudStoreDto>
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val url = preferencesManager.getNextcloudUrl()
            val user = preferencesManager.getNextcloudUsername()
            val pass = preferencesManager.getNextcloudPassword()
            val now = System.currentTimeMillis()

            // 0. Drain pending deletes for stores
            if (pendingDeleteDao != null) {
                for (pending in pendingDeleteDao.getAllByEntity(PENDING_DELETE_ENTITY_STORE)) {
                    apiClient.deleteStore(url, user, pass, pending.serverId).getOrThrow()
                    pendingDeleteDao.deleteById(pending.id)
                }
            }

            val categoryServerIdByLocalId = categoryServerIdByLocalId()
            val localCategoryIdByServerId = localCategoryIdByServerId()
            val localCategoryIdsByStoreId = storeCategoryIdsByStoreId()

            // 1. Persist the matched server ids locally (matched by name or manually) and baseline sync state.
            for ((local, server) in linkedPairs) {
                if (server.id != null) {
                    storeDao.updateServerId(local.id, server.id)
                    syncStateDao.upsert(
                        SyncStateEntity(
                            entityType = SyncStateEntity.TYPE_STORE,
                            localId = local.id,
                            serverId = server.id,
                            baseSnapshot = SyncProjection.storeLocal(
                                store = local,
                                categoryServerIds = categoryServerIdsFor(
                                    local.id,
                                    localCategoryIdsByStoreId,
                                    categoryServerIdByLocalId
                                )
                            ),
                            lastSyncAt = now
                        )
                    )
                }
            }

            // 2. Download missing stores from Server -> Client DB. Local ids are generated
            //    without collisions; the server id is stored so future syncs re-link them.
            //    Shared fields (name / address / categories) are written; logoPath and
            //    receiptName stay null on freshly pulled stores.
            val allLocal = storeDao.getAllStoresOnce()
            val localIds = allLocal.map { it.id }.toMutableSet()
            var nextId = (localIds.maxOrNull() ?: 0L) + 1

            for (serverStore in pullItems) {
                val serverId = serverStore.id ?: continue
                if (storeDao.getByServerId(serverId) != null) continue
                while (nextId in localIds) nextId++
                localIds.add(nextId)
                val newId = nextId
                storeDao.insertStore(
                    StoreEntity(
                        id = newId,
                        name = serverStore.name,
                        address = normalizeAddress(serverStore.address),
                        logoPath = null,
                        serverId = serverId
                    )
                )
                writeStoreCategories(newId, serverStore.categoryIds, localCategoryIdByServerId)
            }

            // 3. Upload missing stores from Client -> Server. The server has no batch
            //    endpoint, so stores are created one by one; each returned id is stored
            //    locally so future syncs re-link them.
            for (local in pushItems) {
                val created = apiClient.createStore(
                    url, user, pass,
                    buildStoreSyncRequest(
                        local = local,
                        categoryServerIds = categoryServerIdsFor(
                            local.id,
                            localCategoryIdsByStoreId,
                            categoryServerIdByLocalId
                        )
                    )
                ).getOrThrow()
                created.id?.let { storeDao.updateServerId(local.id, it) }
            }

            // 4. Push pending local edits (name / address / categories) as PUTs.
            for (local in updateToServer) {
                val serverId = local.serverId?.takeIf { it.isNotBlank() } ?: continue
                val categoryServerIds = categoryServerIdsFor(
                    local.id,
                    localCategoryIdsByStoreId,
                    categoryServerIdByLocalId
                )
                apiClient.updateStore(
                    url, user, pass, serverId,
                    buildStoreSyncRequest(local = local, categoryServerIds = categoryServerIds)
                ).getOrThrow()
                syncStateDao.upsert(
                    SyncStateEntity(
                        entityType = SyncStateEntity.TYPE_STORE,
                        localId = local.id,
                        serverId = serverId,
                        baseSnapshot = SyncProjection.storeLocal(local, categoryServerIds),
                        lastSyncAt = now
                    )
                )
            }

            // 5. Pull pending remote edits: overwrite name / address and replace the
            //    category cross-refs with the server set. Local-only fields
            //    (logoPath / receiptName) must never be clobbered by a pull.
            for (dto in updateToLocal) {
                val serverId = dto.id ?: continue
                val localStore = storeDao.getByServerId(serverId) ?: continue
                storeDao.updateSharedFromServer(
                    id = localStore.id,
                    name = dto.name,
                    address = normalizeAddress(dto.address)
                )
                storeDao.deleteCategoriesForStore(localStore.id)
                writeStoreCategories(localStore.id, dto.categoryIds, localCategoryIdByServerId)
                syncStateDao.upsert(
                    SyncStateEntity(
                        entityType = SyncStateEntity.TYPE_STORE,
                        localId = localStore.id,
                        serverId = serverId,
                        baseSnapshot = SyncProjection.storeServer(dto),
                        lastSyncAt = now
                    )
                )
            }

            true
        }
    }

    private fun writeStoreCategories(
        storeId: Long,
        categoryServerIds: List<String>,
        localCategoryIdByServerId: Map<String, Long>
    ) {
        categoryServerIds
            .mapNotNull { localCategoryIdByServerId[it] }
            .distinct()
            .forEach { categoryId ->
                storeDao.insertStoreCategoryCrossRef(StoreCategoryCrossRef(storeId = storeId, categoryId = categoryId))
            }
    }
}

private fun normalizeAddress(address: String?): String? =
    address?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Builds the full store payload shared by create (POST) and update (PUT) pushes.
 * A blank local address is dropped so it clears the server field; category links are
 * sent as server category ids.
 */
internal fun buildStoreSyncRequest(
    local: StoreEntity,
    categoryServerIds: List<String>
): NextcloudStoreCreateRequest = NextcloudStoreCreateRequest(
    name = local.name,
    address = normalizeAddress(local.address),
    categoryIds = categoryServerIds
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()
)
