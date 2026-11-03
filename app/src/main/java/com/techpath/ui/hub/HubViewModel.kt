package com.techpath.ui.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.techpath.appContainer
import com.techpath.data.Article
import com.techpath.data.DatasetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HubUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val articles: List<Article> = emptyList(),
    /** The guide being read, or null for the list. */
    val openArticle: Article? = null,
    val articleBlocks: List<MdBlock> = emptyList(),
)

sealed interface HubEvent {
    data class Open(val article: Article) : HubEvent
    data object Close : HubEvent
}

class HubViewModel(private val dataset: DatasetRepository) : ViewModel() {
    private val _state = MutableStateFlow(HubUiState())
    val uiState: StateFlow<HubUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = runCatching { dataset.articles() }
                .fold({ HubUiState(loading = false, articles = it) }, { HubUiState(loading = false, error = true) })
        }
    }

    fun onEvent(event: HubEvent) {
        when (event) {
            is HubEvent.Open -> viewModelScope.launch {
                val blocks = runCatching { parseMarkdown(dataset.article(event.article.file)) }.getOrDefault(emptyList())
                _state.update { it.copy(openArticle = event.article, articleBlocks = blocks) }
            }
            HubEvent.Close -> _state.update { it.copy(openArticle = null, articleBlocks = emptyList()) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { HubViewModel(appContainer().dataset) }
        }
    }
}
