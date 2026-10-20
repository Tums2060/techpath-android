package com.techpath.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.ln
import kotlin.math.sqrt

/** Anything that turns text into vectors in the same space as the assets. */
interface TextEmbedder {
    suspend fun embed(texts: List<String>): List<FloatArray>
}

/**
 * Kotlin port of `pipeline/embedders.py::HashEmbedder` ("hash-tfidf-v1"), the
 * offline fallback used when the assets were built without an embeddings API.
 * Any change here must be mirrored in Python; `HashEmbedderParityTest` checks
 * both produce the same vectors.
 */
class HashEmbedder(
    private val stopwords: Set<String>,
    private val expansions: Map<String, List<String>>,
    private val idf: DoubleArray,
) : TextEmbedder {

    val dims: Int get() = idf.size

    override suspend fun embed(texts: List<String>): List<FloatArray> = texts.map { embedOne(it) }

    fun embedOne(text: String): FloatArray {
        val v = DoubleArray(dims)
        for ((feature, weight) in features(text)) {
            val h = fnv1a32(feature)
            val bucket = (h % dims.toLong()).toInt()
            val sign = if ((h ushr 31) == 0L) 1.0 else -1.0
            v[bucket] += sign * ln(1.0 + weight) * idf[bucket]
        }
        var n = 0.0
        for (x in v) n += x * x
        n = sqrt(n)
        return FloatArray(dims) { if (n > 0) (v[it] / n).toFloat() else 0f }
    }

    /** Ordered like Python's dict insertion order, which does not affect the result. */
    fun features(text: String): Map<String, Double> {
        val feats = LinkedHashMap<String, Double>()
        fun add(k: String, w: Double) { feats[k] = (feats[k] ?: 0.0) + w }
        var prev: String? = null
        for (raw in normaliseChars(text).split(' ').filter { it.isNotEmpty() }) {
            if (raw.trim('+', '#').isEmpty() || raw in stopwords) { prev = null; continue }
            val tok = stem(raw)
            add("w:$tok", 1.0)
            if (prev != null) add("b:${prev}_$tok", 1.0)
            prev = tok
            val exp = expansions[raw] ?: expansions[tok]
            exp?.forEach { add("w:${stem(it)}", 0.5) }
        }
        return feats
    }

    companion object {
        fun fnv1a32(s: String): Long {
            var h = 0x811C9DC5L
            for (b in s.toByteArray(Charsets.UTF_8)) {
                h = h xor (b.toLong() and 0xFF)
                h = (h * 0x01000193L) and 0xFFFFFFFFL
            }
            return h
        }

        fun normaliseChars(text: String): String {
            val sb = StringBuilder(text.length)
            for (c0 in text) {
                val c = if (c0 in 'A'..'Z') c0 + 32 else c0
                sb.append(if (c in 'a'..'z' || c in '0'..'9' || c == '+' || c == '#') c else ' ')
            }
            return sb.toString()
        }

        fun stem(t: String): String = when {
            t.length > 4 && t.endsWith("ies") -> t.dropLast(3) + "y"
            t.length > 3 && t.endsWith("s") && !t.endsWith("ss") && !t.endsWith("us") &&
                !(t.endsWith("is") && t.length > 4) -> t.dropLast(1) // keep analysis, redis; apis -> api
            else -> t
        }

        /** Tokens as the embedder sees them; used for lexical title matching. */
        fun tokens(text: String, stopwords: Set<String> = emptySet()): Set<String> =
            normaliseChars(text).split(' ')
                .filter { it.isNotEmpty() && it.trim('+', '#').isNotEmpty() && it !in stopwords }
                .map { stem(it) }
                .toSet()

        /** Builds an embedder from `hash_lexicon.json` and the IDF in careers.json. */
        fun fromLexicon(lexiconJson: String, idf: List<Double>): HashEmbedder {
            val root = Json.parseToJsonElement(lexiconJson).jsonObject
            val stop = root.getValue("stopwords").jsonArray.map { it.jsonPrimitive.content }.toSet()
            val exp = root.getValue("expansions").jsonObject.mapValues { (_, v) ->
                v.jsonArray.map { it.jsonPrimitive.content }
            }
            return HashEmbedder(stop, exp, idf.toDoubleArray())
        }
    }
}
