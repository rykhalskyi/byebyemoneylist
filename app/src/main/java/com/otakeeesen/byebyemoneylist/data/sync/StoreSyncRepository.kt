package com.otakeeesen.byebyemoneylist.data.sync

import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import com.otakeeesen.byebyemoneylist.data.local.dao.StoreDao
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StoreSyncRepository(
    private val storeDao: StoreDao,
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
            matcher.buildPlan(localStores, serverStores)
        }
    }

    override suspend fun executeSyncPlan(
        plan: SyncPlan<StoreEntity, NextcloudStoreDto>,
        pushItems: List<StoreEntity>,
        pullItems: List<NextcloudStoreDto>,
        linkedPairs: List<Pair<StoreEntity, NextcloudStoreDto>>
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

            true
        }
    }
}
