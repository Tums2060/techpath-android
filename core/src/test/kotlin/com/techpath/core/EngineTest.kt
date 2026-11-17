package com.techpath.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {

    // A tiny hand-made vocabulary so the maths can be checked by hand.
    private val vocab = listOf("kotlin", "android", "sql", "git", "kotlin android", "jetpack", "compose", "jetpack compose")
        .withIndex().associate { (i, t) -> t to i }
    private val vectorizer = TfidfVectorizer(vocab, FloatArray(vocab.size) { 1f }, setOf("the", "and", "with"))

    private fun node(id: String, title: String, text: String, core: Boolean = false) =
        SkillNode(id, "android", id, title, "$title description.", core, emptyList(), vectorizer.transform(text))

    @Test fun cleanerMatchesNotebookRules() {
        assertEquals("check the docs for c++ c# .net sql", TextCleaner.clean("Check [the docs](https://x.io) for C++, C# & .NET -- @official@ SQL!!"))
        assertEquals("a b", TextCleaner.clean("  A\n\t  B "))
        assertEquals("heading text", TextCleaner.clean("Heading ===== text"))
    }

    @Test fun tokensDropStopWordsAndSingleLetters() {
        assertEquals(listOf("kotlin", "android"), vectorizer.tokens("the kotlin and a android"))
        // bigrams are built after stop words are removed, as in scikit-learn
        assertEquals(listOf("kotlin", "android", "kotlin android"), vectorizer.terms("kotlin with android"))
    }

    @Test fun tfidfIsSublinearAndL2Normalised() {
        val v = vectorizer.transform("kotlin kotlin sql")
        assertEquals(listOf(0, 2), v.indices.toList())
        val tf = 1f + kotlin.math.ln(2f)
        val n = kotlin.math.sqrt(tf * tf + 1f)
        assertEquals(tf / n, v.values[0], 1e-6f)
        assertEquals(1f / n, v.values[1], 1e-6f)
        assertEquals(1f, v.norm(), 1e-6f)
        assertTrue(vectorizer.transform("nothing known here").isEmpty())
    }

    @Test fun sparseDotAndCosine() {
        val a = SparseVector.of(mapOf(0 to 1f, 3 to 2f))
        val b = SparseVector.of(mapOf(3 to 4f, 5 to 1f))
        assertEquals(8f, a.dot(b), 0f)
        assertEquals(0f, a.cosine(SparseVector.EMPTY), 0f)
        assertEquals(1f, a.cosine(SparseVector.of(mapOf(0 to 2f, 3 to 4f))), 1e-6f)
    }

    @Test fun classifierSoftmax() {
        // 2 features x 2 classes; feature 0 favours class "a", feature 1 favours "b".
        // float16 bit patterns: 2.0 = 0x4000, -2.0 = 0xC000, -1.0 = 0xBC00, 1.0 = 0x3C00
        val w = shortArrayOf(0x4000, 0xC000.toShort(), 0xBC00.toShort(), 0x3C00)
        assertEquals(-2f, CareerClassifier.halfToFloat(0xC000), 0f)
        val clf = CareerClassifier(listOf("a", "b"), w, floatArrayOf(0f, 0f))
        val p = clf.predictProba(SparseVector.of(mapOf(0 to 1f)))
        assertEquals(1f, p.sum(), 1e-6f)
        assertEquals((1 / (1 + Math.exp(-4.0))).toFloat(), p[0], 1e-6f)
        assertEquals("b", clf.topK(SparseVector.of(mapOf(1 to 1f)), 1).single().path)
        assertEquals(0.5f, clf.predictProba(SparseVector.EMPTY)[0], 1e-6f)
    }

    @Test fun retrieverRanksByCosineAndSkipsZeroMatches() {
        val nodes = listOf(node("sql", "SQL", "sql"), node("kt", "Kotlin", "kotlin android"), node("git", "Git", "git"))
        val q = vectorizer.transform("I want to write Kotlin for Android")
        assertEquals(listOf("kt"), Retriever.closest(q, nodes).map { it.item.id })
        assertEquals(listOf("kt", "sql", "git"), Retriever.rank(q, nodes).map { it.item.id })
    }

    @Test fun skillGapUsesChipsTitlesAndThreshold() {
        val nodes = listOf(node("sql", "SQL", "sql"), node("kt", "Kotlin Android", "kotlin android"), node("jc", "Jetpack Compose", "jetpack compose"))
        assertEquals(setOf("sql", "jc"), SkillGap.knownNodeIds(nodes, setOf("jc"), listOf("sql"), vectorizer, 0.99f))
        // "kotlin" alone is similar to the title "Kotlin Android" but not identical
        assertEquals(setOf("kt"), SkillGap.knownNodeIds(nodes, emptySet(), listOf("Kotlin"), vectorizer, 0.5f))
        assertTrue(SkillGap.knownNodeIds(nodes, emptySet(), listOf("Kotlin"), vectorizer, 0.95f).isEmpty())
    }

    @Test fun directMatchByName() {
        val careers = listOf(CareerPath("backend", "Backend Developer", "role", "", 1), CareerPath("cyber-security", "Cyber Security", "role", "", 1))
        assertEquals("backend", CareerMatcher.directMatch("Backend", careers)?.id)
        assertEquals("cyber-security", CareerMatcher.directMatch(" cyber security ", careers)?.id)
        assertEquals(null, CareerMatcher.directMatch("I want to build apps for banks", careers))
    }

    @Test fun mlRoadmapStagesAndKnownSkills() {
        val nodes = (1..30).map { node("n$it", "Topic $it", if (it % 3 == 0) "kotlin android" else "sql", core = it <= 3) }
        val career = CareerPath("android", "Android Developer", "role", "", nodes.size)
        val q = vectorizer.transform("kotlin android")
        val r = MlRoadmap.build(career, nodes, "kotlin android", 1, knownIds = setOf("n30"),
            signals = MlRoadmap.Signals({ q.dot(it.vector) }, { 0.3f }, { 1 }))
        assertEquals("ai", r.source)
        assertEquals(listOf("Foundations", "Closest to your goal", "Core skills", "Go further"), r.stages.map { it.title })
        assertEquals(listOf("n1", "n2", "n3"), r.stages[0].items.filterNot { it.known }.map { it.nodeId })
        assertTrue(r.stages[1].items.all { it.nodeId.removePrefix("n").toInt() % 3 == 0 })
        assertEquals(r.nodeIds.size, r.nodeIds.toSet().size)
        assertTrue(r.stages.flatMap { it.items }.single { it.nodeId == "n30" }.known)
    }

    @Test fun staticRoadmapListsEveryNodeOnce() {
        val nodes = listOf("b" to false, "a" to true, "c" to false).map { (id, core) -> node(id, id.uppercase(), "sql", core) }
        val r = StaticRoadmap.build("android", nodes)
        assertEquals(listOf("a", "b", "c"), r.nodeIds)
        assertEquals("static", r.source)
    }

    @Test fun explainerSplitsDescriptionAndCitesModel() {
        val n = SkillNode("kt", "android", "kt", "Kotlin", "Kotlin is a modern language. It runs on the JVM. Google recommends it for Android. You write less code.",
            core = true, resources = emptyList(), vector = SparseVector.EMPTY)
        val e = SkillExplainer.explain(n, CareerPath("android", "Android Developer", "role", "", 1), 0.93f,
            listOf("Android Developer" to 0.93f), listOf("Kotlin", "Spring Boot"), null)
        assertEquals("Kotlin is a modern language. It runs on the JVM.", e.whatItMeans)
        assertEquals(listOf("Google recommends it for Android.", "You write less code."), e.whatItInvolves.split("\n"))
        assertTrue(e.whyItMatters.contains("93%"))
        assertTrue(e.whyItMatters.contains("2 other roadmaps"))
        assertFalse(e.whyItMatters.contains("general skill"))
    }
}
