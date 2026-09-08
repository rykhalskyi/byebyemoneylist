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
import com.otakeeesen.byebyemoneylist.data.sync.ShoppingListLinkAction
import com.otakeeesen.byebyemoneylist.data.sync.ShoppingListSyncPlan
import com.otakeeesen.byebyemoneylist.data.sync.ShoppingListsSyncRepository
import com.otakeeesen.byebyemoneylist.data.sync.StoreSyncRepository
import com.otakeeesen.byebyemoneylist.data.sync.defaultShoppingListAction
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncCandidate
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncConflict
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncContentState
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncGroupCounts
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncMatch
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncMatchCandidate
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncPlan
import com.otakeeesen.byebyemoneylist.data.sync.model.pickedLocal
import com.otakeeesen.byebyemoneylist.data.sync.model.pickedServer
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One of the independently syncable data groups shown on the sync hub. Each group has its
 * own plan-generation and execution busy flag, so a row's spinner stops as soon as *its*
 * group is done and any group can be refreshed / applied on its own.
 */
enum class SyncGroup { CATEGORIES, STORES, PRODUCTS, SHOPPING_LISTS }

/**
 * Editor state for one sync group (Categories / Stores / Products). Matched pairs are the
 * linked items in sync or changed on one side; [conflicts] are pairs changed on both sides
 * awaiting "Use local" / "Use server"; upload/download are the unmatched pools.
 */
data class SyncGroupEditorState<Local, Server>(
    val planGenerated: Boolean = false,
    val matched: List<SyncMatchCandidate<Local, Server>> = emptyList(),
    val conflicts: List<SyncConflict<Local, Server>> = emptyList(),
    val upload: List<SyncCandidate<Local>> = emptyList(),
    val download: List<SyncCandidate<Server>> = emptyList()
) {
    fun counts(): SyncGroupCounts = SyncGroupCounts(
        matched = matched.size,
        upload = upload.count { it.selected },
        download = download.count { it.selected },
        updates = matched.count { it.selected && it.isUpdate },
        conflicts = unresolvedConflictCount()
    )

    fun unresolvedConflictCount(): Int = conflicts.count { it.resolvedTo == null }
}

/**
 * Editor state for the Shopping Lists sync group. Lists are linked purely by
 * `serverId` (no match routine), so — unlike the match-based groups — there are no
 * upload/download pools and no match picker. The linked pairs carry their git-style
 * content state (see [ShoppingListSyncPlan]); [resolutions] holds the user's per-list
 * "use local" / "use server" choice for conflicts (keyed by local list id).
 */
data class ShoppingListSyncPlanUiState(
    val planGenerated: Boolean = false,
    val plan: ShoppingListSyncPlan? = null,
    val resolutions: Map<Long, SyncContentState> = emptyMap(),
)

data class NextcloudSyncUiState(
    val llmAvailable: Boolean = false,
    val useLlm: Boolean = false,
    /** Groups currently generating their plan ("Fetch latest"), tracked per group. */
    val generating: Set<SyncGroup> = emptySet(),
    /** Groups currently executing a confirmed sync ("Apply changes"), tracked per group. */
    val executing: Set<SyncGroup> = emptySet(),
    val error: String? = null,
    val success: Boolean = false,
    /** Unresolved conflicts skipped by the last confirmed sync (resolved ones ran). */
    val skippedConflicts: Int = 0,
    val categories: SyncGroupEditorState<CategoryEntity, NextcloudCategoryDto> = SyncGroupEditorState(),
    val stores: SyncGroupEditorState<StoreEntity, NextcloudStoreDto> = SyncGroupEditorState(),
    val products: SyncGroupEditorState<ProductEntity, NextcloudProductDto> = SyncGroupEditorState(),
    val shoppingListPlan: ShoppingListSyncPlanUiState = ShoppingListSyncPlanUiState()
) {
    val isGenerating: Boolean get() = generating.isNotEmpty()
    val isExecuting: Boolean get() = executing.isNotEmpty()
    val anyBusy: Boolean get() = isGenerating || isExecuting

    fun isGenerating(group: SyncGroup): Boolean = group in generating
    fun isExecuting(group: SyncGroup): Boolean = group in executing

    /** Busy while this group either generates its plan or executes a sync. */
    fun isBusy(group: SyncGroup): Boolean = isGenerating(group) || isExecuting(group)
}

class NextcloudSyncViewModel(
    private val app: ByeByeMoneyApplication
) : ViewModel() {

    private val preferencesManager = app.preferencesManager
    private val categoryRepository = CategorySyncRepository(
        categoryDao = app.database.categoryDao(),
        syncStateDao = app.database.syncStateDao(),
        preferencesManager = preferencesManager,
        pendingDeleteDao = app.database.syncPendingDeleteDao()
    )
    private val storeRepository = StoreSyncRepository(
        storeDao = app.database.storeDao(),
        syncStateDao = app.database.syncStateDao(),
        categoryDao = app.database.categoryDao(),
        preferencesManager = preferencesManager,
        pendingDeleteDao = app.database.syncPendingDeleteDao()
    )
    private val productRepository = ProductSyncRepository(
        productDao = app.database.productDao(),
        productAliasDao = app.database.productAliasDao(),
        categoryDao = app.database.categoryDao(),
        storeDao = app.database.storeDao(),
        priceDao = app.database.priceDao(),
        syncStateDao = app.database.syncStateDao(),
        preferencesManager = preferencesManager,
        pendingDeleteDao = app.database.syncPendingDeleteDao()
    )
    private val shoppingListsRepository = ShoppingListsSyncRepository(
        shoppingListDao = app.database.shoppingListDao(),
        storeDao = app.database.storeDao(),
        categoryDao = app.database.categoryDao(),
        productDao = app.database.productDao(),
        pendingDeleteDao = app.database.syncPendingDeleteDao(),
        syncStateDao = app.database.syncStateDao(),
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

    /**
     * Refreshes the plan for every group at once. Each group's busy flag is toggled
     * individually (and the groups run concurrently), so a row's spinner stops as soon
     * as its own group has finished fetching/matching.
     */
    fun syncNow(onError: ((String) -> Unit)? = null) {
        if (_uiState.value.anyBusy) return
        _uiState.update { it.copy(error = null, success = false) }
        viewModelScope.launch {
            val useLlm = _uiState.value.useLlm && _uiState.value.llmAvailable
            val errors = coroutineScope {
                val llmCall: (suspend (String) -> String?)? =
                    if (useLlm) {
                        { prompt ->
                            agentManager.generateText(MultiLanguageCategoryMatcher.LLM_SYSTEM_INSTRUCTION, prompt)
                        }
                    } else {
                        null
                    }
                val categories = async { generateCategoriesInternal(useLlm, llmCall) }
                val stores = async { generateStoresInternal() }
                val products = async { generateProductsInternal() }
                val lists = async { generateShoppingListsInternal() }
                listOfNotNull(
                    categories.await(),
                    stores.await(),
                    products.await(),
                    lists.await()
                )
            }
            if (errors.isNotEmpty()) {
                _uiState.update { it.copy(error = errors.first()) }
                onError?.invoke(errors.first())
            }
        }
    }

    /**
     * Generates (or refreshes) the plan for a single group. Used by the per-group sync
     * screens so the user can fetch & apply one group without touching the others.
     */
    fun generatePlan(group: SyncGroup, onError: ((String) -> Unit)? = null) {
        if (_uiState.value.anyBusy) return
        _uiState.update { it.copy(error = null, success = false) }
        viewModelScope.launch {
            val message = when (group) {
                SyncGroup.CATEGORIES -> {
                    val useLlm = _uiState.value.useLlm && _uiState.value.llmAvailable
                    val llmCall: (suspend (String) -> String?)? =
                        if (useLlm) {
                            { prompt ->
                                agentManager.generateText(MultiLanguageCategoryMatcher.LLM_SYSTEM_INSTRUCTION, prompt)
                            }
                        } else {
                            null
                        }
                    generateCategoriesInternal(useLlm, llmCall)
                }
                SyncGroup.STORES -> generateStoresInternal()
                SyncGroup.PRODUCTS -> generateProductsInternal()
                SyncGroup.SHOPPING_LISTS -> generateShoppingListsInternal()
            }
            if (message != null) {
                _uiState.update { it.copy(error = message) }
                onError?.invoke(message)
            }
        }
    }

    /**
     * Applies (executes) a single group's already-generated plan — the per-group
     * counterpart of [confirmAndSync]. Unresolved conflicts in that group are skipped.
     */
    fun applyGroup(group: SyncGroup, onFinished: (Boolean) -> Unit = {}) {
        if (_uiState.value.anyBusy) return
        _uiState.update { it.copy(error = null, success = false) }
        viewModelScope.launch {
            val skipped = when (group) {
                SyncGroup.CATEGORIES -> _uiState.value.categories.unresolvedConflictCount()
                SyncGroup.STORES -> _uiState.value.stores.unresolvedConflictCount()
                SyncGroup.PRODUCTS -> _uiState.value.products.unresolvedConflictCount()
                SyncGroup.SHOPPING_LISTS -> 0
            }
            val result = when (group) {
                SyncGroup.CATEGORIES -> runMatchGroupExecution(
                    SyncGroup.CATEGORIES, _uiState.value.categories, categoryRepository
                )
                SyncGroup.STORES -> runMatchGroupExecution(
                    SyncGroup.STORES, _uiState.value.stores, storeRepository
                )
                SyncGroup.PRODUCTS -> runMatchGroupExecution(
                    SyncGroup.PRODUCTS, _uiState.value.products, productRepository
                )
                SyncGroup.SHOPPING_LISTS -> runShoppingListsExecution()
            }
            _uiState.update {
                it.copy(
                    success = result.isSuccess,
                    skippedConflicts = if (result.isSuccess) skipped else 0,
                    error = result.exceptionOrNull()?.localizedMessage
                )
            }
            onFinished(result.isSuccess)
        }
    }

    // ---- Per-group plan generation helpers ----------------------------------------

    /** @return an error message, or null when the category plan generated fine. */
    private suspend fun generateCategoriesInternal(
        useLlm: Boolean,
        llmCall: (suspend (String) -> String?)? = null
    ): String? = runPlanGroupBusy(SyncGroup.CATEGORIES) {
        categoryRepository.generateSyncPlan(useLlm = useLlm, llmCall = llmCall)
            .onSuccess { plan ->
                _uiState.update { it.copy(categories = editorFromPlan(plan)) }
            }
            .messageOrNull()
    }

    /** @return an error message, or null when the store plan generated fine. */
    private suspend fun generateStoresInternal(): String? = runPlanGroupBusy(SyncGroup.STORES) {
        storeRepository.generateSyncPlan()
            .onSuccess { plan ->
                _uiState.update { it.copy(stores = editorFromPlan(plan)) }
            }
            .messageOrNull()
    }

    /** @return an error message, or null when the product plan generated fine. */
    private suspend fun generateProductsInternal(): String? = runPlanGroupBusy(SyncGroup.PRODUCTS) {
        productRepository.generateSyncPlan()
            .onSuccess { plan ->
                _uiState.update { it.copy(products = editorFromPlan(plan)) }
            }
            .messageOrNull()
    }

    /** @return an error message, or null when the shopping list plan generated fine. */
    private suspend fun generateShoppingListsInternal(): String? = runPlanGroupBusy(SyncGroup.SHOPPING_LISTS) {
        shoppingListsRepository.generateSyncPlan()
            .onSuccess { listPlan ->
                _uiState.update { state ->
                    val keptResolutions = state.shoppingListPlan.resolutions
                        .filterKeys { id -> listPlan.linked.any { it.local.id == id } }
                    state.copy(
                        shoppingListPlan = ShoppingListSyncPlanUiState(
                            planGenerated = true,
                            plan = listPlan,
                            resolutions = keptResolutions
                        )
                    )
                }
            }
            .messageOrNull()
    }

    /**
     * Runs one plan-generation block while the group's generating flag is set, then
     * clears it. The block should update the group's editor on success.
     */
    private suspend fun runPlanGroupBusy(
        group: SyncGroup,
        block: suspend () -> String?
    ): String? {
        _uiState.update { it.copy(generating = it.generating + group) }
        return try {
            block()
        } finally {
            _uiState.update { it.copy(generating = it.generating - group) }
        }
    }

    private fun <T> Result<T>.messageOrNull(): String? = when {
        isSuccess -> null
        else -> exceptionOrNull()?.localizedMessage ?: "Failed to generate sync plan"
    }

    /**
     * Toggles a conflict's resolution towards one side. Choosing the same side again
     * clears the resolution (the conflict falls back to the default client-wins action
     * on the next confirmed sync).
     */
    fun resolveShoppingListConflict(localId: Long, side: SyncContentState) {
        _uiState.update { state ->
            val resolutions = state.shoppingListPlan.resolutions.toMutableMap()
            if (resolutions[localId] == side) {
                resolutions.remove(localId)
            } else {
                resolutions[localId] = side
            }
            state.copy(shoppingListPlan = state.shoppingListPlan.copy(resolutions = resolutions))
        }
    }

    private fun <Local, Server> editorFromPlan(
        plan: SyncPlan<Local, Server>
    ): SyncGroupEditorState<Local, Server> = SyncGroupEditorState(
        planGenerated = true,
        matched = plan.matched
            .filterNot { it.contentState == SyncContentState.CONFLICT }
            .map { SyncMatchCandidate(match = it, selected = true) },
        conflicts = plan.conflicts,
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

    fun toggleUpdate(match: SyncMatch<CategoryEntity, NextcloudCategoryDto>) {
        _uiState.update { state ->
            state.copy(categories = toggleUpdateIn(state.categories, match))
        }
    }

    fun resolveConflict(
        match: SyncMatch<CategoryEntity, NextcloudCategoryDto>,
        side: SyncContentState
    ) {
        _uiState.update { state ->
            state.copy(categories = resolveConflictIn(state.categories, match, side))
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

    fun toggleUpdateStore(match: SyncMatch<StoreEntity, NextcloudStoreDto>) {
        _uiState.update { state ->
            state.copy(stores = toggleUpdateIn(state.stores, match))
        }
    }

    fun resolveStoreConflict(
        match: SyncMatch<StoreEntity, NextcloudStoreDto>,
        side: SyncContentState
    ) {
        _uiState.update { state ->
            state.copy(stores = resolveConflictIn(state.stores, match, side))
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

    fun toggleUpdateProduct(match: SyncMatch<ProductEntity, NextcloudProductDto>) {
        _uiState.update { state ->
            state.copy(products = toggleUpdateIn(state.products, match))
        }
    }

    fun resolveProductConflict(
        match: SyncMatch<ProductEntity, NextcloudProductDto>,
        side: SyncContentState
    ) {
        _uiState.update { state ->
            state.copy(products = resolveConflictIn(state.products, match, side))
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
        matched = editor.matched.filterNot { it.match == match },
        conflicts = editor.conflicts.filterNot { it.match == match },
        upload = editor.upload + SyncCandidate(item = match.local, selected = false),
        download = editor.download + SyncCandidate(item = match.server, selected = false)
    )

    /**
     * Toggles whether a pending change ([SyncContentState.LOCAL_CHANGED] /
     * [SyncContentState.SERVER_CHANGED]) is applied on the next confirmed sync.
     * Selected by default; an unselected update is left untouched (like an
     * unstaged change in git).
     */
    private fun <Local, Server> toggleUpdateIn(
        editor: SyncGroupEditorState<Local, Server>,
        match: SyncMatch<Local, Server>
    ): SyncGroupEditorState<Local, Server> = editor.copy(
        matched = editor.matched.map {
            if (it.match == match) it.copy(selected = !it.selected) else it
        }
    )

    /**
     * Resolves a conflict towards one side. Choosing the same side again clears the
     * resolution (back to unresolved, so the conflict is skipped on the next sync).
     */
    private fun <Local, Server> resolveConflictIn(
        editor: SyncGroupEditorState<Local, Server>,
        match: SyncMatch<Local, Server>,
        side: SyncContentState
    ): SyncGroupEditorState<Local, Server> = editor.copy(
        conflicts = editor.conflicts.map {
            if (it.match == match) {
                it.copy(resolvedTo = if (it.resolvedTo == side) null else side)
            } else {
                it
            }
        }
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
            matched = editor.matched + SyncMatchCandidate(
                match = SyncMatch(local = local, server = server, reason = "Manual match"),
                selected = true
            )
        )
    }

    // ---- Execution -------------------------------------------------------------------

    /**
     * Executes every planned group in the required order (Categories → Stores →
     * Products) and then the Shopping Lists plan, which depends on the
     * store/category/product `serverId`s those groups populate. A failure in
     * one group does not prevent the later groups from running. Each group toggles
     * its own busy flag, so the settings hub spinner stops per row as it completes.
     */
    fun confirmAndSync(onFinished: (Boolean) -> Unit) {
        if (_uiState.value.anyBusy) return
        _uiState.update { it.copy(error = null, success = false) }
        viewModelScope.launch {
            // Unresolved conflicts are skipped (not applied) — see the "Conflicts"
            // section; the rest of the sync still runs.
            val skippedConflicts = _uiState.value.categories.unresolvedConflictCount() +
                _uiState.value.stores.unresolvedConflictCount() +
                _uiState.value.products.unresolvedConflictCount()

            val groupResults = listOf(
                runMatchGroupExecution(
                    SyncGroup.CATEGORIES, _uiState.value.categories, categoryRepository
                ),
                runMatchGroupExecution(
                    SyncGroup.STORES, _uiState.value.stores, storeRepository
                ),
                runMatchGroupExecution(
                    SyncGroup.PRODUCTS, _uiState.value.products, productRepository
                )
            )

            // The shopping lists plan is re-generated here (after the groups have
            // populated the category/store/product server ids) so list refs resolve;
            // the user's per-list conflict resolutions (keyed by local id) are applied.
            // Locally deleted lists are drained first so they are not re-pulled.
            val listResult = runShoppingListsExecution()

            val allResults = groupResults + listResult
            val allOk = allResults.all { it.isSuccess }

            _uiState.update {
                it.copy(
                    success = allOk,
                    skippedConflicts = if (allOk) skippedConflicts else 0,
                    error = allResults.firstNotNullOfOrNull { r -> r.exceptionOrNull()?.localizedMessage }
                )
            }
            onFinished(allOk)
        }
    }

    /**
     * Runs one match-based group's execution (Categories/Stores/Products) under its own
     * busy flag, applying the current editor state.
     */
    private suspend fun <Local, Server> runMatchGroupExecution(
        group: SyncGroup,
        editor: SyncGroupEditorState<Local, Server>,
        repository: com.otakeeesen.byebyemoneylist.data.sync.SyncRepository<Local, Server>
    ): Result<Boolean> {
        _uiState.update { it.copy(executing = it.executing + group) }
        return try {
            buildExecution(editor, repository).invoke()
        } finally {
            _uiState.update { it.copy(executing = it.executing - group) }
        }
    }

    /**
     * Executes the shopping-lists plan under its own busy flag: drains pending deletes,
     * regenerates the plan so freshly synced store/category/product refs resolve, then
     * applies the user's per-list conflict resolutions (client-wins by default).
     */
    private suspend fun runShoppingListsExecution(): Result<Boolean> {
        _uiState.update { it.copy(executing = it.executing + SyncGroup.SHOPPING_LISTS) }
        return try {
            runCatching {
                shoppingListsRepository.deletePendingServerLists().getOrThrow()
                val listPlan = shoppingListsRepository.generateSyncPlan().getOrThrow()
                val resolutions = _uiState.value.shoppingListPlan.resolutions
                shoppingListsRepository.executeSyncPlan(listPlan) { link ->
                    when (resolutions[link.local.id]) {
                        SyncContentState.LOCAL_CHANGED -> ShoppingListLinkAction.PUSH_LOCAL
                        SyncContentState.SERVER_CHANGED -> ShoppingListLinkAction.PULL_SERVER
                        else -> defaultShoppingListAction(link.state)
                    }
                }.getOrThrow()
                true
            }
        } finally {
            _uiState.update { it.copy(executing = it.executing - SyncGroup.SHOPPING_LISTS) }
        }
    }

    private fun <Local, Server> buildExecution(
        editor: SyncGroupEditorState<Local, Server>,
        repository: com.otakeeesen.byebyemoneylist.data.sync.SyncRepository<Local, Server>
    ): suspend () -> Result<Boolean> {
        val push = editor.upload.filter { it.selected }.map { it.item }
        val pull = editor.download.filter { it.selected }.map { it.item }

        // Conflict matches stay linked (serverId persisted) whether or not they are
        // resolved; only resolved ones also run as a push/pull update.
        val links = editor.matched.map { it.match.local to it.match.server } +
            editor.conflicts.map { it.match.local to it.match.server }
        val updateToServer = editor.matched
            .filter { it.selected && it.match.contentState == SyncContentState.LOCAL_CHANGED }
            .map { it.match.local } +
            editor.conflicts.mapNotNull { it.pickedLocal() }
        val updateToLocal = editor.matched
            .filter { it.selected && it.match.contentState == SyncContentState.SERVER_CHANGED }
            .map { it.match.server } +
            editor.conflicts.mapNotNull { it.pickedServer() }
        val plan = SyncPlan(
            matched = editor.matched.map { it.match } + editor.conflicts.map { it.match },
            toPushToServer = push,
            toPullToClient = pull
        )
        return {
            repository.executeSyncPlan(
                plan = plan,
                pushItems = push,
                pullItems = pull,
                linkedPairs = links,
                updateToServer = updateToServer,
                updateToLocal = updateToLocal
            )
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(error = null, success = false, skippedConflicts = 0) }
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
