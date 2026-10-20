package com.techpath.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.techpath.app.AppContainer
import com.techpath.app.data.Events
import com.techpath.app.data.ModeState
import com.techpath.app.data.Profile
import com.techpath.app.ui.LoadingState
import com.techpath.app.ui.NoticeBanner
import com.techpath.core.CareerMatcher
import com.techpath.core.CareerPath
import com.techpath.core.SkillNode
import com.techpath.core.StudyMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingState(
    val step: Int = 0,
    val name: String = "",
    val year: Int = 1,
    val interest: String = "",
    val mode: ModeState? = null,
    val careers: List<CareerPath> = emptyList(),
    val matching: Boolean = false,
    val matches: List<CareerMatcher.Match> = emptyList(),
    val matchError: String? = null,
    val showAll: Boolean = false,
    val filter: String = "",
    val selectedPath: String? = null,
    val loadingSkills: Boolean = false,
    val suggestions: List<SkillNode> = emptyList(),
    val chipIds: Set<String> = emptySet(),
    val freeSkills: List<String> = emptyList(),
    val saving: Boolean = false,
)

class OnboardingViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state

    init {
        viewModelScope.launch {
            val kb = c.knowledgeBase.get()
            _state.update { it.copy(careers = kb.careers, mode = c.roadmaps.modeState()) }
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v.take(60)) }
    fun setYear(v: Int) = _state.update { it.copy(year = v) }
    fun setInterest(v: String) = _state.update { it.copy(interest = v.take(300)) }
    fun setFilter(v: String) = _state.update { it.copy(filter = v) }
    fun showAll() = _state.update { it.copy(showAll = true) }
    fun select(path: String) = _state.update { it.copy(selectedPath = path) }
    fun back() = _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }

    /** Step 0 -> 1: match the free-text interest to career paths. */
    fun toCareer() {
        _state.update { it.copy(step = 1, matching = true, matches = emptyList(), matchError = null) }
        viewModelScope.launch {
            val mode = c.roadmaps.modeState()
            _state.update { it.copy(mode = mode) }
            if (mode.effective == StudyMode.STATIC) {
                // Control condition: a plain list of every path, no matching.
                _state.update { it.copy(matching = false, showAll = true) }
                return@launch
            }
            val r = c.roadmaps.matchCareers(_state.value.interest)
            r.matches.firstOrNull()?.let { c.logger.log(Events.CAREER_MATCHED, it.career.id, detail = "direct=${r.direct != null};score=%.3f".format(it.score)) }
            _state.update {
                it.copy(
                    matching = false, matches = r.matches, matchError = r.error,
                    showAll = r.matches.isEmpty(),
                    selectedPath = r.direct?.id ?: it.selectedPath ?: r.matches.firstOrNull()?.career?.id,
                )
            }
        }
    }

    /** Step 1 -> 2: suggest skills from the chosen path's nodes. */
    fun toSkills() {
        val path = _state.value.selectedPath ?: return
        _state.update { it.copy(step = 2, loadingSkills = true) }
        viewModelScope.launch {
            val sug = c.roadmaps.suggestedSkills(path, _state.value.interest)
            _state.update { st -> st.copy(loadingSkills = false, suggestions = sug, chipIds = st.chipIds.filter { id -> sug.any { it.id == id } }.toSet()) }
        }
    }

    fun toggleChip(id: String) = _state.update { s -> s.copy(chipIds = if (id in s.chipIds) s.chipIds - id else s.chipIds + id) }
    fun addFreeSkill(v: String) {
        val t = v.trim().take(60)
        if (t.isNotEmpty()) _state.update { s -> if (s.freeSkills.any { it.equals(t, true) }) s else s.copy(freeSkills = s.freeSkills + t) }
    }
    fun removeFreeSkill(v: String) = _state.update { s -> s.copy(freeSkills = s.freeSkills - v) }

    fun finish() {
        val s = _state.value
        val path = s.selectedPath ?: return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            c.settings.ensureParticipantId()
            c.settings.saveProfile(Profile(s.name, s.year, s.interest, path, s.freeSkills.toSet(), s.chipIds))
            c.logger.log(Events.ONBOARDING_DONE, path, detail = "year=${s.year};chips=${s.chipIds.size};free_skills=${s.freeSkills.size}")
            c.settings.setOnboarded(true) // Root switches to the main screen; Roadmap generates on first open.
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(container: AppContainer) {
    val vm: OnboardingViewModel = viewModel { OnboardingViewModel(container) }
    val s by vm.state.collectAsState()
    BackHandler(enabled = s.step > 0) { vm.back() }

    Scaffold { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            LinearProgressIndicator(progress = { (s.step + 1) / 3f }, modifier = Modifier.fillMaxWidth())
            when (s.step) {
                0 -> AboutYouStep(s, vm, Modifier.weight(1f))
                1 -> CareerStep(s, vm, Modifier.weight(1f))
                else -> SkillsStep(s, vm, Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutYouStep(s: OnboardingState, vm: OnboardingViewModel, modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Welcome to TechPath", style = MaterialTheme.typography.headlineMedium)
        Text("Tell us a little about yourself and we'll build a skills roadmap for the tech career you want.",
            style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(s.name, vm::setName, label = { Text("Your name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Year of study", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { y ->
                FilterChip(selected = s.year == y, onClick = { vm.setYear(y) }, label = { Text("Year $y") })
            }
        }
        OutlinedTextField(
            s.interest, vm::setInterest,
            label = { Text("What kind of tech work interests you?") },
            placeholder = { Text("e.g. I want to build apps for banks") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Your name and answers stay on this phone.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = vm::toCareer, enabled = s.interest.isNotBlank() && s.careers.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(if (s.careers.isEmpty()) "Loading roadmaps…" else "Next")
        }
    }
}

@Composable
private fun CareerStep(s: OnboardingState, vm: OnboardingViewModel, modifier: Modifier) {
    Column(modifier.padding(horizontal = 20.dp)) {
        Spacer(Modifier.padding(6.dp))
        Text("Choose a career path", style = MaterialTheme.typography.headlineSmall)
        if (s.matching) {
            LoadingState("Matching \"${s.interest}\" to career paths…", Modifier.weight(1f))
            return@Column
        }
        val static = s.mode?.effective == StudyMode.STATIC
        LazyColumn(Modifier.weight(1f)) {
            if (s.mode?.isFallback == true) item { NoticeBanner(s.mode.reason.orEmpty(), Modifier.padding(vertical = 8.dp)) }
            s.matchError?.let { item { NoticeBanner(it, Modifier.padding(vertical = 8.dp)) } }
            if (!static && s.matches.isNotEmpty()) {
                item { Text("Best matches for your interest", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp)) }
                matchCards(s.matches, s.selectedPath, vm::select)
                if (!s.showAll) item { TextButton(onClick = vm::showAll) { Text("None of these? Browse all career paths") } }
            }
            if (s.showAll) {
                item { CareerFilterField(s.filter, vm::setFilter) }
                careerList(s.careers, s.filter, s.selectedPath, vm::select)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = vm::back, modifier = Modifier.weight(1f)) { Text("Back") }
            Button(onClick = vm::toSkills, enabled = s.selectedPath != null, modifier = Modifier.weight(1f)) { Text("Confirm") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkillsStep(s: OnboardingState, vm: OnboardingViewModel, modifier: Modifier) {
    var text by remember { mutableStateOf("") }
    val career = s.careers.firstOrNull { it.id == s.selectedPath }
    Column(modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Skills you already have", style = MaterialTheme.typography.headlineSmall)
        Text("Optional. Pick any you're comfortable with for ${career?.name ?: "this path"}, or type your own. " +
            "Known skills are marked done on your roadmap.", style = MaterialTheme.typography.bodyMedium)
        if (s.loadingSkills) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                s.suggestions.forEach { n ->
                    val sel = n.id in s.chipIds
                    FilterChip(
                        selected = sel, onClick = { vm.toggleChip(n.id) }, label = { Text(n.title) },
                        leadingIcon = if (sel) ({ Icon(Icons.Filled.Check, null) }) else null,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                text, { text = it }, label = { Text("Add another skill") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.addFreeSkill(text); text = "" }),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { vm.addFreeSkill(text); text = "" }, enabled = text.isNotBlank()) { Text("Add") }
        }
        if (s.freeSkills.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.freeSkills.forEach { sk ->
                    InputChip(selected = true, onClick = { vm.removeFreeSkill(sk) }, label = { Text(sk) },
                        trailingIcon = { Icon(Icons.Outlined.Close, "Remove") })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = vm::back, modifier = Modifier.weight(1f)) { Text("Back") }
            Button(onClick = vm::finish, enabled = !s.saving, modifier = Modifier.weight(1f)) { Text("Build my roadmap") }
        }
    }
}
