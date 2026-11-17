package com.techpath.core

import kotlinx.serialization.Serializable

/**
 * The two study conditions. AI = the on-device ML engine (TF-IDF + Logistic
 * Regression + cosine retrieval) personalises the roadmap; STATIC = the same
 * path shown as a fixed checklist (control condition).
 */
enum class StudyMode { AI, STATIC }

@Serializable
data class Resource(
    val title: String,
    val url: String,
    val type: String,
)

/** One roadmap.sh topic ("skill node") of a career path, with its TF-IDF vector. */
class SkillNode(
    val id: String,
    val path: String,
    val slug: String,
    val title: String,
    val description: String,
    val core: Boolean,
    val resources: List<Resource>,
    /** TF-IDF vector of the cleaned "title. description" text the model was trained on. */
    val vector: SparseVector,
)

class CareerPath(
    val id: String,
    val name: String,
    val type: String,
    val description: String,
    val nodeCount: Int,
)

@Serializable
data class RoadmapItem(
    val nodeId: String,
    val why: String = "",
    val known: Boolean = false,
)

@Serializable
data class RoadmapStage(
    val title: String,
    val goal: String,
    val items: List<RoadmapItem>,
)

@Serializable
data class Roadmap(
    val careerPath: String,
    /** "ai" (ML-personalised) or "static" (control checklist). */
    val source: String,
    val stages: List<RoadmapStage>,
    val generatedAt: Long = 0L,
    val note: String? = null,
) {
    val nodeIds: List<String> get() = stages.flatMap { s -> s.items.map { it.nodeId } }
}

/** "What it means / What it involves / Why it matters" for one skill. */
data class SkillExplanation(
    val whatItMeans: String,
    val whatItInvolves: String,
    val whyItMatters: String,
)
