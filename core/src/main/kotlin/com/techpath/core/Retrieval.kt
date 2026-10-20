package com.techpath.core

/** Step 1: match a free-text career interest to career paths. */
object CareerMatcher {

    data class Match(val career: CareerPath, val score: Float, val direct: Boolean)

    private fun squash(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /**
     * If the student typed a path name ("Backend", "cyber security", "Android
     * Developer") return it directly, without embeddings.
     */
    fun directMatch(text: String, careers: List<CareerPath>): CareerPath? {
        val q = squash(text)
        if (q.isEmpty()) return null
        return careers.firstOrNull { squash(it.id) == q || squash(it.name) == q }
    }

    fun topMatches(queryVector: FloatArray, careers: List<CareerPath>, k: Int = 3): List<Match> =
        VectorMath.topK(queryVector, careers, k) { it.vector }.map { Match(it.item, it.score, direct = false) }
}

/** Step 2: rank a path's nodes against the student's goal. */
object Retriever {

    const val DEFAULT_K = 28
    const val MAX_CORE = 8

    /**
     * Top-[k] nodes by cosine similarity to [goalVector], plus the path's core
     * fundamentals (at most [maxCore]) and any [mustInclude] nodes (e.g. skills
     * the student said they know), so the LLM always sees them.
     */
    fun retrieve(
        goalVector: FloatArray,
        pathNodes: List<SkillNode>,
        k: Int = DEFAULT_K,
        maxCore: Int = MAX_CORE,
        mustInclude: Set<String> = emptySet(),
    ): List<VectorMath.Scored<SkillNode>> {
        val scored = VectorMath.topK(goalVector, pathNodes, pathNodes.size) { it.vector }
        val picked = LinkedHashMap<String, VectorMath.Scored<SkillNode>>()
        scored.take(k).forEach { picked[it.item.id] = it }
        scored.filter { it.item.core }.take(maxCore).forEach { picked.putIfAbsent(it.item.id, it) }
        scored.filter { it.item.id in mustInclude }.forEach { picked.putIfAbsent(it.item.id, it) }
        return picked.values.sortedByDescending { it.score }
    }
}

/** Step 3: mark nodes the student already knows. */
object SkillGap {

    const val DEFAULT_THRESHOLD = 0.80f

    /**
     * A node is "already known" when:
     *  - the student picked it as a chip, or
     *  - a free-text skill has the same normalised words as the node title, or
     *  - cosine(skill, node) >= [threshold] for any free-text skill.
     *
     * [compareVector] picks what a skill is compared with: the full node vector
     * for semantic embeddings, or a title-only vector for the lexical fallback.
     */
    fun knownNodeIds(
        candidates: List<SkillNode>,
        chipIds: Set<String>,
        skillTexts: List<String>,
        skillVectors: List<FloatArray>,
        threshold: Float,
        compareVector: (SkillNode) -> FloatArray = { it.vector },
    ): Set<String> {
        val skillTokens = skillTexts.map { HashEmbedder.tokens(it) }.filter { it.isNotEmpty() }
        return candidates.filter { node ->
            node.id in chipIds ||
                HashEmbedder.tokens(node.title).let { t -> skillTokens.any { it == t } } ||
                skillVectors.any { VectorMath.cosine(it, compareVector(node)) >= threshold }
        }.map { it.id }.toSet()
    }
}

/** Opportunity "Relevance": cosine between a listing and the student profile. */
object Relevance {

    /** Student profile = mean of the chosen path vector and the roadmap's node vectors. */
    fun studentVector(path: CareerPath?, roadmapNodes: List<SkillNode>): FloatArray? {
        val vs = buildList {
            path?.let { add(VectorMath.normalize(it.vector)) }
            VectorMath.meanNormalized(roadmapNodes.map { it.vector })?.let { add(it) }
        }
        return VectorMath.meanNormalized(vs)
    }

    /** Cosine clamped to 0..100 for display. */
    fun percent(student: FloatArray, listing: FloatArray): Int =
        (VectorMath.cosine(student, listing).coerceIn(0f, 1f) * 100).toInt()
}
