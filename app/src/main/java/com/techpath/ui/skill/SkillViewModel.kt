package com.techpath.ui.skill

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.techpath.ai.AiClient
import com.techpath.ai.Prompts
import com.techpath.appContainer
import com.techpath.data.DatasetRepository
import com.techpath.data.Link
import com.techpath.data.SkillNode
import com.techpath.data.StudyMode
import com.techpath.data.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SkillUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val title: String = "",
    val explanation: String = "",
    /** Always from the dataset, never from the model. */
    val links: List<Link> = emptyList(),
    /** AI mode was on but the explanation fell back to the dataset description. */
    val aiFallback: Boolean = false,
)

sealed interface SkillEvent {
    data class OpenLink(val url: String) : SkillEvent
    data object Back : SkillEvent
}

class SkillViewModel(
    private val dataset: DatasetRepository,
    private val user: UserRepository,
    private val ai: AiClient,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val pathId: String = checkNotNull(savedState["pathId"])
    private val nodeId: String = checkNotNull(savedState["nodeId"])

    private val _state = MutableStateFlow(SkillUiState())
    val uiState: StateFlow<SkillUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val node = runCatching { dataset.nodes(pathId).firstOrNull { it.id == nodeId } }.getOrNull()
            if (node == null) {
                _state.value = SkillUiState(loading = false, error = true)
                return@launch
            }
            _state.value = SkillUiState(loading = true, title = node.title, links = node.links)
            val (text, fellBack) = explain(node)
            _state.value = _state.value.copy(loading = false, explanation = text, aiFallback = fellBack)
        }
    }

    /** STATIC: dataset description. AI: cached or generated explanation, falling back to the description. */
    private suspend fun explain(node: SkillNode): Pair<String, Boolean> {
        if (user.settings.first().studyMode == StudyMode.STATIC) return node.description to false
        user.cachedExplanation(pathId, nodeId)?.let { return it to false }
        return try {
            val text = ai.complete(Prompts.explainSystem(node.description), Prompts.explainUser(node.title, pathId), json = false).trim()
            user.cacheExplanation(pathId, nodeId, text)
            text to false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            node.description to true
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { appContainer().let { SkillViewModel(it.dataset, it.user, it.ai, createSavedStateHandle()) } }
        }
    }
}
