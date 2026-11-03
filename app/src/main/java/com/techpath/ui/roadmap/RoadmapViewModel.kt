package com.techpath.ui.roadmap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.techpath.ai.RoadmapBuilder
import com.techpath.appContainer
import com.techpath.data.DatasetRepository
import com.techpath.data.PathInfo
import com.techpath.data.Roadmap
import com.techpath.data.SkillNode
import com.techpath.data.StudyMode
import com.techpath.data.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SkillUi(val id: String, val title: String, val done: Boolean, val known: Boolean)

data class StageUi(val number: Int, val title: String?, val why: String?, val skills: List<SkillUi>)

data class RoadmapUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val noPath: Boolean = false,
    val path: PathInfo? = null,
    val stages: List<StageUi> = emptyList(),
    val done: Int = 0,
    val total: Int = 0,
    val aiMode: Boolean = false,
    /** AI mode was on but the roadmap fell back to STATIC. */
    val aiFallback: Boolean = false,
)

sealed interface RoadmapEvent {
    data class ToggleDone(val nodeId: String) : RoadmapEvent
    data class OpenSkill(val nodeId: String) : RoadmapEvent
    data object ChangePath : RoadmapEvent
    data object Regenerate : RoadmapEvent
    data object Retry : RoadmapEvent
}

class RoadmapViewModel(
    private val dataset: DatasetRepository,
    private val user: UserRepository,
    private val builder: RoadmapBuilder,
) : ViewModel() {

    private sealed interface Load {
        data object Loading : Load
        data object NoPath : Load
        data object Error : Load
        data class Ready(val path: PathInfo, val nodes: Map<String, SkillNode>, val roadmap: Roadmap) : Load
    }

    private data class Inputs(val pathId: String?, val year: Int, val known: Set<String>, val mode: StudyMode)

    private val load = MutableStateFlow<Load>(Load.Loading)
    private var inputs: Inputs? = null
    private var job: Job? = null

    val uiState: StateFlow<RoadmapUiState> = combine(load, user.progress) { load, progress ->
        when (load) {
            Load.Loading -> RoadmapUiState(loading = true)
            Load.NoPath -> RoadmapUiState(loading = false, noPath = true)
            Load.Error -> RoadmapUiState(loading = false, error = true)
            is Load.Ready -> load.toUi(progress[load.path.id].orEmpty())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoadmapUiState())

    init {
        viewModelScope.launch {
            user.settings
                .map { Inputs(it.pathId, it.year, it.knownSkills, it.studyMode) }
                .distinctUntilChanged()
                .collect { inputs = it; reload(force = false) }
        }
    }

    fun onEvent(event: RoadmapEvent) {
        when (event) {
            is RoadmapEvent.ToggleDone -> (load.value as? Load.Ready)?.let { ready ->
                viewModelScope.launch { user.toggleDone(ready.path.id, event.nodeId) }
            }
            RoadmapEvent.Regenerate -> reload(force = true)
            RoadmapEvent.Retry -> reload(force = false)
            is RoadmapEvent.OpenSkill, RoadmapEvent.ChangePath -> Unit // handled by the route (navigation)
        }
    }

    /** [force] skips the AI cache ("Regenerate"). */
    private fun reload(force: Boolean) {
        val inp = inputs ?: return
        job?.cancel()
        job = viewModelScope.launch {
            load.value = Load.Loading
            load.value = try {
                build(inp, force)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Load.Error
            }
        }
    }

    private suspend fun build(inp: Inputs, force: Boolean): Load {
        val pathId = inp.pathId ?: return Load.NoPath
        val path = dataset.path(pathId) ?: return Load.Error
        val nodes = dataset.nodes(pathId)
        val roadmap = if (inp.mode == StudyMode.AI) {
            val key = builder.cacheKey(pathId, inp.year, inp.known)
            (if (force) null else user.cachedRoadmap(key))
                ?: builder.build(path, nodes, inp.year, inp.known, StudyMode.AI).also {
                    if (it.source == StudyMode.AI) user.cacheRoadmap(key, it)
                }
        } else {
            builder.build(path, nodes, inp.year, inp.known, StudyMode.STATIC)
        }
        return Load.Ready(path, nodes.associateBy { it.id }, roadmap)
    }

    private fun Load.Ready.toUi(ticked: Set<String>): RoadmapUiState {
        val stages = roadmap.stages.mapIndexed { i, stage ->
            StageUi(
                number = i + 1,
                title = stage.title,
                why = stage.why,
                skills = stage.nodeIds.mapNotNull { id ->
                    nodes[id]?.let { SkillUi(id, it.title, done = id in ticked, known = id in roadmap.knownNodeIds) }
                },
            )
        }
        val all = stages.flatMap { it.skills }
        return RoadmapUiState(
            loading = false,
            path = path,
            stages = stages,
            done = all.count { it.done || it.known },
            total = all.size,
            aiMode = inputs?.mode == StudyMode.AI,
            aiFallback = roadmap.aiFailed,
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { appContainer().let { RoadmapViewModel(it.dataset, it.user, it.roadmapBuilder) } }
        }
    }
}
