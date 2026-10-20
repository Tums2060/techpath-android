package com.techpath.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillGapTest {

    private fun node(id: String, title: String, vararg v: Float) =
        SkillNode(id, "backend", id, title, "desc", false, emptyList(), floatArrayOf(*v))

    private val nodes = listOf(
        node("sql", "SQL", 1f, 0f, 0f),
        node("git", "Git", 0f, 1f, 0f),
        node("docker", "Docker", 0f, 0f, 1f),
    )

    @Test fun similarityAtOrAboveThresholdIsKnown() {
        // cos(skill, sql) = 0.8 exactly, cos(skill, git) = 0.6
        val skill = floatArrayOf(0.8f, 0.6f, 0f)
        val known = SkillGap.knownNodeIds(nodes, emptySet(), listOf("databases"), listOf(skill), threshold = 0.80f)
        assertEquals(setOf("sql"), known)
    }

    @Test fun belowThresholdIsNotKnown() {
        val skill = floatArrayOf(0.79f, 0.61f, 0f)
        assertTrue(SkillGap.knownNodeIds(nodes, emptySet(), listOf("x"), listOf(skill), threshold = 0.80f).isEmpty())
    }

    @Test fun thresholdIsTunable() {
        val skill = floatArrayOf(0.79f, 0.61f, 0f)
        assertEquals(setOf("sql", "git"), SkillGap.knownNodeIds(nodes, emptySet(), listOf("x"), listOf(skill), threshold = 0.6f))
    }

    @Test fun chipsAndExactTitlesAreKnownWithoutVectors() {
        val known = SkillGap.knownNodeIds(nodes, chipIds = setOf("docker"), skillTexts = listOf("git"), skillVectors = emptyList(), threshold = 0.99f)
        assertEquals(setOf("docker", "git"), known)
    }

    @Test fun customCompareVectorIsUsed() {
        val titleVecs = mapOf("sql" to floatArrayOf(0f, 0f, 1f), "git" to floatArrayOf(1f, 0f, 0f), "docker" to floatArrayOf(0f, 1f, 0f))
        val known = SkillGap.knownNodeIds(nodes, emptySet(), listOf("x"), listOf(floatArrayOf(1f, 0f, 0f)), 0.8f) { titleVecs.getValue(it.id) }
        assertEquals(setOf("git"), known)
    }
}
