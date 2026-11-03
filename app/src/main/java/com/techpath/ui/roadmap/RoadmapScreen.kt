package com.techpath.ui.roadmap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.techpath.R
import com.techpath.data.PathInfo
import com.techpath.ui.components.EmptyState
import com.techpath.ui.components.ErrorState
import com.techpath.ui.components.LoadingState
import com.techpath.ui.components.ProgressBar
import com.techpath.ui.components.SkillRow
import com.techpath.ui.components.StageHeader
import com.techpath.ui.components.pathLabel
import com.techpath.ui.theme.Dimens
import com.techpath.ui.theme.TechPathTheme

@Composable
fun RoadmapRoute(
    onOpenSkill: (pathId: String, nodeId: String) -> Unit,
    onChangePath: () -> Unit,
    viewModel: RoadmapViewModel = viewModel(factory = RoadmapViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RoadmapScreen(state) { event ->
        when (event) {
            is RoadmapEvent.OpenSkill -> state.path?.let { onOpenSkill(it.id, event.nodeId) }
            RoadmapEvent.ChangePath -> onChangePath()
            else -> viewModel.onEvent(event)
        }
    }
}

@Composable
fun RoadmapScreen(state: RoadmapUiState, onEvent: (RoadmapEvent) -> Unit) {
    when {
        state.loading -> LoadingState()
        state.error -> ErrorState(stringResource(R.string.error_roadmap), onRetry = { onEvent(RoadmapEvent.Retry) })
        state.noPath -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyState(stringResource(R.string.empty_no_path), Modifier.weight(1f))
            Button(onClick = { onEvent(RoadmapEvent.ChangePath) }, modifier = Modifier.padding(Dimens.spaceL)) {
                Text(stringResource(R.string.action_choose_path))
            }
        }
        else -> LazyColumn(contentPadding = PaddingValues(Dimens.spaceM)) {
            item { RoadmapHeader(state, onEvent) }
            state.stages.forEach { stage ->
                item(key = "stage-${stage.number}") {
                    StageHeader(
                        title = stage.title ?: stringResource(R.string.stage_default_title, stage.number),
                        why = stage.why,
                        done = stage.skills.count { it.done || it.known },
                        total = stage.skills.size,
                    )
                }
                items(stage.skills, key = { it.id }) { skill ->
                    SkillRow(
                        title = skill.title,
                        done = skill.done,
                        known = skill.known,
                        onToggle = { onEvent(RoadmapEvent.ToggleDone(skill.id)) },
                        onClick = { onEvent(RoadmapEvent.OpenSkill(skill.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RoadmapHeader(state: RoadmapUiState, onEvent: (RoadmapEvent) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.path?.let { pathLabel(it) }.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onEvent(RoadmapEvent.ChangePath) }) { Text(stringResource(R.string.action_change_path)) }
        }
        ProgressBar(state.done, state.total)
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS), verticalAlignment = Alignment.CenterVertically) {
            if (state.aiFallback) {
                Text(
                    stringResource(R.string.notice_ai_fallback),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            if (state.aiMode) {
                TextButton(onClick = { onEvent(RoadmapEvent.Regenerate) }) { Text(stringResource(R.string.action_regenerate)) }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RoadmapScreenPreview() {
    TechPathTheme {
        RoadmapScreen(
            RoadmapUiState(
                loading = false,
                path = PathInfo("backend", "Backend", "role", null, 3),
                stages = listOf(
                    StageUi(1, "Foundations", "How the web works.", listOf(SkillUi("a", "Internet", done = true, known = false), SkillUi("b", "Git", done = false, known = true))),
                    StageUi(2, null, null, listOf(SkillUi("c", "Relational databases", done = false, known = false))),
                ),
                done = 2,
                total = 3,
                aiMode = true,
                aiFallback = true,
            ),
            onEvent = {},
        )
    }
}
