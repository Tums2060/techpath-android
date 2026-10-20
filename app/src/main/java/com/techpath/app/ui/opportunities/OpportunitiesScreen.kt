package com.techpath.app.ui.opportunities

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.techpath.app.AppContainer
import com.techpath.app.data.Events
import com.techpath.app.data.Opportunity
import com.techpath.app.data.db.ApplicationStatus
import com.techpath.app.ui.LoadingState
import com.techpath.app.ui.MessageState
import com.techpath.app.ui.NoticeBanner
import com.techpath.app.ui.openUrl
import com.techpath.core.Relevance
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

enum class DeadlineFilter(val label: String, val days: Long?) { ANY("Any deadline", null), D30("Closes in 30 days", 30), D90("Closes in 90 days", 90) }

data class Filters(
    val type: String? = null,
    val field: String? = null,
    val location: String? = null,
    val deadline: DeadlineFilter = DeadlineFilter.ANY,
    val savedOnly: Boolean = false,
    val includeClosed: Boolean = false,
)

data class Listing(val opp: Opportunity, val relevance: Int?, val status: ApplicationStatus?, val fieldName: String)

data class OppUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val isSample: Boolean = true,
    val all: List<Listing> = emptyList(),
    val filters: Filters = Filters(),
) {
    val today: LocalDate get() = LocalDate.now()
    val shown: List<Listing>
        get() = all.filter { l ->
            val d = l.opp.deadlineDate
            (filters.type == null || l.opp.type == filters.type) &&
                (filters.field == null || l.opp.field == filters.field) &&
                (filters.location == null || l.opp.location == filters.location) &&
                (!filters.savedOnly || l.status != null) &&
                (filters.includeClosed || d == null || !d.isBefore(today)) &&
                (filters.deadline.days == null || (d != null && ChronoUnit.DAYS.between(today, d) in 0..filters.deadline.days))
        }.sortedWith(compareByDescending<Listing> { it.relevance ?: -1 }.thenBy { it.opp.deadline })
}

@OptIn(ExperimentalCoroutinesApi::class)
class OpportunitiesViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(OppUiState())
    val state: StateFlow<OppUiState> = _state

    init {
        viewModelScope.launch {
            try {
                val kb = c.knowledgeBase.get()
                val listings = c.opportunities.source.listings()
                val pathFlow = c.settings.settings.map { it.profile.careerPath }.distinctUntilChanged()
                pathFlow.flatMapLatest { path ->
                    val roadmapFlow = if (path == null) kotlinx.coroutines.flow.flowOf(null) else c.roadmaps.observeRoadmap(path)
                    combine(roadmapFlow, c.opportunities.saved) { r, saved -> Triple(path, r, saved) }
                }.collect { (path, roadmap, saved) ->
                    val student = c.roadmaps.studentVector(path, roadmap)
                    val items = listings.map { o ->
                        val v = o.vector
                        val rel = if (student != null && v != null && v.size == student.size) Relevance.percent(student, v) else null
                        Listing(o, rel, saved[o.id], kb.career(o.field)?.name ?: o.field)
                    }
                    _state.update { it.copy(loading = false, all = items, isSample = c.opportunities.source.isSample) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Couldn't load opportunities") }
            }
        }
    }

    fun setFilters(f: Filters) = _state.update { it.copy(filters = f) }

    fun setStatus(l: Listing, status: ApplicationStatus?) = viewModelScope.launch {
        c.opportunities.setStatus(l.opp.id, status)
        when {
            status == ApplicationStatus.SAVED && l.status == null -> c.logger.log(Events.OPPORTUNITY_SAVED, l.opp.field, l.opp.id, "relevance=${l.relevance}")
            status != null -> c.logger.log(Events.OPPORTUNITY_STATUS, l.opp.field, l.opp.id, status.name)
            else -> c.logger.log(Events.OPPORTUNITY_STATUS, l.opp.field, l.opp.id, "UNSAVED")
        }
    }

    fun linkOpened(l: Listing) = c.logger.log(Events.OPPORTUNITY_LINK, l.opp.field, l.opp.id)
}

private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OpportunitiesScreen(container: AppContainer, onOpenSettings: () -> Unit) {
    val vm: OpportunitiesViewModel = viewModel { OpportunitiesViewModel(container) }
    val s by vm.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Opportunities") },
                actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "Settings") } },
            )
        },
    ) { pad ->
        when {
            s.loading -> LoadingState(modifier = Modifier.padding(pad))
            s.error != null -> MessageState("Couldn't load opportunities", s.error, modifier = Modifier.padding(pad))
            else -> {
                val shown = s.shown
                LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (s.isSample) item {
                        NoticeBanner(
                            "Sample data: these listings and organisations are fictional, for the TechPath study only. " +
                                "TechPath never submits applications; links open the organisation's website.",
                            icon = Icons.Outlined.Science,
                        )
                    }
                    item { FilterBar(s, vm::setFilters) }
                    if (shown.isEmpty()) item {
                        MessageState("No opportunities match these filters", "Try clearing a filter.",
                            actionLabel = "Clear filters", onAction = { vm.setFilters(Filters()) })
                    }
                    items(shown, key = { it.opp.id }) { l ->
                        OpportunityCard(l, s.today, onStatus = { st -> vm.setStatus(l, st) }, onApply = { vm.linkOpened(l); openUrl(context, l.opp.applyUrl) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterBar(s: OppUiState, onChange: (Filters) -> Unit) {
    val f = s.filters
    val types = s.all.map { it.opp.type }.distinct().sorted()
    val fields = s.all.map { it.opp.field to it.fieldName }.distinct().sortedBy { it.second }
    val locations = s.all.map { it.opp.location }.distinct().sorted()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = f.type == null, onClick = { onChange(f.copy(type = null)) }, label = { Text("All types") })
            types.forEach { t -> FilterChip(selected = f.type == t, onClick = { onChange(f.copy(type = if (f.type == t) null else t)) }, label = { Text(t) }) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dropdown(f.field?.let { id -> fields.firstOrNull { it.first == id }?.second } ?: "Any field",
                listOf<Pair<String?, String>>(null to "Any field") + fields) { onChange(f.copy(field = it)) }
            Dropdown(f.location ?: "Any location", listOf<Pair<String?, String>>(null to "Any location") + locations.map { it to it }) { onChange(f.copy(location = it)) }
            Dropdown(f.deadline.label, DeadlineFilter.entries.map { it.name to it.label }) { onChange(f.copy(deadline = DeadlineFilter.valueOf(it ?: "ANY"))) }
            FilterChip(selected = f.savedOnly, onClick = { onChange(f.copy(savedOnly = !f.savedOnly)) }, label = { Text("Saved only") })
            FilterChip(selected = f.includeClosed, onClick = { onChange(f.copy(includeClosed = !f.includeClosed)) }, label = { Text("Show closed") })
        }
    }
}

@Composable
private fun Dropdown(label: String, options: List<Pair<String?, String>>, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { open = true }, label = { Text(label) })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onSelect(value) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpportunityCard(l: Listing, today: LocalDate, onStatus: (ApplicationStatus?) -> Unit, onApply: () -> Unit) {
    val o = l.opp
    val d = o.deadlineDate
    val closed = d != null && d.isBefore(today)
    var menu by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(o.title, style = MaterialTheme.typography.titleMedium)
                    Text(o.organisation, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = { onStatus(if (l.status == null) ApplicationStatus.SAVED else null) }) {
                    if (l.status != null) Icon(Icons.Filled.Bookmark, "Unsave", tint = MaterialTheme.colorScheme.primary)
                    else Icon(Icons.Outlined.BookmarkBorder, "Save")
                }
            }
            Text("${o.type} · ${l.fieldName} · ${o.location}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                when {
                    d == null -> "Deadline: ${o.deadline}"
                    closed -> "Closed on ${d.format(dateFmt)}"
                    else -> "Deadline: ${d.format(dateFmt)} (${ChronoUnit.DAYS.between(today, d)} days left)"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (closed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (o.description.isNotBlank()) Text(o.description, style = MaterialTheme.typography.bodySmall)
            Text("Requirements", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                o.requirements.forEach { r -> AssistChip(onClick = {}, label = { Text(r, style = MaterialTheme.typography.labelSmall) }) }
            }
            l.relevance?.let { Text("Relevance to your roadmap: $it%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApply) {
                    Text("Apply on website"); Spacer(Modifier.width(6.dp)); Icon(Icons.AutoMirrored.Outlined.OpenInNew, null)
                }
                if (l.status != null) {
                    Box {
                        OutlinedButton(onClick = { menu = true }) { Text("Status: ${l.status.label}") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            ApplicationStatus.entries.forEach { st ->
                                DropdownMenuItem(text = { Text(st.label) }, onClick = { menu = false; onStatus(st) })
                            }
                        }
                    }
                }
            }
        }
    }
}
