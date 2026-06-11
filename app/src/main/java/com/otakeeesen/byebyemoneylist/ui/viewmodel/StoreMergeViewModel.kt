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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StoreMergeUiState(
    val storeA: StoreEntity? = null,
    val storeB: StoreEntity? = null,
    val storeACategories: List<CategoryEntity> = emptyList(),
    val storeBCategories: List<CategoryEntity> = emptyList(),
    val selectedName: String = "",
    val selectedAddress: String = "",
    val selectedLogoPath: String? = null,
    val selectedCategories: List<CategoryEntity> = emptyList(),
    val isMerging: Boolean = false,
    val mergeComplete: Boolean = false,
    val allCategories: List<CategoryEntity> = emptyList()
)

class StoreMergeViewModel(
    private val storeRepository: StoreRepository,
    private val categoryRepository: CategoryRepository,
    private val storeAId: Long,
    private val storeBId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoreMergeUiState())
    val uiState: StateFlow<StoreMergeUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val a = withContext(Dispatchers.IO) { storeRepository.getAllStoresOnce().find { it.id == storeAId } }
            val b = withContext(Dispatchers.IO) { storeRepository.getAllStoresOnce().find { it.id == storeBId } }
            val aCats = withContext(Dispatchers.IO) { categoryRepository.getCategoriesByStoreIdOnce(storeAId) }
            val bCats = withContext(Dispatchers.IO) { categoryRepository.getCategoriesByStoreIdOnce(storeBId) }
            val categories = withContext(Dispatchers.IO) { categoryRepository.getAllCategoriesOnce() }
            
            _uiState.update { 
                it.copy(
                    storeA = a,
                    storeB = b,
                    storeACategories = aCats,
                    storeBCategories = bCats,
                    selectedName = a?.name ?: "",
                    selectedAddress = a?.address ?: "",
                    selectedLogoPath = a?.logoPath,
                    selectedCategories = aCats,
                    allCategories = categories
                ) 
            }
        }
    }

    fun selectName(name: String) { _uiState.update { it.copy(selectedName = name) } }
    fun selectAddress(address: String) { _uiState.update { it.copy(selectedAddress = address) } }
    fun selectLogoPath(path: String?) { _uiState.update { it.copy(selectedLogoPath = path) } }
    fun selectCategories(categories: List<CategoryEntity>) { _uiState.update { it.copy(selectedCategories = categories) } }

    fun updateName(name: String) { _uiState.update { it.copy(selectedName = name) } }
    fun updateAddress(address: String) { _uiState.update { it.copy(selectedAddress = address) } }

    fun performMerge() {
        val state = _uiState.value
        val a = state.storeA ?: return
        val b = state.storeB ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isMerging = true) }
            
            val resultStore = a.copy(
                name = state.selectedName,
                address = state.selectedAddress.ifBlank { null },
                logoPath = state.selectedLogoPath
            )

            withContext(Dispatchers.IO) {
                storeRepository.mergeStores(storeAId, storeBId, resultStore, state.selectedCategories.map { it.id })
            }
            
            _uiState.update { it.copy(isMerging = false, mergeComplete = true) }
        }
    }

    companion object {
        fun createFactory(storeAId: Long, storeBId: Long): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as ByeByeMoneyApplication
                return StoreMergeViewModel(application.storeRepository, application.categoryRepository, storeAId, storeBId) as T
            }
        }
    }
}
