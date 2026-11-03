package com.techpath.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.techpath.BuildConfig
import com.techpath.appContainer
import com.techpath.data.DatasetRepository
import com.techpath.data.PathInfo
import com.techpath.data.StudyMode
import com.techpath.data.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loading: Boolean = true,
    val year: Int = 1,
    val path: PathInfo? = null,
    val studyMode: StudyMode = StudyMode.STATIC,
    val apiKeyDraft: String = "",
    val apiKeySaved: Boolean = false,
    /** A key was provided at build time via local.properties. */
    val hasBuildKey: Boolean = false,
    val confirmReset: Boolean = false,
    val resetDone: Boolean = false,
)

sealed interface SettingsEvent {
    data class SetYear(val year: Int) : SettingsEvent
    data class SetStudyMode(val mode: StudyMode) : SettingsEvent
    data class ApiKeyChanged(val value: String) : SettingsEvent
    data object SaveApiKey : SettingsEvent
    data object RequestReset : SettingsEvent
    data object ConfirmReset : SettingsEvent
    data object DismissReset : SettingsEvent
    data object ChangePath : SettingsEvent
    data object Back : SettingsEvent
}

class SettingsViewModel(private val dataset: DatasetRepository, private val user: UserRepository) : ViewModel() {

    /** Screen-only state that isn't persisted. */
    private data class Local(val apiKeyDraft: String? = null, val apiKeySaved: Boolean = false, val confirmReset: Boolean = false, val resetDone: Boolean = false)

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<SettingsUiState> = combine(user.settings, local) { s, l ->
        SettingsUiState(
            loading = false,
            year = s.year,
            path = s.pathId?.let { dataset.path(it) },
            studyMode = s.studyMode,
            apiKeyDraft = l.apiKeyDraft ?: s.apiKeyOverride,
            apiKeySaved = l.apiKeySaved,
            hasBuildKey = BuildConfig.GEMINI_API_KEY.isNotBlank(),
            confirmReset = l.confirmReset,
            resetDone = l.resetDone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.SetYear -> viewModelScope.launch { user.updateSettings { it.copy(year = event.year) } }
            is SettingsEvent.SetStudyMode -> viewModelScope.launch { user.updateSettings { it.copy(studyMode = event.mode) } }
            is SettingsEvent.ApiKeyChanged -> local.update { it.copy(apiKeyDraft = event.value, apiKeySaved = false) }
            SettingsEvent.SaveApiKey -> viewModelScope.launch {
                val key = (local.value.apiKeyDraft ?: user.settings.first().apiKeyOverride).trim()
                user.updateSettings { it.copy(apiKeyOverride = key) }
                local.update { it.copy(apiKeyDraft = key, apiKeySaved = true) }
            }
            SettingsEvent.RequestReset -> local.update { it.copy(confirmReset = true) }
            SettingsEvent.DismissReset -> local.update { it.copy(confirmReset = false) }
            SettingsEvent.ConfirmReset -> viewModelScope.launch {
                user.resetProgress()
                local.update { it.copy(confirmReset = false, resetDone = true) }
            }
            SettingsEvent.ChangePath, SettingsEvent.Back -> Unit // handled by the route
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { appContainer().let { SettingsViewModel(it.dataset, it.user) } }
        }
    }
}
