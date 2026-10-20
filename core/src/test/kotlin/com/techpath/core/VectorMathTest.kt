package com.techpath.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorMathTest {

    private fun node(id: String, vararg v: Float, core: Boolean = false) =
        SkillNode(id, "p", id, id, "desc", core, emptyList(), floatArrayOf(*v))

    @Test fun cosineOfIdenticalVectorsIsOne() {
        assertEquals(1f, VectorMath.cosine(floatArrayOf(1f, 2f, 3f), floatArrayOf(1f, 2f, 3f)), 1e-6f)
    }

    @Test fun cosineOfOrthogonalAndOppositeVectors() {
        assertEquals(0f, VectorMath.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 5f)), 1e-6f)
        assertEquals(-1f, VectorMath.cosine(floatArrayOf(1f, 1f), floatArrayOf(-2f, -2f)), 1e-6f)
    }

    @Test fun cosineIgnoresMagnitudeAndHandlesZeroVector() {
        assertEquals(VectorMath.cosine(floatArrayOf(1f, 2f), floatArrayOf(3f, 1f)),
            VectorMath.cosine(floatArrayOf(10f, 20f), floatArrayOf(3f, 1f)), 1e-6f)
        assertEquals(0f, VectorMath.cosine(floatArrayOf(0f, 0f), floatArrayOf(1f, 1f)), 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun dimensionMismatchThrows() { VectorMath.cosine(floatArrayOf(1f), floatArrayOf(1f, 2f)) }

    @Test fun topKReturnsBestFirstAndRespectsK() {
        val items = listOf(node("a", 1f, 0f), node("b", 0f, 1f), node("c", 0.9f, 0.1f), node("d", -1f, 0f))
        val top = VectorMath.topK(floatArrayOf(1f, 0f), items, 2) { it.vector }
        assertEquals(listOf("a", "c"), top.map { it.item.id })
        assertTrue(top[0].score >= top[1].score)
        assertEquals(4, VectorMath.topK(floatArrayOf(1f, 0f), items, 10) { it.vector }.size)
        assertTrue(VectorMath.topK(floatArrayOf(1f, 0f), items, 0) { it.vector }.isEmpty())
    }

    @Test fun topKTiesKeepInputOrder() {
        val items = listOf(node("x", 1f, 0f), node("y", 2f, 0f), node("z", 3f, 0f))
        assertEquals(listOf("x", "y", "z"), VectorMath.topK(floatArrayOf(1f, 0f), items, 3) { it.vector }.map { it.item.id })
    }

    @Test fun retrieverKeepsCoreAndMustIncludeNodes() {
        val nodes = listOf(
            node("near1", 1f, 0f), node("near2", 0.95f, 0.05f), node("near3", 0.9f, 0.1f),
            node("far-core", -1f, 0.1f, core = true), node("far-known", 0f, -1f), node("far", -1f, -0.1f),
        )
        val got = Retriever.retrieve(floatArrayOf(1f, 0f), nodes, k = 2, maxCore = 1, mustInclude = setOf("far-known")).map { it.item.id }
        assertEquals(setOf("near1", "near2", "far-core", "far-known"), got.toSet())
        assertEquals("near1", got.first())
    }

    @Test fun careerMatcherDirectAndTopK() {
        val careers = listOf(
            CareerPath("backend", "Backend Developer", "role", "", 1, floatArrayOf(1f, 0f)),
            CareerPath("cyber-security", "Cyber Security", "role", "", 1, floatArrayOf(0f, 1f)),
            CareerPath("android", "Android Developer", "role", "", 1, floatArrayOf(0.7f, 0.7f)),
        )
        assertEquals("backend", CareerMatcher.directMatch("Backend", careers)?.id)
        assertEquals("cyber-security", CareerMatcher.directMatch(" cyber security ", careers)?.id)
        assertEquals("android", CareerMatcher.directMatch("android developer", careers)?.id)
        assertEquals(null, CareerMatcher.directMatch("I want to build apps for banks", careers))
        val top = CareerMatcher.topMatches(floatArrayOf(0.1f, 1f), careers, 2)
        assertEquals(listOf("cyber-security", "android"), top.map { it.career.id })
    }

    @Test fun float16RoundTrip() {
        val v = floatArrayOf(0f, 1f, -1f, 0.5f, 0.123f, -0.0421f, 1e-5f, 65504f)
        val back = VectorCodec.decode(VectorCodec.encode(v))
        for (i in v.indices) assertEquals(v[i], back[i], Math.abs(v[i]) * 1e-3f + 1e-7f)
    }

    @Test fun decodesPythonFloat16() {
        // numpy: np.array([1.0, -2.0, 0.5], dtype='<f2').tobytes() -> base64 "ADwAwAA4"
        val v = VectorCodec.decode("ADwAwAA4")
        assertEquals(listOf(1f, -2f, 0.5f), v.toList())
    }

    @Test fun relevanceIsClampedPercent() {
        assertEquals(100, Relevance.percent(floatArrayOf(1f, 0f), floatArrayOf(2f, 0f)))
        assertEquals(0, Relevance.percent(floatArrayOf(1f, 0f), floatArrayOf(-1f, 0f)))
    }
}
