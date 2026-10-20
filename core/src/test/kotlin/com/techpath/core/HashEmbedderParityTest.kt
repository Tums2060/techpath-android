package com.techpath.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** The Kotlin hash embedder must reproduce the Python pipeline's vectors. */
class HashEmbedderParityTest {

    private fun resource(name: String) = javaClass.classLoader!!.getResource(name)!!.readText()

    @Test fun matchesPythonFixture() {
        val fx = Json.parseToJsonElement(resource("hash_parity_fixture.json")).jsonObject
        val idf = fx.getValue("idf").jsonArray.map { it.jsonPrimitive.double }
        assertEquals(fx.getValue("dims").jsonPrimitive.int, idf.size)
        val lexicon = File("../pipeline/data/hash_lexicon.json").readText()
        val e = HashEmbedder.fromLexicon(lexicon, idf)
        for (case in fx.getValue("cases").jsonArray) {
            val text = case.jsonObject.getValue("text").jsonPrimitive.content
            val expected = case.jsonObject.getValue("vector").jsonArray.map { it.jsonPrimitive.double.toFloat() }
            val got = e.embedOne(text)
            for (i in expected.indices) assertEquals("'$text' dim $i", expected[i], got[i], 1e-5f)
        }
    }

    @Test fun stemmingAndTokens() {
        assertEquals("library", HashEmbedder.stem("libraries"))
        assertEquals("api", HashEmbedder.stem("apis"))
        assertEquals("class", HashEmbedder.stem("class"))
        assertEquals(setOf("c++", "c#", "rest", "api"), HashEmbedder.tokens("C++, C# & REST APIs"))
    }

    @Test fun similarTextsScoreHigherThanUnrelated() = runBlocking {
        val lexicon = File("../pipeline/data/hash_lexicon.json").readText()
        val e = HashEmbedder.fromLexicon(lexicon, List(256) { 1.0 })
        val (a, b, c) = e.embed(listOf("mobile banking app", "android apps for banks", "baking bread recipes"))
        assert(VectorMath.cosine(a, b) > VectorMath.cosine(a, c))
    }
}
