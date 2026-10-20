package com.techpath.app.ui.roadmap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.techpath.app.AppContainer
import com.techpath.app.data.Events
import com.techpath.app.data.ModeState
import com.techpath.app.ui.LoadingState
import com.techpath.app.ui.NoticeBanner
import com.techpath.app.ui.onboarding.CareerFilterField
import com.techpath.app.ui.onboarding.careerList
import com.techpath.app.ui.onboarding.matchCards
import com.techpath.core.CareerMatcher
import com.techpath.core.CareerPath
import com.techpath.core.StudyMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PickerState(
    val careers: List<CareerPath> = emptyList(),
    val current: String? = null,
    val selected: String? = null,
    val interest: String = "",
    val mode: ModeState? = null,
    val matching: Boolean = false,
    val matches: List<CareerMatcher.Match> = emptyList(),
    val error: String? = null,
    val filter: String = "",
    val saving: Boolean = false,
)

class CareerPickerViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(PickerState())
    val state: StateFlow<PickerState> = _state

    init {
        viewModelScope.launch {
            val kb = c.knowledgeBase.get()
            val s = c.settings.current()
            _state.update {
                it.copy(careers = kb.careers, current = s.profile.careerPath, selected = s.profile.careerPath,
                    interest = s.profile.interest, mode = c.roadmaps.modeState())
            }
        }
    }

    fun setInterest(v: String) = _state.update { it.copy(interest = v.take(300)) }
    fun setFilter(v: String) = _state.update { it.copy(filter = v) }
    fun select(id: String) = _state.update { it.copy(selected = id) }

    fun match() {
        val q = _state.value.interest
        if (q.isBlank()) return
        _state.update { it.copy(matching = true, error = null) }
        viewModelScope.launch {
            val r = c.roadmaps.matchCareers(q)
            _state.update { it.copy(matching = false, matches = r.matches, error = r.error, selected = r.matches.firstOrNull()?.career?.id ?: it.selected) }
        }
    }

    fun confirm(onDone: () -> Unit) {
        val sel = _state.value.selected ?: return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val s = c.settings.current()
            c.settings.saveProfile(s.profile.copy(careerPath = sel, interest = _state.value.interest.ifBlank { s.profile.interest }))
            c.logger.log(Events.CAREER_MATCHED, sel, detail = "switched_from=${s.profile.careerPath}")
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CareerPickerScreen(container: AppContainer, onDone: () -> Unit) {
    val vm: CareerPickerViewModel = viewModel { CareerPickerViewModel(container) }
    val s by vm.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Switch career path") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(16.dp)) {
                Button(onClick = { vm.confirm(onDone) }, enabled = s.selected != null && !s.saving, modifier = Modifier.fillMaxWidth()) {
                    Text(if (s.selected == s.current) "Keep this path" else "Use this path")
                }
            }
        },
    ) { pad ->
        if (s.careers.isEmpty()) {
            LoadingState(modifier = Modifier.padding(pad)); return@Scaffold
        }
        val ai = s.mode?.effective == StudyMode.AI
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(horizontal = 16.dp)) {
            if (ai) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            s.interest, vm::setInterest, label = { Text("Describe the work you want to do") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { vm.match() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = vm::match, enabled = s.interest.isNotBlank() && !s.matching) { Text("Find matching paths") }
                        if (s.matching) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                s.error?.let { e -> item { NoticeBanner(e, Modifier.padding(vertical = 8.dp)) } }
                if (s.matches.isNotEmpty()) {
                    item { Text("Top matches", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp)) }
                    matchCards(s.matches, s.selected, vm::select)
                }
                item { Text("All career paths", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp)) }
            } else if (s.mode?.isFallback == true) {
                item { NoticeBanner(s.mode?.reason.orEmpty(), Modifier.padding(vertical = 8.dp)) }
            }
            item { CareerFilterField(s.filter, vm::setFilter) }
            careerList(s.careers, s.filter, s.selected, vm::select)
        }
    }
}
