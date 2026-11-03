package com.techpath.ui.opportunities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.techpath.appContainer
import com.techpath.data.ApplicationStatus
import com.techpath.data.DatasetRepository
import com.techpath.data.Opportunity
import com.techpath.data.OpportunityType
import com.techpath.data.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class OpportunityUi(
    val opportunity: Opportunity,
    val deadline: LocalDate?,
    /** Null = not saved. */
    val status: ApplicationStatus?,
    val expired: Boolean,
)

data class OpportunitiesUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    /** Null = all types. */
    val filter: OpportunityType? = null,
    val items: List<OpportunityUi> = emptyList(),
)

sealed interface OpportunitiesEvent {
    data class Filter(val type: OpportunityType?) : OpportunitiesEvent
    data class ToggleSaved(val id: String) : OpportunitiesEvent
    data class SetStatus(val id: String, val status: ApplicationStatus) : OpportunitiesEvent
    data class Open(val url: String) : OpportunitiesEvent
}

class OpportunitiesViewModel(dataset: DatasetRepository, private val user: UserRepository) : ViewModel() {

    private val filter = MutableStateFlow<OpportunityType?>(null)
    private val listings = flow { emit(runCatching { dataset.opportunities() }) }

    val uiState: StateFlow<OpportunitiesUiState> =
        combine(listings, user.opportunityStatus, filter) { result, statuses, type ->
            val all = result.getOrElse { return@combine OpportunitiesUiState(loading = false, error = true, filter = type) }
            val today = LocalDate.now()
            val items = all
                .filter { type == null || it.type == type }
                .map { o ->
                    val deadline = o.deadline?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    OpportunityUi(o, deadline, statuses[o.id], expired = deadline != null && deadline.isBefore(today))
                }
                .sortedWith(compareBy<OpportunityUi> { it.expired }.thenBy { it.deadline ?: LocalDate.MAX })
            OpportunitiesUiState(loading = false, filter = type, items = items)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OpportunitiesUiState())

    fun onEvent(event: OpportunitiesEvent) {
        when (event) {
            is OpportunitiesEvent.Filter -> filter.value = event.type
            is OpportunitiesEvent.ToggleSaved -> viewModelScope.launch {
                val saved = uiState.value.items.firstOrNull { it.opportunity.id == event.id }?.status != null
                user.setOpportunityStatus(event.id, if (saved) null else ApplicationStatus.SAVED)
            }
            is OpportunitiesEvent.SetStatus -> viewModelScope.launch { user.setOpportunityStatus(event.id, event.status) }
            is OpportunitiesEvent.Open -> Unit // handled by the route
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { appContainer().let { OpportunitiesViewModel(it.dataset, it.user) } }
        }
    }
}
