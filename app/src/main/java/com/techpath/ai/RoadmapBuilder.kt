package com.techpath.ai

import com.techpath.data.PathInfo
import com.techpath.data.Roadmap
import com.techpath.data.SkillNode
import com.techpath.data.Stage
import com.techpath.data.StudyMode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlin.math.ceil

/**
 * Builds a roadmap in three steps:
 *  1. Retrieval (on-device): skill gap against known skills, then cap the remaining nodes.
 *  2. Ordering: STATIC = file order in stages of ~8; AI = LLM-proposed stages.
 *  3. Validation + fallback: the LLM may only reorder retrieved ids; anything else falls back to STATIC.
 */
class RoadmapBuilder(private val ai: AiClient, private val json: Json) {

    companion object {
        /** Maximum number of not-yet-known nodes kept for one roadmap. */
        const val MAX_RETRIEVED_NODES = 120
        /** Target stage size in STATIC mode. */
        const val STATIC_STAGE_SIZE = 8
        /** How many of a path's most frequent title words count as its core topics. */
        private const val CORE_TOPIC_COUNT = 25
        private val STOPWORDS = setOf(
            "and", "the", "for", "with", "of", "to", "in", "on", "a", "an", "vs", "how", "what",
            "using", "your", "from", "into", "basics", "introduction", "other",
        )
    }

    /** The AI roadmap cache is valid only for the same path, year and known skills. */
    fun cacheKey(pathId: String, year: Int, knownSkills: Set<String>): String =
        "$pathId|$year|" + knownSkills.map(::normalise).sorted().joinToString(",")

    data class Retrieval(val nodes: List<SkillNode>, val knownNodeIds: Set<String>)

    suspend fun build(
        path: PathInfo,
        allNodes: List<SkillNode>,
        year: Int,
        knownSkills: Set<String>,
        mode: StudyMode,
    ): Roadmap {
        val retrieval = retrieve(allNodes, knownSkills)
        if (mode == StudyMode.STATIC) return staticRoadmap(path.id, retrieval, aiFailed = false)

        val user = Prompts.roadmapUser(year, path.id, knownSkills, retrieval.nodes)
        val stages = try {
            val first = ai.complete(Prompts.ROADMAP_SYSTEM, user, json = true)
            validate(first, retrieval.nodes)
                ?: validate(ai.complete(Prompts.ROADMAP_SYSTEM, Prompts.roadmapRepair(first), json = true), retrieval.nodes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // no key, no network, HTTP error, …
        }
        return if (stages == null) staticRoadmap(path.id, retrieval, aiFailed = true)
        else Roadmap(path.id, stages, retrieval.knownNodeIds, StudyMode.AI)
    }

    // ---- 1. Retrieval ----

    /** Known nodes stay in (shown as done); the remaining ones are capped at [MAX_RETRIEVED_NODES]. */
    fun retrieve(allNodes: List<SkillNode>, knownSkills: Set<String>): Retrieval {
        val known = knownSkills.map(::normalise).toSet()
        val knownIds = allNodes.filter { normalise(it.title) in known }.map { it.id }.toSet()
        val remaining = allNodes.filter { it.id !in knownIds }
        val keptIds = if (remaining.size <= MAX_RETRIEVED_NODES) {
            remaining.map { it.id }.toSet()
        } else {
            val topics = coreTopics(allNodes)
            remaining.withIndex()
                .sortedWith(compareByDescending<IndexedValue<SkillNode>> { score(it.value, topics) }.thenBy { it.index })
                .take(MAX_RETRIEVED_NODES)
                .map { it.value.id }
                .toSet()
        }
        return Retrieval(allNodes.filter { it.id in knownIds || it.id in keptIds }, knownIds)
    }

    /** Case-insensitive, punctuation-insensitive title key ("C++" and "c++ " match; "C" does not). */
    fun normalise(title: String): String =
        title.lowercase().replace(Regex("[^a-z0-9+#]+"), " ").trim()

    private fun tokens(text: String): Set<String> =
        normalise(text).split(' ').filter { it.length >= 2 && it !in STOPWORDS && !it.all(Char::isDigit) }.toSet()

    /** A path's core topics: the words that appear in the most node titles of that path. */
    private fun coreTopics(nodes: List<SkillNode>): Set<String> =
        nodes.flatMap { tokens(it.title) }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(CORE_TOPIC_COUNT).map { it.key }.toSet()

    private fun score(node: SkillNode, topics: Set<String>): Int =
        tokens(node.title + " " + node.description).count { it in topics }

    // ---- 2. STATIC ordering ----

    /** File order, split into ceil(n / 8) stages of near-equal size. Stage titles default to "Stage N". */
    fun staticStages(nodes: List<SkillNode>): List<Stage> {
        if (nodes.isEmpty()) return emptyList()
        val stageCount = ceil(nodes.size / STATIC_STAGE_SIZE.toDouble()).toInt()
        val base = nodes.size / stageCount
        val extra = nodes.size % stageCount
        var start = 0
        return List(stageCount) { i ->
            val size = base + if (i < extra) 1 else 0
            Stage(nodeIds = nodes.subList(start, start + size).map { it.id }).also { start += size }
        }
    }

    private fun staticRoadmap(pathId: String, r: Retrieval, aiFailed: Boolean) =
        Roadmap(pathId, staticStages(r.nodes), r.knownNodeIds, StudyMode.STATIC, aiFailed)

    // ---- 3. Validation of the AI reply ----

    @Serializable
    private data class AiReply(val stages: List<AiStage>)

    @Serializable
    private data class AiStage(val title: String? = null, val why: String? = null, @SerialName("node_ids") val nodeIds: List<String> = emptyList())

    /**
     * Returns null when the reply is not usable JSON. Otherwise: unknown ids dropped, duplicates
     * removed (first occurrence wins), empty stages dropped, and any retrieved node the model left
     * out appended, in file order, to a final untitled stage.
     */
    fun validate(reply: String, retrieved: List<SkillNode>): List<Stage>? {
        val parsed = try {
            json.decodeFromString<AiReply>(stripCodeFence(reply))
        } catch (e: Exception) {
            return null
        }
        val allowed = retrieved.map { it.id }.toSet()
        val seen = mutableSetOf<String>()
        val stages = parsed.stages.mapNotNull { s ->
            val ids = s.nodeIds.filter { it in allowed && seen.add(it) }
            if (ids.isEmpty()) null
            else Stage(title = s.title?.trim()?.takeIf { it.isNotEmpty() }, why = s.why?.trim()?.takeIf { it.isNotEmpty() }, nodeIds = ids)
        }
        if (stages.isEmpty()) return null
        val missing = retrieved.map { it.id }.filter { it !in seen }
        return if (missing.isEmpty()) stages else stages + Stage(nodeIds = missing)
    }

    private fun stripCodeFence(s: String): String =
        s.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
}
