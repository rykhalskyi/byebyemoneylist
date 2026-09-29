package com.otakeeesen.byebyemoneylist.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.data.ListKind
import com.otakeeesen.byebyemoneylist.data.local.entity.ShoppingListItemEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.listKind
import com.otakeeesen.byebyemoneylist.data.local.repository.ShoppingListRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ToBuyItem(
    val id: Long,
    val name: String,
    val isChecked: Boolean,
)

data class ToBuyUiState(
    val activeListId: Long? = null,
    val title: String = "",
    val items: List<ToBuyItem> = emptyList(),
    val newItemText: String = "",
    val editingItem: ToBuyItem? = null,
    val isLoading: Boolean = true,
) {
    val checkedCount: Int get() = items.count { it.isChecked }
    val totalCount: Int get() = items.size
}

sealed class ToBuyEvent {
    data class ItemDeleted(val name: String) : ToBuyEvent()
}

class ToBuyViewModel(
    private val repository: ShoppingListRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras,
            ): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as ByeByeMoneyApplication
                return ToBuyViewModel(application.shoppingListRepository) as T
            }
        }
    }

    private val _uiState = MutableStateFlow(ToBuyUiState())
    val uiState: StateFlow<ToBuyUiState> = _uiState.asStateFlow()

    private val _events = Channel<ToBuyEvent>(Channel.BUFFERED)
    val events: Flow<ToBuyEvent> = _events.receiveAsFlow()

    private var undoableItem: ShoppingListItemEntity? = null
    private var undoJob: Job? = null

    init {
        viewModelScope.launch {
            repository.allShoppingLists
                .map { lists -> lists.firstOrNull { it.listKind == ListKind.NEED_TO_BUY && it.isActive } }
                .distinctUntilChanged()
                .flatMapLatest { active ->
                    if (active == null) {
                        flowOf(null)
                    } else {
                        repository.getItemsForList(active.id).map { items -> active to items }
                    }
                }
                .collect { active ->
                    if (active == null) {
                        _uiState.update {
                            it.copy(
                                activeListId = null,
                                title = "",
                                items = emptyList(),
                                isLoading = false,
                            )
                        }
                    } else {
                        val (list, items) = active
                        _uiState.update {
                            it.copy(
                                activeListId = list.id,
                                title = list.name,
                                items = items.sortedBy { item -> item.position }.map { item ->
                                    ToBuyItem(
                                        id = item.id,
                                        name = item.customName?.trim().orEmpty(),
                                        isChecked = item.isChecked,
                                    )
                                },
                                isLoading = false,
                            )
                        }
                    }
                }
        }
    }

    fun updateNewItemText(text: String) {
        _uiState.update { it.copy(newItemText = text) }
    }

    fun addItem() {
        val listId = _uiState.value.activeListId ?: return
        val name = _uiState.value.newItemText.trim()
        if (name.isEmpty()) return
        _uiState.update { it.copy(newItemText = "") }
        viewModelScope.launch {
            withContext(ioDispatcher) { repository.addToBuyItem(listId, name) }
        }
    }

    fun toggleChecked(item: ToBuyItem, checked: Boolean) {
        _uiState.update { state ->
            state.copy(
                items = state.items.map { if (it.id == item.id) it.copy(isChecked = checked) else it }
            )
        }
        viewModelScope.launch {
            withContext(ioDispatcher) { repository.updateItemChecked(item.id, checked) }
        }
    }

    fun startEditingItem(item: ToBuyItem) {
        _uiState.update { it.copy(editingItem = item) }
    }

    fun stopEditingItem() {
        _uiState.update { it.copy(editingItem = null) }
    }

    fun renameItem(item: ToBuyItem, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            stopEditingItem()
            return
        }
        viewModelScope.launch {
            withContext(ioDispatcher) {
                val entity = repository.getShoppingListItemById(item.id) ?: return@withContext
                repository.updateShoppingListItem(entity.copy(customName = trimmed))
            }
            stopEditingItem()
        }
    }

    fun deleteItem(item: ToBuyItem) {
        viewModelScope.launch {
            undoJob?.cancel()
            val deleted = withContext(ioDispatcher) { repository.deleteShoppingListItemAndReturn(item.id) }
            if (deleted != null) {
                undoableItem = deleted
                _events.send(ToBuyEvent.ItemDeleted(item.name))
                undoJob = viewModelScope.launch {
                    delay(4_000L)
                    undoableItem = null
                }
            }
        }
    }

    fun undoDelete() {
        val item = undoableItem ?: return
        undoJob?.cancel()
        undoableItem = null
        viewModelScope.launch {
            withContext(ioDispatcher) { repository.insertShoppingListItem(item) }
        }
    }

    fun createActiveList(prefix: String, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val id = withContext(ioDispatcher) { repository.createToBuyList(prefix) }
            onCreated(id)
        }
    }
}
