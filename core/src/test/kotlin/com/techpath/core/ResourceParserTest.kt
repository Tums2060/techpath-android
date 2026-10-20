package com.techpath.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceParserTest {

    @Test fun parsesPipeSeparatedEntries() {
        val r = ResourceParser.parse("What is REST? (https://a.com/rest) | REST Tutorial (https://b.org/x?y=1)", "article")
        assertEquals(2, r.size)
        assertEquals(Resource("What is REST?", "https://a.com/rest", "article"), r[0])
        assertEquals("https://b.org/x?y=1", r[1].url)
    }

    @Test fun keepsPipesInsideTitles() {
        val r = ResourceParser.parse("Responsible AI | Microsoft Learn (https://learn.microsoft.com/ai) | Next (https://n.io)", "docs")
        assertEquals(listOf("Responsible AI | Microsoft Learn", "Next"), r.map { it.title })
    }

    @Test fun emptyAndMissingCellsGiveEmptyList() {
        assertTrue(ResourceParser.parse(null, "video").isEmpty())
        assertTrue(ResourceParser.parse("", "video").isEmpty())
        assertTrue(ResourceParser.parse("   ", "video").isEmpty())
        assertTrue(ResourceParser.parse("Just a title with no link", "video").isEmpty())
    }

    @Test fun repairsTruncatedScheme() {
        assertEquals("https://x.dev/a", ResourceParser.parse("X (ttps://x.dev/a)", "course").single().url)
    }

    @Test fun urlsWithParenthesesInTitle() {
        val r = ResourceParser.parse("Tree of Thoughts (ToT) guide (https://t.ai/tot)", "article")
        assertEquals("Tree of Thoughts (ToT) guide", r.single().title)
    }
}
