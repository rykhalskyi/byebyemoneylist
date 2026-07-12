package com.otakeeesen.byebyemoneylist.data.sync

import android.content.Context
import com.otakeeesen.byebyemoneylist.data.local.AppDatabase
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListItemEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SyncState { IDLE, SYNCING, ERROR, OFFLINE }

data class SyncUiState(
    val state: SyncState = SyncState.IDLE,
    val lastSyncError: String? = null,
    val newListDetected: String? = null
)

class ListSyncEngine(
    private val context: Context,
    private val syncFolderRepo: SyncFolderRepository,
    private val database: AppDatabase,
    private val prefs: PreferencesManager,
    private val productMatcher: SyncProductMatcher,
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var syncJob: Job? = null

    private val _syncState = MutableStateFlow(SyncUiState())
    val syncState: StateFlow<SyncUiState> = _syncState.asStateFlow()

    fun startSync() {
        if (syncJob != null) return
        syncJob = scope.launch {
            performFullSync()
            while (true) {
                delay(60_000L)
                performFullSync()
            }
        }
    }

    fun stopSync() {
        syncJob?.cancel()
        syncJob = null
    }

    fun dismissNewListDetection() {
        _syncState.value = _syncState.value.copy(newListDetected = null)
    }

    suspend fun pushList(listId: Long) {
        val entity = database.shoppingListDao().getShoppingListById(listId) ?: return
        val syncId = entity.syncId ?: return
        val items = database.shoppingListDao().getItemsForListSync(listId)
        val now = System.currentTimeMillis()
        val userId = prefs.getSyncUserId()
        val displayName = prefs.getSyncDisplayName()

        val dto = SyncListDto(
            syncVersion = 1,
            syncId = syncId,
            title = entity.name,
            lastModified = now,
            modifiedByUserId = userId,
            modifiedByDisplayName = displayName,
            items = items.map { item ->
                val productName = if (item.productId > 0L) {
                    database.productDao().getProductById(item.productId)?.name
                } else null
                val aliases = if (item.productId > 0L) {
                    database.productAliasDao().getAliasesByProductId(item.productId).map { it.aliasName }
                } else emptyList()
                SyncItemDto(
                    itemId = item.id.toString(),
                    name = item.customName ?: productName ?: "item_${item.id}",
                    aliases = aliases,
                    quantity = item.quantity,
                    checked = item.isChecked,
                    position = item.position,
                    lastModified = now
                )
            }
        )
        syncFolderRepo.writeFile(syncId, dto)
        database.shoppingListDao().updateSyncTimestamp(listId, now)
    }

    suspend fun performFullSync() {
        if (!syncFolderRepo.isFolderSet()) return
        _syncState.value = _syncState.value.copy(state = SyncState.SYNCING)

        try {
            val files = syncFolderRepo.listSyncFiles()
            val localShared = database.shoppingListDao().getSharedListsSync()
            val now = System.currentTimeMillis()

            for (file in files) {
                val syncId = file.name.removeSuffix(".bbl.json")
                val dto = syncFolderRepo.readFile(file.name) ?: continue
                val localEntity = database.shoppingListDao().getShoppingListBySyncId(syncId)

                if (localEntity == null) {
                    if (dto.isDeleted) {
                        syncFolderRepo.deleteFile(syncId)
                    } else {
                        createLocalListFromDto(dto, syncId)
                        _syncState.value = _syncState.value.copy(newListDetected = dto.title)
                    }
                    continue
                }

                if (dto.isDeleted) {
                    if (localEntity.lastSyncTimestamp < file.lastModified) {
                        database.shoppingListDao().markAsUnshared(localEntity.id)
                        syncFolderRepo.deleteFile(syncId)
                    }
                    continue
                }

                val folderModified = file.lastModified
                val localModified = localEntity.lastModifiedAt

                if (folderModified > localModified && folderModified > localEntity.lastSyncTimestamp) {
                    mergeIntoLocal(localEntity, dto, file.lastModified)
                } else if (localModified > folderModified && localModified > localEntity.lastSyncTimestamp) {
                    pushList(localEntity.id)
                }
            }

            for (entity in localShared) {
                val syncId = entity.syncId ?: continue
                val hasFile = files.any { it.name == "$syncId.bbl.json" }
                if (!hasFile) {
                    pushList(entity.id)
                }
            }
            _syncState.value = _syncState.value.copy(state = SyncState.IDLE, lastSyncError = null)
        } catch (e: Exception) {
            _syncState.value = _syncState.value.copy(state = SyncState.ERROR, lastSyncError = e.message)
        }
    }

    private suspend fun mergeIntoLocal(localEntity: ShoppingListEntity, dto: SyncListDto, syncTimestamp: Long) {
        val localItems = database.shoppingListDao().getItemsForListSync(localEntity.id)
        val localItemMap = localItems.associateBy { it.id.toString() }
        val storeId = localEntity.storeId
        val matchedItems = productMatcher.matchItems(
            dto.items.filter { it.itemId !in localItemMap.keys }, storeId
        )

        for (itemDto in dto.items) {
            val localItem = localItemMap[itemDto.itemId]
            if (localItem == null) {
                val matched = matchedItems.find { it.item.itemId == itemDto.itemId }
                val productId = matched?.productId ?: 0L
                database.shoppingListDao().insertShoppingListItem(
                    ShoppingListItemEntity(
                        id = 0,
                        shoppingListId = localEntity.id,
                        productId = productId,
                        quantity = itemDto.quantity,
                        isChecked = itemDto.checked,
                        position = itemDto.position,
                        customName = null
                    )
                )
            } else {
                val dtoModified = itemDto.lastModified
                if (dtoModified > localEntity.lastSyncTimestamp) {
                    database.shoppingListDao().updateShoppingListItem(
                        localItem.copy(
                            quantity = itemDto.quantity,
                            isChecked = itemDto.checked,
                            position = itemDto.position
                        )
                    )
                }
            }
        }

        if (dto.title != localEntity.name) {
            database.shoppingListDao().updateShoppingList(localEntity.copy(name = dto.title))
        }

        database.shoppingListDao().updateSyncTimestamp(localEntity.id, syncTimestamp)
        database.shoppingListDao().updateModifiedAt(localEntity.id, syncTimestamp)
    }

    private suspend fun createLocalListFromDto(dto: SyncListDto, syncId: String) {
        val now = System.currentTimeMillis()
        val maxPosition = database.shoppingListDao().getMaxListPosition()
        val entity = ShoppingListEntity(
            id = 0,
            name = dto.title,
            createDate = now,
            purchaseDate = null,
            storeId = null,
            isFinished = false,
            finalTotal = null,
            position = maxPosition + 1,
            isRecurring = false,
            recurringPeriod = "MONTH",
            isForwardEmpty = true,
            isArchived = false,
            isSubscription = false,
            isIncome = false,
            isShared = true,
            syncId = syncId,
            lastSyncTimestamp = now,
            lastModifiedAt = now
        )
        val listId = database.shoppingListDao().insertShoppingList(entity)
        val matchedItems = productMatcher.matchItems(dto.items, null)
        for (matched in matchedItems) {
            database.shoppingListDao().insertShoppingListItem(
                ShoppingListItemEntity(
                    id = 0,
                    shoppingListId = listId,
                    productId = matched.productId,
                    quantity = matched.item.quantity,
                    isChecked = matched.item.checked,
                    position = matched.item.position,
                    customName = null
                )
            )
        }
    }
}
