package com.techpath.ai

import com.techpath.data.SkillNode

/** All prompt text sent to the model lives here. */
object Prompts {

    // ---- Roadmap ordering (AI mode) ----

    const val ROADMAP_SYSTEM =
        "You are a career mentor for university technology students. You order a fixed list of " +
            "skills into a learning roadmap. Use only the node ids you are given. Never invent skills " +
            "or ids. Reply with JSON only."

    fun roadmapUser(year: Int, careerPath: String, knownSkills: Collection<String>, nodes: List<SkillNode>): String {
        val nodeList = nodes.joinToString(",\n") { """{"id":"${escape(it.id)}","title":"${escape(it.title)}"}""" }
        val known = if (knownSkills.isEmpty()) "none" else knownSkills.joinToString(", ")
        return """
            Student: year $year of 4 of a university computing degree.
            Career path: ${careerPath.replace('-', ' ')}
            Skills the student already knows: $known

            Order the skills below into 4 to 6 learning stages, from foundations to advanced, for this student.
            Put every node id in exactly one stage. Give each stage a short title and a one-line "why".

            Reply with strict JSON in exactly this shape:
            {"stages":[{"title":"Foundations","why":"one line","node_ids":["..."]}]}

            Skills:
            [
            $nodeList
            ]
        """.trimIndent()
    }

    fun roadmapRepair(badReply: String): String = """
        Your previous reply was not valid JSON in the required shape. Here it is:
        ${badReply.take(4000)}

        Reply again with strict JSON only, in exactly this shape and using only the node ids you were given:
        {"stages":[{"title":"Foundations","why":"one line","node_ids":["..."]}]}
    """.trimIndent()

    // ---- Skill explanation (AI mode) ----

    /** Grounding: the dataset description goes in the system message. */
    fun explainSystem(description: String): String =
        "You explain technology skills to university students clearly and accurately, in plain " +
            "language and a few short paragraphs. Do not include links. Base your answer on this " +
            "reference description:\n\n$description"

    /** Must stay identical to the template used in the fine-tuning dataset. Do not change it. */
    fun explainUser(title: String, careerPath: String): String =
        "Explain the skill \"$title\" for a student pursuing the ${careerPath.replace('-', ' ')} path: " +
            "what it is, what it involves and why it matters."

    private fun escape(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
