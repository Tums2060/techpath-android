package com.techpath.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.techpath.app.data.db.AppDatabase
import com.techpath.app.data.db.ExplanationEntity
import com.techpath.app.data.db.RoadmapEntity
import com.techpath.app.data.db.SkillStatus
import com.techpath.app.data.db.SkillStatusEntity
import com.techpath.core.CareerMatcher
import com.techpath.core.CareerPath
import com.techpath.core.Prompts
import com.techpath.core.Relevance
import com.techpath.core.Retriever
import com.techpath.core.Roadmap
import com.techpath.core.RoadmapValidator
import com.techpath.core.SkillExplanationParser
import com.techpath.core.SkillGap
import com.techpath.core.SkillNode
import com.techpath.core.StaticRoadmap
import com.techpath.core.StudyMode
import com.techpath.core.TextEmbedder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/** The study condition actually in force, and why it differs from the setting. */
data class ModeState(val requested: StudyMode, val effective: StudyMode, val reason: String?) {
    val isFallback get() = requested == StudyMode.AI && effective == StudyMode.STATIC
}

data class CareerMatchResult(
    val direct: CareerPath?,
    val matches: List<CareerMatcher.Match>,
    val error: String? = null,
)

/**
 * The RAG pipeline (on-device except for the API calls):
 * career matching -> retrieval -> skill-gap -> LLM generation -> validation,
 * with Static mode as the control condition and the fallback.
 */
class RoadmapRepository(
    private val context: Context,
    private val kb: KnowledgeBase,
    private val gemini: GeminiClient,
    private val settings: SettingsRepository,
    private val db: AppDatabase,
    private val logger: EventLogger,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun isOnline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun modeState(): ModeState {
        val s = settings.current()
        if (s.mode == StudyMode.STATIC) return ModeState(StudyMode.STATIC, StudyMode.STATIC, null)
        val reason = when {
            settings.apiKey() == null -> "No Gemini API key set. Add one in Settings to use AI mode."
            !isOnline() -> "You're offline, so TechPath is showing the static roadmap."
            else -> null
        }
        return ModeState(StudyMode.AI, if (reason == null) StudyMode.AI else StudyMode.STATIC, reason)
    }

    /** Embeds queries in the same space as the bundled vectors, or null if impossible now. */
    private suspend fun queryEmbedder(data: KnowledgeBase.Data): TextEmbedder? {
        data.hashEmbedder?.let { return it }
        val key = settings.apiKey() ?: return null
        if (!isOnline()) return null
        return gemini.embedder(key, data.embedding.model, data.embedding.dims)
    }

    // ---------------------------------------------------------------- step 1

    suspend fun matchCareers(interest: String): CareerMatchResult {
        val data = kb.get()
        val direct = CareerMatcher.directMatch(interest, data.careers)
        if (direct != null) return CareerMatchResult(direct, listOf(CareerMatcher.Match(direct, 1f, direct = true)))
        val embedder = queryEmbedder(data)
            ?: return CareerMatchResult(null, emptyList(), "Matching needs the embeddings API (no key or offline). Pick a path from the list.")
        return try {
            val q = embedder.embed(listOf(interest)).single()
            CareerMatchResult(null, CareerMatcher.topMatches(q, data.careers, 3))
        } catch (e: Exception) {
            CareerMatchResult(null, emptyList(), "Couldn't match your interest: ${e.message}")
        }
    }

    /** Chips for "skills you already have": core topics plus those closest to the goal. */
    suspend fun suggestedSkills(path: String, interest: String, limit: Int = 18): List<SkillNode> {
        val data = kb.get()
        val nodes = data.nodes(path)
        val core = nodes.filter { it.core }
        val near = runCatching {
            val e = queryEmbedder(data) ?: return@runCatching emptyList()
            val q = e.embed(listOf("${data.career(path)?.name}: $interest")).single()
            Retriever.retrieve(q, nodes, k = limit, maxCore = 0).map { it.item }
        }.getOrDefault(emptyList())
        return (core + near + nodes).distinctBy { it.title.lowercase() }.take(limit)
    }

    // ---------------------------------------------------------------- roadmap

    fun observeRoadmap(path: String): Flow<Roadmap?> =
        db.roadmaps().observe(path).map { e -> e?.let { runCatching { json.decodeFromString(Roadmap.serializer(), it.json) }.getOrNull() } }

    fun observeStatuses(path: String): Flow<Map<String, SkillStatus>> =
        db.skillStatus().observe(path).map { l -> l.associate { it.nodeId to it.status } }

    fun observeStatus(path: String, nodeId: String): Flow<SkillStatus> =
        db.skillStatus().observeOne(path, nodeId).map { it?.status ?: SkillStatus.NOT_STARTED }

    suspend fun setStatus(path: String, nodeId: String, status: SkillStatus) {
        db.skillStatus().upsert(SkillStatusEntity(path, nodeId, status, System.currentTimeMillis()))
        logger.log(Events.STATUS_CHANGED, path, nodeId, status.name)
    }

    /** Generates (AI) or builds (Static) the roadmap for [path] and stores it. */
    suspend fun generate(path: String): Roadmap {
        val data = kb.get()
        val nodes = data.nodes(path)
        val career = data.career(path) ?: error("Unknown career path $path")
        val mode = modeState()
        val started = System.currentTimeMillis()

        val roadmap = if (mode.effective == StudyMode.STATIC) {
            StaticRoadmap.build(path, nodes, source = if (mode.isFallback) "static-fallback" else "static", note = mode.reason)
        } else {
            try {
                generateAi(data, career, nodes)
            } catch (e: Exception) {
                StaticRoadmap.build(path, nodes, "static-fallback", "AI roadmap unavailable (${e.message ?: e.javaClass.simpleName}). Showing the static roadmap.")
            }
        }

        db.roadmaps().upsert(RoadmapEntity(path, json.encodeToString(Roadmap.serializer(), roadmap), roadmap.source, roadmap.generatedAt))
        val knownItems = roadmap.stages.flatMap { it.items }.filter { it.known }
        db.skillStatus().insertIfAbsent(knownItems.map { SkillStatusEntity(path, it.nodeId, SkillStatus.DONE, System.currentTimeMillis()) })
        logger.log(
            if (roadmap.source == "static-fallback") Events.ROADMAP_FALLBACK else Events.ROADMAP_GENERATED,
            path, null,
            "source=${roadmap.source};stages=${roadmap.stages.size};skills=${roadmap.nodeIds.size};known=${knownItems.size}",
            System.currentTimeMillis() - started,
        )
        return roadmap
    }

    private suspend fun generateAi(data: KnowledgeBase.Data, career: CareerPath, nodes: List<SkillNode>): Roadmap {
        val s = settings.current()
        val profile = s.profile
        val key = settings.apiKey() ?: error("no API key")
        val embedder = queryEmbedder(data) ?: error("embeddings unavailable")

        // Step 2: retrieval against the student's goal.
        val goal = profile.interest.ifBlank { career.name }
        val goalVec = embedder.embed(listOf("${career.name}: $goal")).single()

        // Step 3: skill gap. Chips are exact; free-text skills use the threshold.
        val skills = profile.knownSkills.toList()
        val skillVecs = if (skills.isEmpty()) emptyList() else embedder.embed(skills)
        val threshold = s.knownThreshold ?: data.embedding.knownThreshold.toFloat()
        val compare: (SkillNode) -> FloatArray =
            if (data.embedding.knownCompare == "title" && data.hashEmbedder != null) { n -> data.titleVector(n) } else { n -> n.vector }
        val known = SkillGap.knownNodeIds(nodes, profile.knownChipIds, skills, skillVecs, threshold, compare)

        val retrieved = Retriever.retrieve(goalVec, nodes, mustInclude = known).map { it.item }
        val allowed = retrieved.map { it.id }.toSet()
        val knownInSet = known intersect allowed

        // Step 4: generation, validated; one repair retry.
        val user = Prompts.roadmapUser(career.name, goal, profile.year, skills + chipTitles(nodes, profile.knownChipIds), retrieved, knownInSet)
        val turns = mutableListOf(Turn("user", user))
        var last = "no response"
        repeat(2) { attempt ->
            val reply = gemini.generate(key, s.textModel, Prompts.ROADMAP_SYSTEM, turns)
            when (val r = RoadmapValidator.validate(reply, career.id, allowed, knownInSet)) {
                is RoadmapValidator.Result.Valid -> return r.roadmap.copy(
                    note = if (r.droppedIds.isNotEmpty()) "${r.droppedIds.size} unknown skill id(s) from the AI were removed." else null,
                )
                is RoadmapValidator.Result.Invalid -> {
                    last = r.reason
                    if (attempt == 0) {
                        turns += Turn("model", reply)
                        turns += Turn("user", Prompts.REPAIR)
                    }
                }
            }
        }
        error("AI response failed validation: $last")
    }

    private fun chipTitles(nodes: List<SkillNode>, ids: Set<String>) = nodes.filter { it.id in ids }.map { it.title }

    // ---------------------------------------------------------------- step 5

    data class Explanation(val whatItMeans: String, val whatItInvolves: String, val whyItMatters: String)

    suspend fun explanation(path: String, nodeId: String, forceRefresh: Boolean = false): Explanation {
        if (!forceRefresh) db.explanations().get(path, nodeId)?.let { return Explanation(it.whatItMeans, it.whatItInvolves, it.whyItMatters) }
        val data = kb.get()
        val node = data.node(path, nodeId) ?: error("Unknown skill")
        val career = data.career(path) ?: error("Unknown career path")
        val s = settings.current()
        val key = settings.apiKey() ?: error("No API key")
        val prompt = Prompts.skillUser(node.title, node.description, career.name, s.profile.interest)
        var parsed = SkillExplanationParser.parse(gemini.generate(key, s.textModel, Prompts.SKILL_SYSTEM, listOf(Turn("user", prompt))))
        if (parsed == null) {
            parsed = SkillExplanationParser.parse(gemini.generate(key, s.textModel, Prompts.SKILL_SYSTEM, listOf(Turn("user", prompt + "\n\nReply with the JSON object only."))))
        }
        parsed ?: error("The AI response could not be read. Try again.")
        db.explanations().upsert(
            ExplanationEntity(path, nodeId, parsed.whatItMeans, parsed.whatItInvolves, parsed.whyItMatters, s.textModel, System.currentTimeMillis()),
        )
        return Explanation(parsed.whatItMeans, parsed.whatItInvolves, parsed.whyItMatters)
    }

    /** Student profile vector for opportunity relevance. */
    suspend fun studentVector(path: String?, roadmap: Roadmap?): FloatArray? {
        val data = kb.get()
        val career = path?.let { data.career(it) }
        val nodes = roadmap?.nodeIds?.mapNotNull { data.node(roadmap.careerPath, it) }.orEmpty()
        return Relevance.studentVector(career, nodes)
    }

    suspend fun clearCaches() {
        db.explanations().clear()
        db.roadmaps().clear()
    }
}
