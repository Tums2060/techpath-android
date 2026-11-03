package com.techpath.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---- Dataset (bundled assets, see tools/build_assets.py) ----

@Serializable
data class PathInfo(
    val id: String,
    val name: String,
    val type: String,
    val parent: String? = null,
    val nodeCount: Int,
) {
    val isRole: Boolean get() = type == "role"
    val isBeginnerVariant: Boolean get() = parent != null
}

@Serializable
data class Link(val type: String, val title: String, val url: String)

@Serializable
data class SkillNode(
    val id: String,
    val skillId: String,
    val title: String,
    val description: String,
    val links: List<Link> = emptyList(),
)

@Serializable
data class PathData(val id: String, val nodes: List<SkillNode>)

@Serializable
enum class OpportunityType { INTERNSHIP, ATTACHMENT, JOB, HACKATHON }

@Serializable
data class Opportunity(
    val id: String,
    val title: String,
    val organisation: String,
    val type: OpportunityType,
    val location: String,
    val deadline: String? = null, // ISO date, e.g. 2026-11-30
    val requirements: List<String> = emptyList(),
    val url: String,
)

data class Article(val file: String, val title: String)

// ---- User state (DataStore) ----

enum class StudyMode { AI, STATIC }

@Serializable
enum class ApplicationStatus { SAVED, APPLIED, INTERVIEW, OFFER, REJECTED }

@Serializable
data class UserSettings(
    val onboarded: Boolean = false,
    val year: Int = 1,
    val pathId: String? = null,
    /** Skill titles the student ticked in onboarding. */
    val knownSkills: Set<String> = emptySet(),
    val studyMode: StudyMode = StudyMode.STATIC,
    /** Overrides BuildConfig.GEMINI_API_KEY when not blank. */
    val apiKeyOverride: String = "",
)

// ---- Roadmap ----

@Serializable
data class Stage(
    /** Null means "use the default label" (Stage 1, Stage 2, …), which the UI takes from strings.xml. */
    val title: String? = null,
    val why: String? = null,
    @SerialName("node_ids") val nodeIds: List<String>,
)

@Serializable
data class Roadmap(
    val pathId: String,
    val stages: List<Stage>,
    val knownNodeIds: Set<String>,
    val source: StudyMode,
    /** True when AI mode was requested but the roadmap fell back to STATIC. */
    val aiFailed: Boolean = false,
)
