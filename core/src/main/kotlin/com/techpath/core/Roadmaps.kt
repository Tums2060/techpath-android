package com.techpath.core

import kotlin.math.roundToInt

/**
 * AI-mode roadmap, built entirely on the phone from the ML engine's outputs:
 *
 * - relevance  = cosine(student goal, skill) - the notebook's retrieval step
 * - typicality = P(this path | skill text) from the Logistic Regression model,
 *                i.e. how characteristic a skill is of the career
 *
 * Stages: Foundations (core topics) -> Closest to your goal (the notebook's top-5
 * cosine matches) -> Core skills (highest blended score) -> Go further. Skills
 * the student already knows are kept and marked done.
 */
object MlRoadmap {

    data class Signals(
        val relevance: (SkillNode) -> Float,
        val typicality: (SkillNode) -> Float,
        val sharedPaths: (SkillNode) -> Int,
    )

    fun build(
        career: CareerPath,
        pathNodes: List<SkillNode>,
        goal: String,
        yearOfStudy: Int,
        knownIds: Set<String>,
        signals: Signals,
        now: Long = System.currentTimeMillis(),
    ): Roadmap {
        val used = LinkedHashSet<String>()
        val stages = ArrayList<RoadmapStage>()
        val junior = yearOfStudy <= 2
        val name = career.name

        fun item(n: SkillNode, why: String) = RoadmapItem(n.id, why, known = n.id in knownIds)
        fun pct(x: Float) = (x * 100).roundToInt()

        // 1. Foundations: the path's core topics (more of them for years 1-2).
        val core = pathNodes.filter { it.core }
            .sortedWith(compareByDescending<SkillNode> { signals.sharedPaths(it) }.thenByDescending { signals.typicality(it) })
            .take(if (junior) 8 else 5)
        if (core.isNotEmpty()) {
            stages += RoadmapStage("Foundations", "Core topics to get comfortable with first.", core.map { n ->
                val shared = signals.sharedPaths(n) - 1
                used += n.id
                item(n, if (shared > 0) "A fundamental of $name that also appears on $shared other roadmap${if (shared == 1) "" else "s"}."
                else "A fundamental topic of the $name roadmap.")
            })
        }

        // 2. Closest to your goal: the notebook's top-5 cosine matches.
        val goalMatches = pathNodes.filter { it.id !in used }
            .map { Scored(it, signals.relevance(it)) }
            .filter { it.score > 0f }
            .sortedByDescending { it.score }
            .take(5)
        if (goalMatches.isNotEmpty()) {
            stages += RoadmapStage("Closest to your goal", "The skills most similar to what you said you want to do.", goalMatches.map { s ->
                used += s.item.id
                item(s.item, "Among the topics most similar to your goal \"${goal.take(60)}\" (similarity %.2f).".format(s.score))
            })
        }

        // 3 + 4. Rank the rest by a blend of goal relevance and typicality.
        val rest = pathNodes.filter { it.id !in used }
        val maxRel = rest.maxOfOrNull { signals.relevance(it) }?.takeIf { it > 0f } ?: 1f
        val blended = rest.map { n -> Scored(n, 0.6f * signals.typicality(n) + 0.4f * signals.relevance(n) / maxRel) }
            .sortedByDescending { it.score }
        val coreSize = if (junior) 10 else 8
        val furtherSize = if (junior) 8 else 12
        val coreSkills = blended.take(coreSize)
        val further = blended.drop(coreSize).take(furtherSize)
        if (coreSkills.isNotEmpty()) {
            stages += RoadmapStage("Core skills", "Skills the model sees as most characteristic of $name.", coreSkills.map { s ->
                used += s.item.id
                item(s.item, whyBlend(s.item, name, signals, ::pct))
            })
        }
        if (further.isNotEmpty()) {
            stages += RoadmapStage("Go further", "Broaden and deepen once the core skills feel solid.", further.map { s ->
                used += s.item.id
                item(s.item, whyBlend(s.item, name, signals, ::pct))
            })
        }

        // Known skills that were not selected still appear (first stage, marked done).
        val missingKnown = pathNodes.filter { it.id in knownIds && it.id !in used }
        if (missingKnown.isNotEmpty() && stages.isNotEmpty()) {
            val first = stages[0]
            stages[0] = first.copy(items = missingKnown.map { RoadmapItem(it.id, "You said you already know this.", known = true) } + first.items)
        }
        return Roadmap(career.id, source = "ai", stages = stages, generatedAt = now)
    }

    private fun whyBlend(n: SkillNode, name: String, s: Signals, pct: (Float) -> Int): String {
        val t = s.typicality(n)
        val r = s.relevance(n)
        return when {
            t >= 0.5f -> "A typical $name skill (model probability ${pct(t)}%)."
            r > 0f -> "Related to your goal (similarity %.2f).".format(r)
            else -> "A supporting topic on the $name roadmap."
        }
    }
}

/** Control condition: the path's topics as a fixed, non-personalised checklist. */
object StaticRoadmap {

    fun build(careerPath: String, pathNodes: List<SkillNode>, now: Long = System.currentTimeMillis()): Roadmap {
        val core = pathNodes.filter { it.core }
        val rest = pathNodes.filterNot { it.core }.sortedBy { it.title.lowercase() }
        val stages = buildList {
            if (core.isNotEmpty()) add(RoadmapStage("Fundamentals", "Core topics of this roadmap.", core.map { RoadmapItem(it.id) }))
            if (rest.isNotEmpty()) add(RoadmapStage("All topics (A-Z)", "Every other topic in this roadmap.", rest.map { RoadmapItem(it.id) }))
        }
        return Roadmap(careerPath, source = "static", stages = stages, generatedAt = now)
    }
}

/**
 * Offline replacement for the old LLM explanation: an extractive summary of the
 * dataset description plus the ML engine's own evidence for why the skill matters.
 */
object SkillExplainer {

    private val SENTENCE_END = Regex("""(?<=[.!?])\s+(?=["'(\[A-Z0-9])""")

    fun sentences(text: String): List<String> =
        text.trim().split(SENTENCE_END).map { it.trim() }.filter { it.length > 1 }

    fun explain(
        node: SkillNode,
        career: CareerPath,
        /** P(path | skill) for this skill's own path. */
        typicality: Float,
        /** The model's top predictions for this skill (display names + probability). */
        topPredictions: List<Pair<String, Float>>,
        /** Names of other career paths that also contain this skill. */
        otherPaths: List<String>,
        /** The "why now" line from the roadmap, if any. */
        roadmapReason: String?,
    ): SkillExplanation {
        val s = sentences(node.description)
        val meansCount = if (s.isNotEmpty() && s[0].length < 90 && s.size > 2) 2 else 1
        val means = s.take(meansCount).joinToString(" ").ifBlank { node.title }
        val involves = s.drop(meansCount).take(5).joinToString("\n").ifBlank {
            "Read the official documentation for ${node.title}.\nWork through one of the learning resources below.\n" +
                "Build a small example that uses ${node.title} and explain it in your own words."
        }

        val pct = (typicality * 100).roundToInt()
        val why = buildList {
            add(
                when {
                    typicality >= 0.5f -> "${node.title} is a characteristic ${career.name} skill: from its description alone, " +
                        "the TechPath model predicts ${career.name} with $pct% probability."
                    typicality >= 0.15f -> "${node.title} is clearly linked to ${career.name}: the TechPath model gives ${career.name} " +
                        "$pct% probability from this topic's description."
                    else -> {
                        val top = topPredictions.firstOrNull()
                        "${node.title} is a general skill rather than one unique to ${career.name}" +
                            (top?.let { " (the model associates it most with ${it.first})" } ?: "") + ", but it is on the ${career.name} roadmap."
                    }
                },
            )
            if (otherPaths.isNotEmpty()) {
                val shown = otherPaths.take(3).joinToString()
                add("It also appears on ${otherPaths.size} other roadmap${if (otherPaths.size == 1) "" else "s"} ($shown${if (otherPaths.size > 3) ", …" else ""}), " +
                    "so it carries over if you change direction.")
            }
            if (node.core) add("It is one of the core fundamentals of the ${career.name} roadmap.")
            // Add the roadmap's reason only when it says something new (goal similarity, already known).
            roadmapReason?.takeIf { it.contains("your goal") || it.contains("already know") }?.let { add("On your roadmap: $it") }
        }.joinToString(" ")

        return SkillExplanation(means, involves, why)
    }
}
