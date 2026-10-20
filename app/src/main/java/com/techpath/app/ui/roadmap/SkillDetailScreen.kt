package com.techpath.app.ui.roadmap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.techpath.app.AppContainer
import com.techpath.app.data.Events
import com.techpath.app.data.RoadmapRepository
import com.techpath.app.data.db.SkillStatus
import com.techpath.app.ui.LoadingState
import com.techpath.app.ui.MessageState
import com.techpath.app.ui.ModeBadge
import com.techpath.app.ui.openUrl
import com.techpath.core.CareerPath
import com.techpath.core.Resource
import com.techpath.core.ResourceParser
import com.techpath.core.SkillNode
import com.techpath.core.StudyMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SkillUiState(
    val loading: Boolean = true,
    val node: SkillNode? = null,
    val career: CareerPath? = null,
    val mode: StudyMode = StudyMode.STATIC,
    val explaining: Boolean = false,
    val explanation: RoadmapRepository.Explanation? = null,
    val explainError: String? = null,
    val why: String? = null,
)

class SkillViewModel(private val c: AppContainer, private val path: String, private val nodeId: String) : ViewModel() {
    private val _state = MutableStateFlow(SkillUiState())
    val state: StateFlow<SkillUiState> = _state
    val status = c.roadmaps.observeStatus(path, nodeId)

    init {
        viewModelScope.launch {
            val kb = c.knowledgeBase.get()
            val mode = c.roadmaps.modeState().effective
            _state.update { it.copy(loading = false, node = kb.node(path, nodeId), career = kb.career(path), mode = mode) }
            c.logger.log(Events.SKILL_OPENED, path, nodeId)
            if (mode == StudyMode.AI) explain(false)
        }
        viewModelScope.launch {
            c.roadmaps.observeRoadmap(path).collect { r ->
                val why = r?.stages?.flatMap { it.items }?.firstOrNull { it.nodeId == nodeId }?.why?.takeIf { it.isNotBlank() }
                _state.update { it.copy(why = why) }
            }
        }
    }

    fun explain(force: Boolean) {
        _state.update { it.copy(explaining = true, explainError = null) }
        viewModelScope.launch {
            try {
                val e = c.roadmaps.explanation(path, nodeId, forceRefresh = force)
                _state.update { it.copy(explaining = false, explanation = e) }
            } catch (e: Exception) {
                _state.update { it.copy(explaining = false, explainError = e.message ?: "Couldn't load the explanation") }
            }
        }
    }

    fun setStatus(s: SkillStatus) = viewModelScope.launch { c.roadmaps.setStatus(path, nodeId, s) }
    fun resourceOpened(r: Resource) = c.logger.log(Events.RESOURCE_OPENED, path, nodeId, "${r.type}|${r.url}")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillDetailScreen(container: AppContainer, path: String, nodeId: String, onBack: () -> Unit) {
    val vm: SkillViewModel = viewModel(key = "skill-$path-$nodeId") { SkillViewModel(container, path, nodeId) }
    val s by vm.state.collectAsState()
    val status by vm.status.collectAsState(initial = SkillStatus.NOT_STARTED)
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.node?.title ?: "Skill", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        val node = s.node
        when {
            s.loading -> LoadingState(modifier = Modifier.padding(pad))
            node == null -> MessageState("Skill not found", "It may have been removed in a newer dataset.", modifier = Modifier.padding(pad))
            else -> LazyColumn(
                Modifier.padding(pad),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.career?.name.orEmpty(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            ModeBadge(s.mode)
                        }
                        Text(node.title, style = MaterialTheme.typography.headlineSmall)
                        s.why?.let { Text("Why now: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SkillStatus.entries.forEachIndexed { i, st ->
                                SegmentedButton(
                                    selected = status == st,
                                    onClick = { vm.setStatus(st) },
                                    shape = SegmentedButtonDefaults.itemShape(i, SkillStatus.entries.size),
                                ) { Text(st.label, maxLines = 1) }
                            }
                        }
                    }
                }

                if (s.mode == StudyMode.AI) {
                    val e = s.explanation
                    when {
                        s.explaining && e == null -> item {
                            Column { Text("Writing an explanation for you…", style = MaterialTheme.typography.bodyMedium); LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp)) }
                        }
                        e != null -> {
                            item { Section("What it means", e.whatItMeans) }
                            item { Section("What it involves", e.whatItInvolves) }
                            item { Section("Why it matters for ${s.career?.name ?: "this career"}", e.whyItMatters) }
                            item {
                                TextButton(onClick = { vm.explain(true) }, enabled = !s.explaining) {
                                    Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Regenerate explanation")
                                }
                            }
                        }
                        else -> item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(s.explainError ?: "No explanation yet.", style = MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick = { vm.explain(false) }) { Text("Try again") }
                                }
                            }
                        }
                    }
                    item { Section("From the roadmap dataset", node.description, muted = true) }
                } else {
                    item { Section("About this skill", node.description) }
                }

                item { Text("Learning resources", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                if (node.resources.isEmpty()) {
                    item {
                        Text("The dataset has no linked resources for this topic yet. Try the official documentation of the tools involved, or search for \"${node.title} tutorial\".",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    items(node.resources, key = { it.url }) { r ->
                        Card(
                            onClick = { vm.resourceOpened(r); openUrl(context, r.url) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(resourceIcon(r.type), null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(r.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(ResourceParser.label(r.type) + " · " + hostOf(r.url), style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.AutoMirrored.Outlined.OpenInNew, "Open link")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, body: String, muted: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (muted) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            body.split("\n").map { it.trim().removePrefix("- ").removePrefix("• ") }.filter { it.isNotEmpty() }.let { lines ->
                if (lines.size > 1) lines.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                else Text(body.trim(), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun resourceIcon(type: String): ImageVector = when (type) {
    "video" -> Icons.Outlined.OndemandVideo
    "course" -> Icons.Outlined.School
    "docs" -> Icons.Outlined.Description
    "opensource" -> Icons.Outlined.Code
    else -> Icons.AutoMirrored.Outlined.Article
}

private fun hostOf(url: String) = runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull() ?: url
