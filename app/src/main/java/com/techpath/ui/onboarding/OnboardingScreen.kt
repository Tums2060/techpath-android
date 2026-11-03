package com.techpath.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.techpath.R
import com.techpath.data.PathInfo
import com.techpath.ui.components.EmptyState
import com.techpath.ui.components.LoadingState
import com.techpath.ui.components.SkillRow
import com.techpath.ui.components.pathLabel
import com.techpath.ui.theme.Dimens
import com.techpath.ui.theme.TechPathTheme

@Composable
fun OnboardingRoute(onDone: () -> Unit, viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onDone() }
    BackHandler(enabled = state.step != OnboardingStep.YEAR) { viewModel.onEvent(OnboardingEvent.Back) }
    OnboardingScreen(state, viewModel::onEvent)
}

@Composable
fun OnboardingScreen(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    Scaffold(
        bottomBar = {
            if (!state.loading) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(Dimens.spaceM),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS),
                ) {
                    val showBack = state.step == OnboardingStep.SKILLS || (state.step == OnboardingStep.PATH)
                    if (showBack) {
                        OutlinedButton(onClick = { onEvent(OnboardingEvent.Back) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.action_back))
                        }
                    }
                    Button(
                        onClick = { onEvent(OnboardingEvent.Next) },
                        enabled = state.step != OnboardingStep.PATH || state.selectedPathId != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(if (state.step == OnboardingStep.SKILLS) R.string.action_finish else R.string.action_next))
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = Dimens.spaceM)) {
            if (state.loading) {
                LoadingState()
                return@Column
            }
            when (state.step) {
                OnboardingStep.YEAR -> YearStep(state.year, onEvent)
                OnboardingStep.PATH -> PathStep(state, onEvent)
                OnboardingStep.SKILLS -> SkillsStep(state, onEvent)
            }
        }
    }
}

@Composable
private fun StepTitle(title: String, subtitle: String? = null) {
    Spacer(Modifier.height(Dimens.spaceL))
    Text(title, style = MaterialTheme.typography.headlineSmall)
    if (subtitle != null) {
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(Dimens.spaceM))
}

@Composable
private fun YearStep(year: Int, onEvent: (OnboardingEvent) -> Unit) {
    StepTitle(stringResource(R.string.onboarding_welcome), stringResource(R.string.onboarding_year_question))
    YearChips(year) { onEvent(OnboardingEvent.SelectYear(it)) }
}

/** Year 1–4 picker; also used by Settings. */
@Composable
fun YearChips(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
        (1..4).forEach { y ->
            FilterChip(
                selected = y == selected,
                onClick = { onSelect(y) },
                label = { Text(stringResource(R.string.year_format, y)) },
            )
        }
    }
}

@Composable
private fun PathStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    StepTitle(stringResource(R.string.onboarding_path_question))
    OutlinedTextField(
        value = state.pathQuery,
        onValueChange = { onEvent(OnboardingEvent.PathQueryChanged(it)) },
        label = { Text(stringResource(R.string.search_paths)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.roles.isEmpty() && state.skillPaths.isEmpty()) {
        EmptyState(stringResource(R.string.empty_search))
        return
    }
    LazyColumn(contentPadding = PaddingValues(vertical = Dimens.spaceS)) {
        pathSection(R.string.paths_roles, state.roles, state.selectedPathId, onEvent)
        pathSection(R.string.paths_skills, state.skillPaths, state.selectedPathId, onEvent)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.pathSection(
    header: Int,
    paths: List<PathInfo>,
    selectedId: String?,
    onEvent: (OnboardingEvent) -> Unit,
) {
    if (paths.isEmpty()) return
    item(key = "header-$header") {
        Text(
            stringResource(header),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = Dimens.spaceM, bottom = Dimens.spaceXs),
        )
    }
    items(paths, key = { it.id }) { path ->
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(selected = path.id == selectedId, role = Role.RadioButton) { onEvent(OnboardingEvent.SelectPath(path.id)) }
                .padding(start = if (path.isBeginnerVariant) Dimens.indent else Dimens.spaceXs, top = Dimens.spaceXs, bottom = Dimens.spaceXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = path.id == selectedId, onClick = null)
            Column(Modifier.padding(start = Dimens.spaceS)) {
                Text(pathLabel(path), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.path_skill_count, path.nodeCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SkillsStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit) {
    StepTitle(stringResource(R.string.onboarding_skills_question), stringResource(R.string.onboarding_skills_hint))
    OutlinedTextField(
        value = state.skillQuery,
        onValueChange = { onEvent(OnboardingEvent.SkillQueryChanged(it)) },
        label = { Text(stringResource(R.string.search_skills)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    val q = state.skillQuery.trim()
    val options = state.skillOptions.filter { q.isEmpty() || it.contains(q, ignoreCase = true) }
    if (options.isEmpty()) {
        EmptyState(stringResource(R.string.empty_search))
        return
    }
    LazyColumn(contentPadding = PaddingValues(vertical = Dimens.spaceS)) {
        items(options, key = { it }) { title ->
            SkillRow(title, done = title in state.knownSkills, onToggle = { onEvent(OnboardingEvent.ToggleSkill(title)) })
        }
    }
}

private val previewPaths = listOf(
    PathInfo("backend", "Backend", "role", null, 155),
    PathInfo("backend-beginner", "Backend", "role", "backend", 26),
    PathInfo("python", "Python", "skill", null, 120),
)

@Preview(showBackground = true)
@Composable
private fun OnboardingPathPreview() {
    TechPathTheme {
        OnboardingScreen(
            OnboardingUiState(
                loading = false,
                step = OnboardingStep.PATH,
                roles = previewPaths.take(2),
                skillPaths = previewPaths.drop(1).takeLast(1),
                selectedPathId = "backend",
            ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingSkillsPreview() {
    TechPathTheme {
        OnboardingScreen(
            OnboardingUiState(
                loading = false,
                step = OnboardingStep.SKILLS,
                skillOptions = listOf("Internet", "HTTP", "Git"),
                knownSkills = setOf("Git"),
            ),
            onEvent = {},
        )
    }
}
