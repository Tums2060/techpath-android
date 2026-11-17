package com.techpath.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.boolean
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The Kotlin engine must reproduce scikit-learn: same cleaned text, same TF-IDF
 * vector, same top-3 career paths and probabilities, same top-5 closest skills.
 * Expected values come from `pipeline/train_model.py` (model_parity_fixture.json).
 */
class ModelParityTest {

    private val assets = File("../app/src/main/assets")

    private val model: MlModel by lazy {
        val dir = File(assets, "model")
        MlModel.load(
            File(dir, "model_meta.json").readText(),
            File(dir, "vocab.txt").readText(),
            File(dir, "idf.bin").readBytes(),
            File(dir, "coef.bin").readBytes(),
        )
    }

    private val nodes: List<SkillNode> by lazy {
        val vectors = SkillVectors.read(File(assets, "model/skill_vectors.bin").readBytes())
        Json.parseToJsonElement(File(assets, "nodes.json").readText()).jsonObject.getValue("nodes").jsonArray.mapIndexed { i, it ->
            val o = it.jsonObject
            fun s(k: String) = o.getValue(k).jsonPrimitive.content
            SkillNode(s("id"), s("path"), s("slug"), s("title"), s("description"), o.getValue("core").jsonPrimitive.boolean,
                emptyList(), vectors[i])
        }
    }

    private val cases by lazy {
        val text = javaClass.classLoader.getResource("model_parity_fixture.json")!!.readText()
        Json.parseToJsonElement(text).jsonObject.getValue("cases").jsonArray.map { it.jsonObject }
    }

    @Test fun cleanedTextMatchesPython() {
        for (c in cases) {
            assertEquals(c.getValue("cleaned").jsonPrimitive.content, TextCleaner.clean(c.getValue("text").jsonPrimitive.content))
        }
    }

    @Test fun tfidfMatchesScikitLearn() {
        for (c in cases) {
            val expected = c.getValue("tfidf").jsonObject.mapKeys { it.key.toInt() }.mapValues { it.value.jsonPrimitive.float }
            val got = model.vectorizer.transform(c.getValue("text").jsonPrimitive.content)
            assertEquals("features for ${c["text"]}", expected.keys.sorted(), got.indices.toList())
            got.indices.forEachIndexed { k, i -> assertEquals(expected.getValue(i), got.values[k], 1e-5f) }
        }
    }

    @Test fun top3PathsAndProbabilitiesMatch() {
        for (c in cases) {
            val expected = c.getValue("top3").jsonArray.map { it.jsonObject }
            val got = model.classifier.topK(model.vectorizer.transform(c.getValue("text").jsonPrimitive.content), 3)
            assertEquals(expected.map { it.getValue("path").jsonPrimitive.content }, got.map { it.path })
            expected.zip(got).forEach { (e, g) -> assertEquals(e.getValue("p").jsonPrimitive.float, g.probability, 2e-3f) }
        }
    }

    @Test fun top5ClosestSkillsMatch() {
        for (c in cases) {
            val q = model.vectorizer.transform(c.getValue("text").jsonPrimitive.content)
            val topPath = model.classifier.topK(q, 1).single().path
            val expected = c.getValue("top5_skills").jsonArray.map { it.jsonPrimitive.content }
            val got = Retriever.rank(q, nodes.filter { it.path == topPath }).take(5).map { it.item.title }
            assertEquals("skills for ${c["text"]}", expected, got)
        }
    }

    @Test fun modelMetadataIsConsistent() {
        assertEquals(86, model.meta.classes.size)
        assertEquals(model.meta.nFeatures, model.classifier.nFeatures)
        assertEquals(model.meta.nFeatures, model.vectorizer.nFeatures)
        assertEquals(30.0, model.meta.logisticRegression.c, 0.0)
        assertEquals(listOf(1, 2), model.meta.tfidf.ngramRange)
    }

    @Test fun androidInterestPredictsAndroid() {
        val m = CareerMatcher.topMatches(model, "I want to build mobile apps for Android phones",
            model.meta.classes.map { CareerPath(it, it, "role", "", 0) }, { p -> nodes.filter { it.path == p } })
        assertEquals("android", m.first().career.id)
        assertEquals(5, m.first().closestSkills.size)
    }
}
