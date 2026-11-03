package com.techpath.ai

import com.techpath.data.PathInfo
import com.techpath.data.SkillNode
import com.techpath.data.Stage
import com.techpath.data.StudyMode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadmapBuilderTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val path = PathInfo(id = "backend", name = "Backend", type = "role", nodeCount = 0)

    private fun node(id: String, title: String = "Skill $id", description: String = "About $id") =
        SkillNode(id = id, skillId = "s-$id", title = title, description = description)

    private fun nodes(n: Int) = (1..n).map { node("n$it") }

    /** Replies with the queued answers in order; throws when the queue is empty. */
    private class FakeAi(vararg replies: String) : AiClient {
        val queue = ArrayDeque(replies.toList())
        var calls = 0
        override suspend fun complete(system: String, user: String, json: Boolean): String {
            calls++
            return queue.removeFirstOrNull() ?: throw java.io.IOException("offline")
        }
    }

    private val builder = RoadmapBuilder(FakeAi(), json)

    // ---- validation of the AI reply ----

    @Test
    fun validate_dropsUnknownIds() {
        val stages = builder.validate("""{"stages":[{"title":"A","why":"w","node_ids":["n1","ghost","n2"]}]}""", nodes(2))!!
        assertEquals(listOf(Stage("A", "w", listOf("n1", "n2"))), stages)
    }

    @Test
    fun validate_removesDuplicates_firstOccurrenceWins() {
        val stages = builder.validate(
            """{"stages":[{"title":"A","node_ids":["n1","n1"]},{"title":"B","node_ids":["n1","n2"]}]}""", nodes(2),
        )!!
        assertEquals(listOf("n1"), stages[0].nodeIds)
        assertEquals(listOf("n2"), stages[1].nodeIds)
    }

    @Test
    fun validate_appendsMissingNodesToFinalStage_inFileOrder() {
        val stages = builder.validate("""{"stages":[{"title":"A","node_ids":["n3"]}]}""", nodes(4))!!
        assertEquals(2, stages.size)
        assertEquals(Stage(title = null, nodeIds = listOf("n1", "n2", "n4")), stages.last())
    }

    @Test
    fun validate_dropsStagesLeftEmpty() {
        val stages = builder.validate(
            """{"stages":[{"title":"Empty","node_ids":["ghost"]},{"title":"A","node_ids":["n1"]}]}""", nodes(1),
        )!!
        assertEquals(listOf("A"), stages.map { it.title })
    }

    @Test
    fun validate_returnsNullForInvalidJsonOrNoUsableStage() {
        assertNull(builder.validate("not json", nodes(2)))
        assertNull(builder.validate("""{"foo":1}""", nodes(2)))
        assertNull(builder.validate("""{"stages":[{"title":"A","node_ids":["ghost"]}]}""", nodes(2)))
    }

    @Test
    fun validate_acceptsMarkdownCodeFence() {
        val stages = builder.validate("```json\n{\"stages\":[{\"title\":\"A\",\"node_ids\":[\"n1\"]}]}\n```", nodes(1))
        assertEquals(listOf("n1"), stages!!.single().nodeIds)
    }

    // ---- STATIC staging ----

    @Test
    fun staticStages_keepFileOrderInStagesOfAboutEight() {
        val stages = builder.staticStages(nodes(26))
        assertEquals(listOf(7, 7, 6, 6), stages.map { it.nodeIds.size })
        assertEquals(nodes(26).map { it.id }, stages.flatMap { it.nodeIds })
        assertTrue(stages.all { it.title == null })
    }

    @Test
    fun staticStages_edgeCases() {
        assertEquals(listOf(8), builder.staticStages(nodes(8)).map { it.nodeIds.size })
        assertEquals(listOf(5, 4), builder.staticStages(nodes(9)).map { it.nodeIds.size })
        assertEquals(emptyList<Stage>(), builder.staticStages(emptyList()))
    }

    // ---- skill gap ----

    @Test
    fun retrieve_matchesKnownTitlesCaseAndPunctuationInsensitively() {
        val all = listOf(node("a", "Git & GitHub"), node("b", "C++"), node("c", "C"), node("d", "REST APIs"))
        val r = builder.retrieve(all, setOf("  git github ", "c++", "rest-apis"))
        assertEquals(setOf("a", "b", "d"), r.knownNodeIds)
        assertEquals(all, r.nodes) // known nodes are kept (shown as done), not hidden
    }

    @Test
    fun retrieve_capsRemainingNodes_keepsKnownAndFileOrder() {
        val all = (1..200).map { node("n$it", title = if (it % 2 == 0) "Kotlin topic $it" else "Misc $it") }
        val r = builder.retrieve(all, setOf("Misc 1"))
        assertEquals(RoadmapBuilder.MAX_RETRIEVED_NODES + 1, r.nodes.size)
        assertTrue("n1" in r.nodes.map { it.id })
        val positions = r.nodes.map { n -> all.indexOf(n) }
        assertEquals(positions.sorted(), positions)
    }

    // ---- end-to-end with fallback ----

    @Test
    fun build_aiReplyUsed() = runTest {
        val ai = FakeAi("""{"stages":[{"title":"Start","why":"w","node_ids":["n2","n1"]}]}""")
        val roadmap = RoadmapBuilder(ai, json).build(path, nodes(2), 2, emptySet(), StudyMode.AI)
        assertEquals(StudyMode.AI, roadmap.source)
        assertEquals(listOf("n2", "n1"), roadmap.stages.single().nodeIds)
    }

    @Test
    fun build_retriesOnceWithRepairPrompt() = runTest {
        val ai = FakeAi("oops", """{"stages":[{"title":"Start","node_ids":["n1","n2"]}]}""")
        val roadmap = RoadmapBuilder(ai, json).build(path, nodes(2), 2, emptySet(), StudyMode.AI)
        assertEquals(2, ai.calls)
        assertEquals(StudyMode.AI, roadmap.source)
    }

    @Test
    fun build_fallsBackToStaticAfterSecondFailureOrNetworkError() = runTest {
        val twiceBad = RoadmapBuilder(FakeAi("oops", "still oops"), json).build(path, nodes(3), 1, emptySet(), StudyMode.AI)
        assertEquals(StudyMode.STATIC, twiceBad.source)
        assertTrue(twiceBad.aiFailed)

        val offline = RoadmapBuilder(FakeAi(), json).build(path, nodes(3), 1, emptySet(), StudyMode.AI)
        assertTrue(offline.aiFailed)
        assertEquals(builder.staticStages(nodes(3)), offline.stages)
    }

    @Test
    fun build_staticModeNeverCallsAi() = runTest {
        val ai = FakeAi()
        val roadmap = RoadmapBuilder(ai, json).build(path, nodes(3), 1, emptySet(), StudyMode.STATIC)
        assertEquals(0, ai.calls)
        assertFalse(roadmap.aiFailed)
    }

    @Test
    fun cacheKey_ignoresOrderAndCaseOfKnownSkills() {
        assertEquals(builder.cacheKey("p", 2, setOf("Git", "SQL")), builder.cacheKey("p", 2, setOf("sql", "git")))
        assertFalse(builder.cacheKey("p", 2, emptySet()) == builder.cacheKey("p", 3, emptySet()))
    }
}
