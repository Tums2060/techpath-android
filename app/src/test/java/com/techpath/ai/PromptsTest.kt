package com.techpath.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class PromptsTest {
    @Test
    fun explainUser_matchesFineTuningTemplate() {
        assertEquals(
            "Explain the skill \"Acting / Tool Invocation\" for a student pursuing the ai agents path: " +
                "what it is, what it involves and why it matters.",
            Prompts.explainUser("Acting / Tool Invocation", "ai-agents"),
        )
    }
}
