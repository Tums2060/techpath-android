package com.techpath.core

/**
 * Every prompt TechPath sends to the LLM lives in this file so they are easy to
 * tune. Responses are requested as strict JSON and validated by
 * [RoadmapValidator] / [SkillExplanationParser].
 */
object Prompts {

    const val ROADMAP_SYSTEM = """You are a careful career-skills adviser for university technology students in Kenya.
You order and explain skills from a curated roadmap knowledge base (roadmap.sh).
Rules:
- Use ONLY the skill ids given in the CANDIDATE SKILLS list. Never invent ids or skills.
- You may leave out candidates that do not fit the student's goal.
- Produce 4 to 6 stages that move from fundamentals to job-readiness, for example:
  Foundations -> Core Skills -> Tools & Practice -> Projects -> Job-Ready.
- Within each stage, order skills so prerequisites come first.
- Skills marked [KNOWN] are ones the student already has: place them where they belong (usually early);
  their "why" should say briefly how they build on it.
- Each "why" is ONE short sentence (max 20 words) explaining why the skill comes at this point.
- Adjust depth to the year of study: year 1-2 students need more fundamentals, year 4-5 more job-ready work.
- Reply with JSON only, no markdown, matching exactly:
{"stages":[{"title":"string","goal":"string","skills":[{"id":"string","why":"string"}]}]}"""

    fun roadmapUser(
        careerName: String,
        goal: String,
        yearOfStudy: Int,
        knownSkills: List<String>,
        candidates: List<SkillNode>,
        knownIds: Set<String>,
    ): String = buildString {
        appendLine("CAREER PATH: $careerName")
        appendLine("STUDENT GOAL: ${goal.ifBlank { careerName }}")
        appendLine("YEAR OF STUDY: $yearOfStudy (of 5)")
        appendLine("SKILLS THE STUDENT SAYS THEY HAVE: ${knownSkills.joinToString().ifBlank { "none given" }}")
        appendLine()
        appendLine("CANDIDATE SKILLS (id | title | short description):")
        for (n in candidates) {
            val tag = if (n.id in knownIds) " [KNOWN]" else if (n.core) " [CORE]" else ""
            appendLine("${n.id} | ${n.title}$tag | ${n.description.shorten(160)}")
        }
        appendLine()
        append("Return the JSON roadmap now.")
    }

    const val REPAIR = "Your previous reply was not valid JSON in the required shape, or used ids that are not in the list. " +
        "Reply again with JSON only, exactly {\"stages\":[{\"title\":\"\",\"goal\":\"\",\"skills\":[{\"id\":\"\",\"why\":\"\"}]}]}, " +
        "4 to 6 stages, using only the given ids."

    const val SKILL_SYSTEM = """You explain one technology skill to a university student in plain, friendly English.
Base your answer on the REFERENCE DESCRIPTION; do not contradict it and do not invent facts, tools or statistics.
Reply with JSON only:
{"what_it_means":"2-3 sentences defining the skill","what_it_involves":"3-5 short bullet-style sentences separated by \n about what a learner actually does or practises","why_it_matters":"2-3 sentences on why it matters for the named career"}"""

    fun skillUser(title: String, description: String, careerName: String, goal: String): String =
        "SKILL: $title\nCAREER: $careerName\nSTUDENT GOAL: ${goal.ifBlank { careerName }}\n" +
            "REFERENCE DESCRIPTION: ${description.shorten(1500)}"

    private fun String.shorten(max: Int) = if (length <= max) this else take(max - 1).trimEnd() + "…"
}
