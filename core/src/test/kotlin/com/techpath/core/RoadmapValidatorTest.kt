package com.techpath.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadmapValidatorTest {

    private val allowed = (1..10).map { "n$it" }.toSet()

    private fun stage(title: String, vararg ids: String) =
        """{"title":"$title","goal":"g","skills":[${ids.joinToString(",") { """{"id":"$it","why":"because"}""" }}]}"""

    private fun roadmap(vararg stages: String) = """{"stages":[${stages.joinToString(",")}]}"""

    private val good = roadmap(stage("Foundations", "n1", "n2"), stage("Core", "n3", "n4"), stage("Tools", "n5", "n6"), stage("Projects", "n7", "n8"))

    private fun valid(r: RoadmapValidator.Result) = (r as RoadmapValidator.Result.Valid)

    @Test fun acceptsWellFormedRoadmap() {
        val r = valid(RoadmapValidator.validate(good, "backend", allowed, emptySet()))
        assertEquals(4, r.roadmap.stages.size)
        assertEquals("ai", r.roadmap.source)
        assertEquals(listOf("n1", "n2", "n3", "n4", "n5", "n6", "n7", "n8"), r.roadmap.nodeIds)
        assertEquals("because", r.roadmap.stages[0].items[0].why)
    }

    @Test fun dropsUnknownAndDuplicateIds() {
        val json = roadmap(stage("A", "n1", "BOGUS", "n2"), stage("B", "n3", "n1"), stage("C", "n4", "made-up"), stage("D", "n5", "n6"))
        val r = valid(RoadmapValidator.validate(json, "backend", allowed, emptySet()))
        assertEquals(listOf("n1", "n2", "n3", "n4", "n5", "n6"), r.roadmap.nodeIds)
        assertEquals(listOf("BOGUS", "made-up"), r.droppedIds)
    }

    @Test fun stageWithOnlyInvalidIdsIsDroppedAndCanMakeItInvalid() {
        val json = roadmap(stage("A", "n1", "n2"), stage("B", "x", "y"), stage("C", "n3", "n4"), stage("D", "n5", "n6"))
        assertTrue(RoadmapValidator.validate(json, "p", allowed, emptySet()) is RoadmapValidator.Result.Invalid)
    }

    @Test fun rejectsTooFewStages() {
        val json = roadmap(stage("A", "n1", "n2", "n3"), stage("B", "n4", "n5", "n6"))
        assertTrue(RoadmapValidator.validate(json, "p", allowed, emptySet()) is RoadmapValidator.Result.Invalid)
    }

    @Test fun mergesExtraStagesIntoLast() {
        val json = roadmap(*(1..8).map { stage("S$it", "n$it") }.toTypedArray())
        val r = valid(RoadmapValidator.validate(json, "p", allowed, emptySet()))
        assertEquals(6, r.roadmap.stages.size)
        assertEquals(listOf("n6", "n7", "n8"), r.roadmap.stages.last().items.map { it.nodeId })
    }

    @Test fun rejectsInvalidJson() {
        assertTrue(RoadmapValidator.validate("not json at all", "p", allowed, emptySet()) is RoadmapValidator.Result.Invalid)
        assertTrue(RoadmapValidator.validate("{\"stages\": [ {\"title\": ", "p", allowed, emptySet()) is RoadmapValidator.Result.Invalid)
        assertTrue(RoadmapValidator.validate("{\"stages\": \"oops\"}", "p", allowed, emptySet()) is RoadmapValidator.Result.Invalid)
    }

    @Test fun toleratesMarkdownFencesAndExtraFields() {
        val fenced = "```json\n" + good.replace("\"goal\":\"g\"", "\"goal\":\"g\",\"extra\":1") + "\n```"
        assertEquals(4, valid(RoadmapValidator.validate(fenced, "p", allowed, emptySet())).roadmap.stages.size)
    }

    @Test fun rejectsTooFewValidSkills() {
        val json = roadmap(stage("A", "n1"), stage("B", "n2"), stage("C", "n3"), stage("D", "x"), stage("E", "n4"))
        val big = (1..40).map { "n$it" }.toSet()
        assertTrue(RoadmapValidator.validate(json, "p", big, emptySet()) is RoadmapValidator.Result.Invalid)
    }

    @Test fun marksKnownAndAddsMissingKnownToFirstStage() {
        val r = valid(RoadmapValidator.validate(good, "p", allowed, knownIds = setOf("n2", "n9")))
        val first = r.roadmap.stages.first().items
        assertEquals("n9", first.first().nodeId)
        assertTrue(first.first().known)
        assertTrue(first.single { it.nodeId == "n2" }.known)
    }

    @Test fun parsesSkillExplanation() {
        val e = SkillExplanationParser.parse("""{"what_it_means":"a","what_it_involves":"b","why_it_matters":"c"}""")
        assertNotNull(e)
        assertEquals("c", e!!.whyItMatters)
        assertNull(SkillExplanationParser.parse("""{"what_it_means":"a"}"""))
        assertNull(SkillExplanationParser.parse("garbage"))
    }

    @Test fun staticRoadmapListsEveryNodeOnce() {
        val nodes = listOf("b" to false, "a" to true, "c" to false).map { (id, core) ->
            SkillNode(id, "p", id, id.uppercase(), "", core, emptyList(), floatArrayOf(1f))
        }
        val r = StaticRoadmap.build("p", nodes)
        assertEquals(listOf("a", "b", "c"), r.nodeIds)
        assertEquals("static", r.source)
    }
}
