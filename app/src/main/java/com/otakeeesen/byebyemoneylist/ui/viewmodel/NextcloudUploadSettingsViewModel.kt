package com.otakeeesen.byebyemoneylist.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.otakeeesen.byebyemoneylist.BuildConfig
import com.otakeeesen.byebyemoneylist.ByeByeMoneyApplication
import com.otakeeesen.byebyemoneylist.data.integration.PurchaseUploadRepository
import com.otakeeesen.byebyemoneylist.data.local.PreferencesManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NextcloudUploadSettingsUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isTesting: Boolean = false,
)

sealed class NextcloudUploadSettingsEvent {
    data object ConnectionSuccess : NextcloudUploadSettingsEvent()
    data class ConnectionFailure(val message: String?) : NextcloudUploadSettingsEvent()
    data object InsecureUrl : NextcloudUploadSettingsEvent()
}

class NextcloudUploadSettingsViewModel(
    private val preferencesManager: PreferencesManager,
    private val uploadRepository: PurchaseUploadRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

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
                return NextcloudUploadSettingsViewModel(
                    application.preferencesManager,
                    application.purchaseUploadRepository,
                ) as T
            }
        }
    }

    private val _uiState = MutableStateFlow(NextcloudUploadSettingsUiState())
    val uiState: StateFlow<NextcloudUploadSettingsUiState> = _uiState.asStateFlow()

    private val _events = Channel<NextcloudUploadSettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch(ioDispatcher) {
            _uiState.update {
                NextcloudUploadSettingsUiState(
                    serverUrl = preferencesManager.getNextcloudUrl(),
                    username = preferencesManager.getNextcloudUsername(),
                    password = preferencesManager.getNextcloudPassword(),
                )
            }
        }
    }

    fun onServerUrlChange(value: String) {
        _uiState.update { it.copy(serverUrl = value) }
        viewModelScope.launch(ioDispatcher) { preferencesManager.setNextcloudUrl(value) }
    }

    fun onUsernameChange(value: String) {
        _uiState.update { it.copy(username = value) }
        viewModelScope.launch(ioDispatcher) { preferencesManager.setNextcloudUsername(value) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value) }
        viewModelScope.launch(ioDispatcher) { preferencesManager.setNextcloudPassword(value) }
    }

    fun testConnection() {
        val current = _uiState.value
        if (current.isTesting) return

        if (!isUrlAllowed(current.serverUrl)) {
            viewModelScope.launch { _events.send(NextcloudUploadSettingsEvent.InsecureUrl) }
            return
        }

        _uiState.update { it.copy(isTesting = true) }
        viewModelScope.launch {
            val result = uploadRepository.testConnection(
                serverUrl = current.serverUrl,
                username = current.username,
                password = current.password,
            )
            _uiState.update { it.copy(isTesting = false) }
            _events.send(
                result.fold(
                    onSuccess = { NextcloudUploadSettingsEvent.ConnectionSuccess },
                    onFailure = { NextcloudUploadSettingsEvent.ConnectionFailure(it.localizedMessage ?: it.message) },
                )
            )
        }
    }

    private fun isUrlAllowed(raw: String): Boolean {
        val url = raw.trim()
        if (url.startsWith("http://")) return BuildConfig.DEBUG
        return true
    }
}
