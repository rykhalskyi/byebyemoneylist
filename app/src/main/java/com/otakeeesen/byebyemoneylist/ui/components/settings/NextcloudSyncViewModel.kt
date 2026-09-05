package com.otakeeesen.byebyemoneylist.ui.components.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.data.agent.AgentManager
import com.otakeeesen.byebyemoneylist.data.agent.AgentQueryExecutor
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.ProductEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.data.sync.CategorySyncRepository
import com.otakeeesen.byebyemoneylist.data.sync.MultiLanguageCategoryMatcher
import com.otakeeesen.byebyemoneylist.data.sync.NextcloudCategoryDto
import com.otakeeesen.byebyemoneylist.data.sync.NextcloudProductDto
import com.otakeeesen.byebyemoneylist.data.sync.NextcloudStoreDto
import com.otakeeesen.byebyemoneylist.data.sync.ProductSyncRepository
import com.otakeeesen.byebyemoneylist.data.sync.ShoppingListsSyncRepository
import com.otakeeesen.byebyemoneylist.data.sync.ShoppingListsSyncResult
import com.otakeeesen.byebyemoneylist.data.sync.SyncCoordinator
import com.otakeeesen.byebyemoneylist.data.sync.SyncPhase
import com.otakeeesen.byebyemoneylist.data.sync.StoreSyncRepository
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncCandidate
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncGroupCounts
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncMatch
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Editor state for one sync group (Categories / Stores / Products). Matched pairs are the
 * linked items; upload/download are the unmatched pools, editable and selectable.
 */
data class SyncGroupEditorState<Local, Server>(
    val planGenerated: Boolean = false,
    val matched: List<SyncMatch<Local, Server>> = emptyList(),
    val upload: List<SyncCandidate<Local>> = emptyList(),
    val download: List<SyncCandidate<Server>> = emptyList()
) {
    fun counts(): SyncGroupCounts = SyncGroupCounts(
        matched = matched.size,
        upload = upload.count { it.selected },
        download = download.count { it.selected }
    )
}

/**
 * Status of the Shopping Lists mirror group. Lists are linked purely by
 * `serverId` (no match routine), so this group has no editor state — only the
 * outcome of the last mirror run, shown as a count + status on the settings
 * row.
 */
data class ShoppingListsSyncUiState(
    val hasSynced: Boolean = false,
    val listCount: Int = 0,
    val skipped: Int = 0,
    val error: String? = null,
) {
    val syncedSuccessfully: Boolean get() = hasSynced && error == null
}

data class NextcloudSyncUiState(
    val llmAvailable: Boolean = false,
    val useLlm: Boolean = false,
    val isGenerating: Boolean = false,
    val isExecuting: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    val categories: SyncGroupEditorState<CategoryEntity, NextcloudCategoryDto> = SyncGroupEditorState(),
    val stores: SyncGroupEditorState<StoreEntity, NextcloudStoreDto> = SyncGroupEditorState(),
    val products: SyncGroupEditorState<ProductEntity, NextcloudProductDto> = SyncGroupEditorState(),
    val shoppingLists: ShoppingListsSyncUiState = ShoppingListsSyncUiState()
)

class NextcloudSyncViewModel(
    private val app: ByeByeMoneyApplication
) : ViewModel() {

    private val preferencesManager = app.preferencesManager
    private val categoryRepository = CategorySyncRepository(
        categoryDao = app.database.categoryDao(),
        preferencesManager = preferencesManager
    )
    private val storeRepository = StoreSyncRepository(
        storeDao = app.database.storeDao(),
        preferencesManager = preferencesManager
    )
    private val productRepository = ProductSyncRepository(
        productDao = app.database.productDao(),
        productAliasDao = app.database.productAliasDao(),
        categoryDao = app.database.categoryDao(),
        preferencesManager = preferencesManager
    )
    private val shoppingListsRepository = ShoppingListsSyncRepository(
        shoppingListDao = app.database.shoppingListDao(),
        storeDao = app.database.storeDao(),
        categoryDao = app.database.categoryDao(),
        productDao = app.database.productDao(),
        pendingDeleteDao = app.database.syncPendingDeleteDao(),
        preferencesManager = preferencesManager
    )
    private val agentManager: AgentManager by lazy {
        val executor = AgentQueryExecutor(
            app.shoppingListRepository,
            app.categoryRepository,
            app.productRepository,
            app.priceRepository,
            app.storeRepository,
            preferencesManager
        )
        AgentManager(preferencesManager, executor)
    }

    private val _uiState = MutableStateFlow(NextcloudSyncUiState())
    val uiState: StateFlow<NextcloudSyncUiState> = _uiState.asStateFlow()

    init {
        _uiState.update {
            it.copy(
                llmAvailable = preferencesManager.getActiveProfileId() != null,
                useLlm = preferencesManager.getActiveProfileId() != null
            )
        }
    }

    fun setUseLlm(useLlm: Boolean) {
        _uiState.update { it.copy(useLlm = useLlm) }
    }

    fun syncNow(onError: ((String) -> Unit)? = null) {
        if (_uiState.value.isGenerating) return
        _uiState.update { it.copy(isGenerating = true, error = null, success = false) }
        viewModelScope.launch {
            val state = _uiState.value
            val useLlm = state.useLlm && state.llmAvailable
            val llmCall: (suspend (String) -> String?)? =
                if (useLlm) {
                    { prompt ->
                        agentManager.generateText(MultiLanguageCategoryMatcher.LLM_SYSTEM_INSTRUCTION, prompt)
                    }
                } else {
                    null
                }

            var anyFailed = false
            var firstError: String? = null

            categoryRepository.generateSyncPlan(
                useLlm = useLlm,
                llmCall = llmCall,
                onPhase = { phase ->
                    if (phase == SyncPhase.LLM_MATCHING) {
                        _uiState.update { it.copy(isGenerating = true) }
                    }
                }
            ).onSuccess { plan ->
                _uiState.update { it.copy(categories = editorFromPlan(plan)) }
            }.onFailure { e ->
                anyFailed = true
                firstError = firstError ?: (e.localizedMessage ?: "Failed to generate sync plan")
            }

            storeRepository.generateSyncPlan(
                useLlm = false,
                llmCall = null,
                onPhase = {}
            ).onSuccess { plan ->
                _uiState.update { it.copy(stores = editorFromPlan(plan)) }
            }.onFailure { e ->
                anyFailed = true
                firstError = firstError ?: (e.localizedMessage ?: "Failed to generate sync plan")
            }

            productRepository.generateSyncPlan(
                useLlm = false,
                llmCall = null,
                onPhase = {}
            ).onSuccess { plan ->
                _uiState.update { it.copy(products = editorFromPlan(plan)) }
            }.onFailure { e ->
                anyFailed = true
                firstError = firstError ?: (e.localizedMessage ?: "Failed to generate sync plan")
            }

            // The Shopping Lists group is a live mirror — there is no plan or
            // user selection to confirm, so it syncs immediately on "Sync Now"
            // (and again inside "Confirm and sync", which runs it after the
            // match-based groups have populated the server ids it references).
            val shoppingListsState = shoppingListUiStateFrom(shoppingListsRepository.sync())

            _uiState.update { it.copy(isGenerating = false, shoppingLists = shoppingListsState) }
            if (anyFailed) {
                _uiState.update { it.copy(error = firstError) }
                onError?.invoke(firstError ?: "")
            } else {
                shoppingListsState.error?.let {
                    _uiState.update { state -> state.copy(error = it) }
                    onError?.invoke(it)
                }
            }
        }
    }

    private fun shoppingListUiStateFrom(result: Result<ShoppingListsSyncResult>): ShoppingListsSyncUiState =
        result.fold(
            onSuccess = { r ->
                ShoppingListsSyncUiState(
                    hasSynced = true,
                    listCount = r.listsOnClient,
                    skipped = r.skippedItems
                )
            },
            onFailure = { e ->
                ShoppingListsSyncUiState(
                    hasSynced = true,
                    error = e.localizedMessage ?: "Shopping lists sync failed"
                )
            }
        )

    private fun <Local, Server> editorFromPlan(
        plan: SyncPlan<Local, Server>
    ): SyncGroupEditorState<Local, Server> = SyncGroupEditorState(
        planGenerated = true,
        matched = plan.matched,
        upload = plan.toPushToServer.map { SyncCandidate(item = it, selected = true) },
        download = plan.toPullToClient.map { SyncCandidate(item = it, selected = true) }
    )

    // ---- Category editor operations -------------------------------------------------

    fun toggleUpload(local: CategoryEntity) {
        _uiState.update { state ->
            state.copy(categories = toggleInUpload(state.categories, local))
        }
    }

    fun toggleDownload(server: NextcloudCategoryDto) {
        _uiState.update { state ->
            state.copy(categories = toggleInDownload(state.categories, server))
        }
    }

    fun selectAllUpload(select: Boolean) {
        _uiState.update { state ->
            state.copy(categories = selectAllInUpload(state.categories, select))
        }
    }

    fun selectAllDownload(select: Boolean) {
        _uiState.update { state ->
            state.copy(categories = selectAllInDownload(state.categories, select))
        }
    }

    fun unlinkMatch(match: SyncMatch<CategoryEntity, NextcloudCategoryDto>) {
        _uiState.update { state ->
            state.copy(categories = unlink(state.categories, match))
        }
    }

    fun createMatch(local: CategoryEntity, server: NextcloudCategoryDto) {
        _uiState.update { state ->
            state.copy(categories = createMatchIn(state.categories, local, server))
        }
    }

    // ---- Store editor operations ----------------------------------------------------

    fun toggleUploadStore(local: StoreEntity) {
        _uiState.update { state ->
            state.copy(stores = toggleInUpload(state.stores, local))
        }
    }

    fun toggleDownloadStore(server: NextcloudStoreDto) {
        _uiState.update { state ->
            state.copy(stores = toggleInDownload(state.stores, server))
        }
    }

    fun selectAllUploadStore(select: Boolean) {
        _uiState.update { state ->
            state.copy(stores = selectAllInUpload(state.stores, select))
        }
    }

    fun selectAllDownloadStore(select: Boolean) {
        _uiState.update { state ->
            state.copy(stores = selectAllInDownload(state.stores, select))
        }
    }

    fun unlinkStoreMatch(match: SyncMatch<StoreEntity, NextcloudStoreDto>) {
        _uiState.update { state ->
            state.copy(stores = unlink(state.stores, match))
        }
    }

    fun createStoreMatch(local: StoreEntity, server: NextcloudStoreDto) {
        _uiState.update { state ->
            state.copy(stores = createMatchIn(state.stores, local, server))
        }
    }

    // ---- Product editor operations --------------------------------------------------

    fun toggleUploadProduct(local: ProductEntity) {
        _uiState.update { state ->
            state.copy(products = toggleInUpload(state.products, local))
        }
    }

    fun toggleDownloadProduct(server: NextcloudProductDto) {
        _uiState.update { state ->
            state.copy(products = toggleInDownload(state.products, server))
        }
    }

    fun selectAllUploadProduct(select: Boolean) {
        _uiState.update { state ->
            state.copy(products = selectAllInUpload(state.products, select))
        }
    }

    fun selectAllDownloadProduct(select: Boolean) {
        _uiState.update { state ->
            state.copy(products = selectAllInDownload(state.products, select))
        }
    }

    fun unlinkProductMatch(match: SyncMatch<ProductEntity, NextcloudProductDto>) {
        _uiState.update { state ->
            state.copy(products = unlink(state.products, match))
        }
    }

    fun createProductMatch(local: ProductEntity, server: NextcloudProductDto) {
        _uiState.update { state ->
            state.copy(products = createMatchIn(state.products, local, server))
        }
    }

    // ---- Generic editor operations --------------------------------------------------

    private fun <Local, Server> toggleInUpload(
        editor: SyncGroupEditorState<Local, Server>,
        item: Local
    ): SyncGroupEditorState<Local, Server> = editor.copy(
        upload = editor.upload.map {
            if (it.item == item) it.copy(selected = !it.selected) else it
        }
    )

    private fun <Local, Server> toggleInDownload(
        editor: SyncGroupEditorState<Local, Server>,
        item: Server
    ): SyncGroupEditorState<Local, Server> = editor.copy(
        download = editor.download.map {
            if (it.item == item) it.copy(selected = !it.selected) else it
        }
    )

    private fun <Local, Server> selectAllInUpload(
        editor: SyncGroupEditorState<Local, Server>,
        select: Boolean
    ): SyncGroupEditorState<Local, Server> = editor.copy(
        upload = editor.upload.map { it.copy(selected = select) }
    )

    private fun <Local, Server> selectAllInDownload(
        editor: SyncGroupEditorState<Local, Server>,
        select: Boolean
    ): SyncGroupEditorState<Local, Server> = editor.copy(
        download = editor.download.map { it.copy(selected = select) }
    )

    /**
     * Unlinks a matched pair. The local item returns to the upload pool and the server item to
     * the download pool. Unlinked items are ordinary unmatched candidates: they appear in the
     * Upload/Download sections and can be re-matched, re-uploaded or re-downloaded (creating a
     * new entry with a new id on the destination side).
     */
    private fun <Local, Server> unlink(
        editor: SyncGroupEditorState<Local, Server>,
        match: SyncMatch<Local, Server>
    ): SyncGroupEditorState<Local, Server> = editor.copy(
        matched = editor.matched.filterNot {
            it.local == match.local && it.server == match.server
        },
        upload = editor.upload + SyncCandidate(item = match.local, selected = false),
        download = editor.download + SyncCandidate(item = match.server, selected = false)
    )

    /**
     * Creates a manual match between an unmatched local and an unmatched server item.
     * Both must currently be unmatched (present in their respective pools).
     */
    private fun <Local, Server> createMatchIn(
        editor: SyncGroupEditorState<Local, Server>,
        local: Local,
        server: Server
    ): SyncGroupEditorState<Local, Server> {
        val localInPool = editor.upload.any { it.item == local }
        val serverInPool = editor.download.any { it.item == server }
        if (!localInPool || !serverInPool) return editor
        return editor.copy(
            upload = editor.upload.filterNot { it.item == local },
            download = editor.download.filterNot { it.item == server },
            matched = editor.matched + SyncMatch(local = local, server = server, reason = "Manual match")
        )
    }

    // ---- Execution -------------------------------------------------------------------

    /**
     * Executes every planned group in the required order (Categories → Stores →
     * Products) and then runs the Shopping Lists mirror, which depends on the
     * store/category/product `serverId`s those groups populate. A failure in
     * one group does not prevent the later groups from running.
     */
    fun confirmAndSync(onFinished: (Boolean) -> Unit) {
        if (_uiState.value.isExecuting) return
        _uiState.update { it.copy(isExecuting = true, error = null, success = false) }
        viewModelScope.launch {
            val state = _uiState.value

            val coordinator = SyncCoordinator(
                listOf(
                    buildExecution(state.categories, categoryRepository),
                    buildExecution(state.stores, storeRepository),
                    buildExecution(state.products, productRepository)
                )
            )
            val groupResults = coordinator.executeAll()

            // The mirror has no match routine and references server UUIDs, so
            // it runs strictly after the coordinator's three groups.
            val shoppingListResult = shoppingListsRepository.sync()
            val shoppingListsState = shoppingListUiStateFrom(shoppingListResult)

            val allResults = groupResults + shoppingListResult.map { true }
            val allOk = allResults.all { it.isSuccess }

            _uiState.update {
                it.copy(
                    isExecuting = false,
                    success = allOk,
                    error = allResults.firstNotNullOfOrNull { r -> r.exceptionOrNull()?.localizedMessage },
                    shoppingLists = shoppingListsState
                )
            }
            onFinished(allOk)
        }
    }

    private fun <Local, Server> buildExecution(
        editor: SyncGroupEditorState<Local, Server>,
        repository: com.otakeeesen.byebyemoneylist.data.sync.SyncRepository<Local, Server>
    ): suspend () -> Result<Boolean> {
        val push = editor.upload.filter { it.selected }.map { it.item }
        val pull = editor.download.filter { it.selected }.map { it.item }
        val links = editor.matched.map { it.local to it.server }
        val plan = SyncPlan(
            matched = editor.matched,
            toPushToServer = push,
            toPullToClient = pull
        )
        return {
            repository.executeSyncPlan(plan = plan, pushItems = push, pullItems = pull, linkedPairs = links)
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(error = null, success = false) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras,
            ): T {
                val application = checkNotNull(
                    extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                ) as ByeByeMoneyApplication
                return NextcloudSyncViewModel(application) as T
            }
        }
    }
}
