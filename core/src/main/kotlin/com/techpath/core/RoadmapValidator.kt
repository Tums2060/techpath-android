package com.techpath.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parses and validates the LLM's roadmap JSON (step 4).
 *
 * - Unknown ids (not in the retrieved set) and duplicates are dropped.
 * - Empty stages are dropped; more than [MAX_STAGES] are merged into the last.
 * - Fewer than [MIN_STAGES] stages, or too few valid skills, is [Result.Invalid]
 *   so the caller can retry once and then fall back to Static mode.
 * - Known skills the LLM left out are added to the first stage, marked done.
 */
object RoadmapValidator {

    const val MIN_STAGES = 4
    const val MAX_STAGES = 6
    /** At least this share of retrieved ids must survive validation. */
    const val MIN_COVERAGE = 0.4

    sealed interface Result {
        data class Valid(val roadmap: Roadmap, val droppedIds: List<String>) : Result
        data class Invalid(val reason: String) : Result
    }

    @Serializable private data class RawSkill(val id: String = "", val why: String = "")
    @Serializable private data class RawStage(val title: String = "", val goal: String = "", val skills: List<RawSkill> = emptyList())
    @Serializable private data class RawRoadmap(val stages: List<RawStage> = emptyList())

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    /** Strips ``` fences and any prose around the outermost JSON object. */
    fun extractJson(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start in 0 until end) text.substring(start, end + 1) else null
    }

    fun validate(
        raw: String,
        careerPath: String,
        allowedIds: Set<String>,
        knownIds: Set<String>,
        now: Long = System.currentTimeMillis(),
    ): Result {
        val body = extractJson(raw) ?: return Result.Invalid("no JSON object in response")
        val parsed = try {
            json.decodeFromString(RawRoadmap.serializer(), body)
        } catch (e: Exception) {
            return Result.Invalid("malformed JSON: ${e.message?.take(120)}")
        }

        val seen = HashSet<String>()
        val dropped = mutableListOf<String>()
        var stages = parsed.stages.mapIndexedNotNull { i, s ->
            val items = s.skills.mapNotNull { sk ->
                val id = sk.id.trim()
                when {
                    id !in allowedIds -> { dropped += id; null }
                    !seen.add(id) -> null
                    else -> RoadmapItem(id, sk.why.trim(), known = id in knownIds)
                }
            }
            if (items.isEmpty()) null
            else RoadmapStage(s.title.trim().ifEmpty { "Stage ${i + 1}" }, s.goal.trim(), items)
        }

        if (stages.size > MAX_STAGES) {
            val tail = stages.drop(MAX_STAGES - 1)
            stages = stages.take(MAX_STAGES - 1) + tail.first().copy(items = tail.flatMap { it.items })
        }
        if (stages.size < MIN_STAGES) return Result.Invalid("expected $MIN_STAGES-$MAX_STAGES stages, got ${stages.size}")
        if (seen.size < (allowedIds.size * MIN_COVERAGE).toInt().coerceAtMost(8)) {
            return Result.Invalid("only ${seen.size} valid skills")
        }

        val missingKnown = knownIds.filter { it in allowedIds && it !in seen }
        if (missingKnown.isNotEmpty()) {
            val first = stages.first()
            stages = listOf(first.copy(items = missingKnown.map { RoadmapItem(it, "You already know this.", known = true) } + first.items)) + stages.drop(1)
        }
        return Result.Valid(Roadmap(careerPath, source = "ai", stages = stages, generatedAt = now), dropped)
    }
}

/** Parsed on-demand skill explanation (step 5). */
@Serializable
data class SkillExplanation(
    @kotlinx.serialization.SerialName("what_it_means") val whatItMeans: String = "",
    @kotlinx.serialization.SerialName("what_it_involves") val whatItInvolves: String = "",
    @kotlinx.serialization.SerialName("why_it_matters") val whyItMatters: String = "",
) {
    val isComplete get() = whatItMeans.isNotBlank() && whatItInvolves.isNotBlank() && whyItMatters.isNotBlank()
}

object SkillExplanationParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String): SkillExplanation? {
        val body = RoadmapValidator.extractJson(raw) ?: return null
        return try {
            json.decodeFromString(SkillExplanation.serializer(), body).takeIf { it.isComplete }
        } catch (e: Exception) {
            null
        }
    }
}

/** Control condition: the path's nodes as a fixed, non-personalised checklist. */
object StaticRoadmap {

    fun build(careerPath: String, pathNodes: List<SkillNode>, source: String = "static", note: String? = null): Roadmap {
        val core = pathNodes.filter { it.core }
        val rest = pathNodes.filterNot { it.core }.sortedBy { it.title.lowercase() }
        val stages = buildList {
            if (core.isNotEmpty()) add(RoadmapStage("Fundamentals", "Core topics of this roadmap.", core.map { RoadmapItem(it.id) }))
            if (rest.isNotEmpty()) add(RoadmapStage("All topics (A-Z)", "Every other topic in this roadmap.", rest.map { RoadmapItem(it.id) }))
        }
        return Roadmap(careerPath, source = source, stages = stages, generatedAt = System.currentTimeMillis(), note = note)
    }
}
