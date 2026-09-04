package com.otakeeesen.byebyemoneylist.ui.components.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.data.agent.AgentManager
import com.otakeeesen.byebyemoneylist.data.agent.AgentQueryExecutor
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.sync.CategorySyncRepository
import com.otakeeesen.byebyemoneylist.data.sync.MultiLanguageCategoryMatcher
import com.otakeeesen.byebyemoneylist.data.sync.NextcloudCategoryDto
import com.otakeeesen.byebyemoneylist.data.sync.SyncCoordinator
import com.otakeeesen.byebyemoneylist.data.sync.SyncPhase
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncCandidate
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncGroupCounts
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncMatch
import com.otakeeesen.byebyemoneylist.data.sync.model.SyncPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoryEditorState(
    val planGenerated: Boolean = false,
    val matched: List<SyncMatch<CategoryEntity, NextcloudCategoryDto>> = emptyList(),
    val upload: List<SyncCandidate<CategoryEntity>> = emptyList(),
    val download: List<SyncCandidate<NextcloudCategoryDto>> = emptyList()
) {
    fun counts(): SyncGroupCounts = SyncGroupCounts(
        matched = matched.size,
        upload = upload.count { it.selected },
        download = download.count { it.selected }
    )
}

data class NextcloudSyncUiState(
    val llmAvailable: Boolean = false,
    val useLlm: Boolean = false,
    val isGenerating: Boolean = false,
    val isExecuting: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    val categories: CategoryEditorState = CategoryEditorState()
)

class NextcloudSyncViewModel(
    private val app: ByeByeMoneyApplication
) : ViewModel() {

    private val preferencesManager = app.preferencesManager
    private val categoryRepository = CategorySyncRepository(
        categoryDao = app.database.categoryDao(),
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
            val llmCall: (suspend (String) -> String?)? =
                if (state.useLlm && state.llmAvailable) {
                    { prompt ->
                        agentManager.generateText(MultiLanguageCategoryMatcher.LLM_SYSTEM_INSTRUCTION, prompt)
                    }
                } else {
                    null
                }
            categoryRepository.generateSyncPlan(
                useLlm = state.useLlm && state.llmAvailable,
                llmCall = llmCall,
                onPhase = { phase ->
                    if (phase == SyncPhase.LLM_MATCHING) {
                        _uiState.update { it.copy(isGenerating = true) }
                    }
                }
            ).onSuccess { plan ->
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        categories = editorFromPlan(plan)
                    )
                }
            }.onFailure { e ->
                val message = e.localizedMessage ?: "Failed to generate sync plan"
                _uiState.update {
                    it.copy(isGenerating = false, error = message)
                }
                onError?.invoke(message)
            }
        }
    }

    private fun editorFromPlan(
        plan: SyncPlan<CategoryEntity, NextcloudCategoryDto>
    ): CategoryEditorState = CategoryEditorState(
        planGenerated = true,
        matched = plan.matched,
        upload = plan.toPushToServer.map { SyncCandidate(item = it, selected = true) },
        download = plan.toPullToClient.map { SyncCandidate(item = it, selected = true) }
    )

    // ---- Category editor operations -------------------------------------------------

    fun toggleUpload(local: CategoryEntity) {
        _uiState.update { state ->
            val cat = state.categories
            state.copy(
                categories = cat.copy(
                    upload = cat.upload.map {
                        if (it.item == local) it.copy(selected = !it.selected) else it
                    }
                )
            )
        }
    }

    fun toggleDownload(server: NextcloudCategoryDto) {
        _uiState.update { state ->
            val cat = state.categories
            state.copy(
                categories = cat.copy(
                    download = cat.download.map {
                        if (it.item == server) it.copy(selected = !it.selected) else it
                    }
                )
            )
        }
    }

    fun selectAllUpload(select: Boolean) {
        _uiState.update { state ->
            val cat = state.categories
            state.copy(
                categories = cat.copy(
                    upload = cat.upload.map { it.copy(selected = select) }
                )
            )
        }
    }

    fun selectAllDownload(select: Boolean) {
        _uiState.update { state ->
            val cat = state.categories
            state.copy(
                categories = cat.copy(
                    download = cat.download.map { it.copy(selected = select) }
                )
            )
        }
    }

    /**
     * Unlinks a matched pair. The local item returns to the upload pool and the server item to
     * the download pool. Unlinked items are ordinary unmatched candidates: they appear in the
     * Upload/Download sections and can be re-matched, re-uploaded or re-downloaded (creating a
     * new category with a new id on the destination side).
     */
    fun unlinkMatch(match: SyncMatch<CategoryEntity, NextcloudCategoryDto>) {
        _uiState.update { state ->
            val cat = state.categories
            state.copy(
                categories = cat.copy(
                    matched = cat.matched.filterNot {
                        it.local == match.local && it.server == match.server
                    },
                    upload = cat.upload + SyncCandidate(
                        item = match.local,
                        selected = false
                    ),
                    download = cat.download + SyncCandidate(
                        item = match.server,
                        selected = false
                    )
                )
            )
        }
    }

    /**
     * Creates a manual match between an unmatched local and an unmatched server item.
     * Both must currently be unmatched (present in their respective pools).
     */
    fun createMatch(local: CategoryEntity, server: NextcloudCategoryDto) {
        _uiState.update { state ->
            val cat = state.categories
            val localInPool = cat.upload.any { it.item == local }
            val serverInPool = cat.download.any { it.item == server }
            if (!localInPool || !serverInPool) return@update state
            state.copy(
                categories = cat.copy(
                    upload = cat.upload.filterNot { it.item == local },
                    download = cat.download.filterNot { it.item == server },
                    matched = cat.matched + SyncMatch(
                        local = local,
                        server = server,
                        reason = "Manual match"
                    )
                )
            )
        }
    }

    // ---- Execution -------------------------------------------------------------------

    fun confirmAndSync(onFinished: (Boolean) -> Unit) {
        if (_uiState.value.isExecuting) return
        _uiState.update { it.copy(isExecuting = true, error = null, success = false) }
        viewModelScope.launch {
            val state = _uiState.value
            val cat = state.categories
            val push = cat.upload.filter { it.selected }.map { it.item }
            val pull = cat.download.filter { it.selected }.map { it.item }
            val links = cat.matched.map { it.local to it.server }
            val plan = SyncPlan(
                matched = cat.matched,
                toPushToServer = push,
                toPullToClient = pull
            )

            val coordinator = SyncCoordinator(
                listOf {
                    categoryRepository.executeSyncPlan(
                        plan = plan,
                        pushItems = push,
                        pullItems = pull,
                        linkedPairs = links
                    )
                }
            )
            val results = coordinator.executeAll()
            val allOk = results.all { it.isSuccess }
            _uiState.update {
                it.copy(
                    isExecuting = false,
                    success = allOk,
                    error = results.firstNotNullOfOrNull { r -> r.exceptionOrNull()?.localizedMessage }
                )
            }
            onFinished(allOk)
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
