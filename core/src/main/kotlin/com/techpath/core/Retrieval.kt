package com.techpath.core

data class Scored<T>(val item: T, val score: Float)

/** Step 1: predict the most likely career paths for a student's free text. */
object CareerMatcher {

    data class Match(
        val career: CareerPath,
        /** Logistic Regression probability P(path | text). */
        val probability: Float,
        /** True when the student typed the path's name. */
        val direct: Boolean,
        /** Notebook's retrieval step: the closest skills on this path. */
        val closestSkills: List<String> = emptyList(),
    )

    private fun squash(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** "Backend", "cyber security", "Android Developer" -> that path, no model needed. */
    fun directMatch(text: String, careers: List<CareerPath>): CareerPath? {
        val q = squash(text)
        if (q.isEmpty()) return null
        return careers.firstOrNull { squash(it.id) == q || squash(it.name) == q }
    }

    /**
     * The notebook's `recommend()`: top-[k] paths by predicted probability. An exact
     * path-name match is put first if the model did not already rank it first.
     */
    fun topMatches(
        model: MlModel,
        text: String,
        careers: List<CareerPath>,
        nodesOf: (String) -> List<SkillNode>,
        k: Int = 3,
        skillsPerPath: Int = 5,
    ): List<Match> {
        val byId = careers.associateBy { it.id }
        val q = model.vectorizer.transform(text)
        val preds = model.classifier.predictProba(q)
        val direct = directMatch(text, careers)
        val ranked = model.classifier.topK(q, k).mapNotNull { p -> byId[p.path]?.let { it to p.probability } }
            .toMutableList()
        if (direct != null && ranked.firstOrNull()?.first?.id != direct.id) {
            ranked.removeAll { it.first.id == direct.id }
            val p = model.classifier.indexOf(direct.id).takeIf { it >= 0 }?.let { preds[it] } ?: 0f
            ranked.add(0, direct to p)
            if (ranked.size > k) ranked.removeAt(ranked.lastIndex)
        }
        return ranked.map { (c, p) ->
            Match(c, p, direct = c.id == direct?.id,
                closestSkills = Retriever.closest(q, nodesOf(c.id), skillsPerPath).map { it.item.title })
        }
    }
}

/** Step 2: cosine-similarity retrieval of skills (the notebook's retrieval step). */
object Retriever {

    /** Skills ranked by cosine similarity to [query], best first; ties keep input order. */
    fun rank(query: SparseVector, nodes: List<SkillNode>): List<Scored<SkillNode>> =
        nodes.mapIndexed { i, n -> Triple(i, n, query.dot(n.vector)) }
            .sortedWith(compareByDescending<Triple<Int, SkillNode, Float>> { it.third }.thenBy { it.first })
            .map { Scored(it.second, it.third) }

    /** The notebook's top-5 "closest skills on the roadmap" (only skills with any overlap). */
    fun closest(query: SparseVector, nodes: List<SkillNode>, k: Int = 5): List<Scored<SkillNode>> =
        if (query.isEmpty()) emptyList() else rank(query, nodes).filter { it.score > 0f }.take(k)
}

/** Step 3: mark skills the student already knows. */
object SkillGap {

    const val DEFAULT_THRESHOLD = 0.60f

    /**
     * A skill node is "already known" when the student picked it as a chip, typed
     * its exact title, or a typed skill's TF-IDF vector has cosine >= [threshold]
     * with the node's title vector.
     */
    fun knownNodeIds(
        candidates: List<SkillNode>,
        chipIds: Set<String>,
        skillTexts: List<String>,
        vectorizer: TfidfVectorizer,
        threshold: Float = DEFAULT_THRESHOLD,
    ): Set<String> {
        val typed = skillTexts.map { TextCleaner.clean(it) }.filter { it.isNotEmpty() }
        val typedVecs = typed.map { vectorizer.transformCleaned(it) }.filterNot { it.isEmpty() }
        return candidates.filter { node ->
            if (node.id in chipIds) return@filter true
            val title = TextCleaner.clean(node.title)
            if (typed.any { it == title }) return@filter true
            if (typedVecs.isEmpty()) return@filter false
            val tv = vectorizer.transformCleaned(title)
            typedVecs.any { it.cosine(tv) >= threshold }
        }.map { it.id }.toSet()
    }
}

/** Opportunity "Relevance": the classifier's probability that a listing belongs to the student's path. */
object Relevance {
    fun percent(model: MlModel, listingText: String, careerPath: String): Int? {
        val idx = model.classifier.indexOf(careerPath)
        if (idx < 0) return null
        val p = model.classifier.predictProba(model.vectorizer.transform(listingText))[idx]
        return (p * 100).toInt().coerceIn(0, 100)
    }
}
