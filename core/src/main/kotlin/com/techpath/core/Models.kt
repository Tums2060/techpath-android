package com.techpath.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class StudyMode { AI, STATIC }

@Serializable
data class Resource(
    val title: String,
    val url: String,
    val type: String,
)

/** One roadmap.sh topic ("node") of a career path, with its embedding. */
class SkillNode(
    val id: String,
    val path: String,
    val slug: String,
    val title: String,
    val description: String,
    val core: Boolean,
    val resources: List<Resource>,
    val vector: FloatArray,
)

class CareerPath(
    val id: String,
    val name: String,
    val type: String,
    val description: String,
    val nodeCount: Int,
    val vector: FloatArray,
)

/** How the asset vectors were made; the app must embed queries the same way. */
@Serializable
data class EmbeddingMeta(
    val kind: String,
    val model: String,
    val dims: Int,
    val idf: List<Double> = emptyList(),
    @SerialName("known_threshold") val knownThreshold: Double = 0.8,
    @SerialName("known_compare") val knownCompare: String = "node",
    val encoding: String = "float16-le-base64",
) {
    val isLocal: Boolean get() = kind == "hash"
}

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
    /** "ai", "static", or "static-fallback" (AI was requested but unavailable). */
    val source: String,
    val stages: List<RoadmapStage>,
    val generatedAt: Long = 0L,
    val note: String? = null,
) {
    val nodeIds: List<String> get() = stages.flatMap { s -> s.items.map { it.nodeId } }
}
