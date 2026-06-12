package com.otakeeesen.byebyemoneylist.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.data.local.entity.StoreEntity
import com.otakeeesen.byebyemoneylist.data.local.entity.CategoryEntity
import com.otakeeesen.byebyemoneylist.data.local.repository.StoreRepository
import com.otakeeesen.byebyemoneylist.data.local.repository.CategoryRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class StoreMergeSearchUiState(
    val stores: List<StoreEntity> = emptyList(),
    val filteredStores: List<StoreEntity> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val allCategories: List<CategoryEntity> = emptyList()
)

class StoreMergeSearchViewModel(
    private val storeRepository: StoreRepository,
    private val categoryRepository: CategoryRepository,
    private val storeAId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoreMergeSearchUiState())
    val uiState: StateFlow<StoreMergeSearchUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            combine(
                storeRepository.allStores,
                categoryRepository.allCategories
            ) { stores, categories ->
                val otherStores = stores.filter { it.id != storeAId }
                _uiState.update { 
                    it.copy(
                        stores = otherStores,
                        filteredStores = applyFilter(otherStores, it.searchQuery),
                        allCategories = categories
                    ) 
                }
            }.collect {}
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { 
            it.copy(
                searchQuery = query,
                filteredStores = applyFilter(it.stores, query)
            ) 
        }
    }

    private fun applyFilter(stores: List<StoreEntity>, query: String): List<StoreEntity> {
        if (query.isBlank()) return stores
        val lowerQuery = query.lowercase().trim()
        return stores.filter { it.name.lowercase().contains(lowerQuery) || (it.address?.lowercase()?.contains(lowerQuery) == true) }
    }

    companion object {
        fun createFactory(storeAId: Long): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as ByeByeMoneyApplication
                return StoreMergeSearchViewModel(application.storeRepository, application.categoryRepository, storeAId) as T
            }
        }
    }
}
