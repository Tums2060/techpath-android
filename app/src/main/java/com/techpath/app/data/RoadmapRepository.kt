package com.techpath.app.data

import com.techpath.app.data.db.AppDatabase
import com.techpath.app.data.db.RoadmapEntity
import com.techpath.app.data.db.SkillStatus
import com.techpath.app.data.db.SkillStatusEntity
import com.techpath.core.CareerMatcher
import com.techpath.core.MlRoadmap
import com.techpath.core.Retriever
import com.techpath.core.Roadmap
import com.techpath.core.SkillExplainer
import com.techpath.core.SkillExplanation
import com.techpath.core.SkillGap
import com.techpath.core.SkillNode
import com.techpath.core.StaticRoadmap
import com.techpath.core.StudyMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** The study condition in force. Everything runs on the phone, so there is no fallback. */
data class ModeState(val requested: StudyMode, val effective: StudyMode = requested, val reason: String? = null) {
    val isFallback get() = requested != effective
}

data class CareerMatchResult(
    val direct: com.techpath.core.CareerPath?,
    val matches: List<CareerMatcher.Match>,
    val error: String? = null,
)

/**
 * The TechPath ML engine pipeline, fully on-device (no network, no API key):
 * 1. career matching  - TF-IDF + Logistic Regression -> top-3 paths with probabilities
 * 2. retrieval        - cosine similarity between the student's goal and every skill on the path
 * 3. skill gap        - known skills marked done (chips, exact titles, TF-IDF similarity)
 * 4. roadmap          - stages built from relevance + the model's typicality scores
 * 5. skill detail     - extractive explanation + the model's evidence
 * Static mode is the non-personalised control condition.
 */
class RoadmapRepository(
    private val kb: KnowledgeBase,
    private val settings: SettingsRepository,
    private val db: AppDatabase,
    private val logger: EventLogger,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun modeState(): ModeState = ModeState(settings.current().mode)

    // ---------------------------------------------------------------- step 1

    suspend fun matchCareers(interest: String): CareerMatchResult = withContext(Dispatchers.Default) {
        val data = kb.get()
        val direct = CareerMatcher.directMatch(interest, data.careers)
        val matches = CareerMatcher.topMatches(data.model, interest, data.careers, data::nodes, k = 3)
        CareerMatchResult(direct, matches, if (matches.isEmpty()) "No career path matched. Pick one from the list." else null)
    }

    /** Chips for "skills you already have": core topics plus those closest to the goal. */
    suspend fun suggestedSkills(path: String, interest: String, limit: Int = 18): List<SkillNode> = withContext(Dispatchers.Default) {
        val data = kb.get()
        val nodes = data.nodes(path)
        val core = nodes.filter { it.core }
        val near = Retriever.closest(data.model.vectorizer.transform(interest), nodes, limit).map { it.item }
        (core + near + nodes).distinctBy { it.title.lowercase() }.take(limit)
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

    /** Builds the AI (ML) or Static roadmap for [path] and stores it. */
    suspend fun generate(path: String): Roadmap = withContext(Dispatchers.Default) {
        val data = kb.get()
        val nodes = data.nodes(path)
        val career = data.career(path) ?: error("Unknown career path $path")
        val s = settings.current()
        val started = System.currentTimeMillis()

        val roadmap = if (s.mode == StudyMode.STATIC) {
            StaticRoadmap.build(path, nodes)
        } else {
            val profile = s.profile
            val goal = profile.interest.ifBlank { career.name }
            val goalVec = data.model.vectorizer.transform(goal)
            val known = SkillGap.knownNodeIds(nodes, profile.knownChipIds, profile.knownSkills.toList(), data.model.vectorizer,
                s.knownThreshold ?: SkillGap.DEFAULT_THRESHOLD)
            MlRoadmap.build(
                career, nodes, goal, profile.year, known,
                MlRoadmap.Signals(
                    relevance = { goalVec.dot(it.vector) },
                    typicality = data::typicality,
                    sharedPaths = data::sharedPathCount,
                ),
            )
        }

        db.roadmaps().upsert(RoadmapEntity(path, json.encodeToString(Roadmap.serializer(), roadmap), roadmap.source, roadmap.generatedAt))
        val knownItems = roadmap.stages.flatMap { it.items }.filter { it.known }
        db.skillStatus().insertIfAbsent(knownItems.map { SkillStatusEntity(path, it.nodeId, SkillStatus.DONE, System.currentTimeMillis()) })
        logger.log(
            Events.ROADMAP_GENERATED, path, null,
            "source=${roadmap.source};stages=${roadmap.stages.size};skills=${roadmap.nodeIds.size};known=${knownItems.size}",
            System.currentTimeMillis() - started,
        )
        roadmap
    }

    // ---------------------------------------------------------------- step 5

    suspend fun explanation(path: String, nodeId: String): SkillExplanation = withContext(Dispatchers.Default) {
        val data = kb.get()
        val node = data.node(path, nodeId) ?: error("Unknown skill")
        val career = data.career(path) ?: error("Unknown career path")
        val why = observeRoadmap(path).first()?.stages?.flatMap { it.items }?.firstOrNull { it.nodeId == nodeId }?.why
        SkillExplainer.explain(node, career, data.typicality(node), data.topPredictions(node), data.otherPaths(node), why)
    }

    /** Opportunity relevance: P(student's career path | listing text), as a percentage. */
    suspend fun relevance(path: String?, listingText: String): Int? {
        if (path == null) return null
        return com.techpath.core.Relevance.percent(kb.get().model, listingText, path)
    }

    suspend fun clearCaches() {
        db.roadmaps().clear()
    }
}
