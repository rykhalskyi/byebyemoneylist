package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import com.otakeeesen.byebyemoneylist.data.local.dao.StoreDao
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncConflict
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncContentState
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncPlan
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncStateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StoreSyncRepository(
    private val storeDao: StoreDao,
    private val syncStateDao: SyncStateDao,
    private val preferencesManager: PreferencesManager,
    private val apiClient: NextcloudApiClient = NextcloudApiClient(),
    private val matcher: StoreSyncMatcher = StoreSyncMatcher()
) : SyncRepository<StoreEntity, NextcloudStoreDto> {

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

            val existingStates = syncStateDao.getAll(SyncStateEntity.TYPE_STORE)
                .associateBy { it.localId }
            val annotation = SyncStateResolver.annotate(
                matched = plan.matched,
                existingByLocalId = existingStates,
                entityType = SyncStateEntity.TYPE_STORE,
                localId = { it.id },
                serverId = { it.id },
                toLocalJson = { SyncProjection.storeLocal(it) },
                toServerJson = { SyncProjection.storeServer(it) }
            )
            annotation.baselines.forEach { syncStateDao.upsert(it) }

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

            // 1. Persist the matched server ids locally (matched by name or manually).
            for ((local, server) in linkedPairs) {
                server.id?.let { storeDao.updateServerId(local.id, it) }
            }

            // 2. Download missing stores from Server -> Client DB. Local ids are generated
            //    without collisions; the server id is stored so future syncs re-link them.
            val allLocal = storeDao.getAllStoresOnce()
            val localIds = allLocal.map { it.id }.toMutableSet()
            var nextId = (localIds.maxOrNull() ?: 0L) + 1

            for (serverStore in pullItems) {
                val serverId = serverStore.id ?: continue
                if (storeDao.getByServerId(serverId) != null) continue
                while (nextId in localIds) nextId++
                localIds.add(nextId)
                storeDao.insertStore(
                    StoreEntity(
                        id = nextId,
                        name = serverStore.name,
                        logoPath = null,
                        serverId = serverId
                    )
                )
            }

            // 3. Upload missing stores from Client -> Server. The server has no batch
            //    endpoint, so stores are created one by one; each returned id is stored
            //    locally so future syncs re-link them.
            for (local in pushItems) {
                val created = apiClient.createStore(url, user, pass, local.name).getOrThrow()
                created.id?.let { storeDao.updateServerId(local.id, it) }
            }

            val now = System.currentTimeMillis()

            // 4. Push pending local renames (server stores are name-only).
            for (local in updateToServer) {
                val serverId = local.serverId?.takeIf { it.isNotBlank() } ?: continue
                apiClient.updateStore(url, user, pass, serverId, local.name).getOrThrow()
                syncStateDao.upsert(
                    SyncStateEntity(
                        entityType = SyncStateEntity.TYPE_STORE,
                        localId = local.id,
                        serverId = serverId,
                        baseSnapshot = SyncProjection.storeLocal(local),
                        lastSyncAt = now
                    )
                )
            }

            // 5. Pull pending remote renames: overwrite the name only. Local-only fields
            //    (logoPath / address / receiptName) must never be clobbered by a pull.
            for (dto in updateToLocal) {
                val serverId = dto.id ?: continue
                val localStore = storeDao.getByServerId(serverId) ?: continue
                storeDao.updateNameFromServer(localStore.id, dto.name)
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
}
