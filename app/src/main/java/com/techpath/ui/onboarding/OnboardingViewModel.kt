package com.techpath.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.techpath.appContainer
import com.techpath.data.DatasetRepository
import com.techpath.data.PathInfo
import com.techpath.data.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep { YEAR, PATH, SKILLS }

data class OnboardingUiState(
    val loading: Boolean = true,
    val step: OnboardingStep = OnboardingStep.YEAR,
    /** Edit mode (opened from Roadmap/Settings to change path): starts at PATH and skips YEAR. */
    val isEdit: Boolean = false,
    val year: Int = 1,
    val pathQuery: String = "",
    /** Main paths with their beginner variants listed right after them. */
    val roles: List<PathInfo> = emptyList(),
    val skillPaths: List<PathInfo> = emptyList(),
    val selectedPathId: String? = null,
    val skillQuery: String = "",
    val skillOptions: List<String> = emptyList(),
    val knownSkills: Set<String> = emptySet(),
    val finished: Boolean = false,
)

sealed interface OnboardingEvent {
    data class SelectYear(val year: Int) : OnboardingEvent
    data class PathQueryChanged(val query: String) : OnboardingEvent
    data class SelectPath(val pathId: String) : OnboardingEvent
    data class SkillQueryChanged(val query: String) : OnboardingEvent
    data class ToggleSkill(val title: String) : OnboardingEvent
    data object Next : OnboardingEvent
    data object Back : OnboardingEvent
}

class OnboardingViewModel(
    private val dataset: DatasetRepository,
    private val user: UserRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState(isEdit = savedState.get<Boolean>("edit") ?: false))
    val uiState: StateFlow<OnboardingUiState> = _state.asStateFlow()
    private var allPaths: List<PathInfo> = emptyList()

    init {
        viewModelScope.launch {
            allPaths = dataset.paths()
            val settings = user.settings.first()
            _state.update {
                it.copy(
                    loading = false,
                    step = if (it.isEdit) OnboardingStep.PATH else OnboardingStep.YEAR,
                    year = settings.year,
                    selectedPathId = settings.pathId,
                    knownSkills = settings.knownSkills,
                ).withFilteredPaths()
            }
        }
    }

    fun onEvent(event: OnboardingEvent) {
        when (event) {
            is OnboardingEvent.SelectYear -> _state.update { it.copy(year = event.year) }
            is OnboardingEvent.PathQueryChanged -> _state.update { it.copy(pathQuery = event.query).withFilteredPaths() }
            is OnboardingEvent.SelectPath -> _state.update { it.copy(selectedPathId = event.pathId) }
            is OnboardingEvent.SkillQueryChanged -> _state.update { it.copy(skillQuery = event.query) }
            is OnboardingEvent.ToggleSkill -> _state.update {
                val k = it.knownSkills
                it.copy(knownSkills = if (event.title in k) k - event.title else k + event.title)
            }
            OnboardingEvent.Next -> next()
            OnboardingEvent.Back -> _state.update {
                when (it.step) {
                    OnboardingStep.SKILLS -> it.copy(step = OnboardingStep.PATH)
                    OnboardingStep.PATH -> if (it.isEdit) it.copy(finished = true) else it.copy(step = OnboardingStep.YEAR)
                    OnboardingStep.YEAR -> it
                }
            }
        }
    }

    private fun next() {
        val s = _state.value
        when (s.step) {
            OnboardingStep.YEAR -> _state.update { it.copy(step = OnboardingStep.PATH) }
            OnboardingStep.PATH -> {
                val pathId = s.selectedPathId ?: return
                viewModelScope.launch {
                    val titles = dataset.nodes(pathId).map { it.title }.distinct()
                    _state.update { it.copy(step = OnboardingStep.SKILLS, skillOptions = titles, skillQuery = "") }
                }
            }
            OnboardingStep.SKILLS -> viewModelScope.launch {
                user.updateSettings {
                    it.copy(onboarded = true, year = s.year, pathId = s.selectedPathId, knownSkills = s.knownSkills)
                }
                _state.update { it.copy(finished = true) }
            }
        }
    }

    private fun OnboardingUiState.withFilteredPaths(): OnboardingUiState {
        val q = pathQuery.trim().lowercase()
        val matches = allPaths.filter { q.isEmpty() || it.name.lowercase().contains(q) || it.id.contains(q) }
        // Nest beginner variants right under their parent.
        val ordered = matches.filter { !it.isBeginnerVariant }.flatMap { main ->
            listOf(main) + matches.filter { it.parent == main.id }
        } + matches.filter { v -> v.isBeginnerVariant && matches.none { it.id == v.parent } }
        return copy(roles = ordered.filter { it.isRole }, skillPaths = ordered.filter { !it.isRole })
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { OnboardingViewModel(appContainer().dataset, appContainer().user, createSavedStateHandle()) }
        }
    }
}
