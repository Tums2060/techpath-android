package com.techpath.core

import kotlin.math.ln
import kotlin.math.sqrt

/** A sparse vector with sorted, unique feature indices. */
class SparseVector(val indices: IntArray, val values: FloatArray) {
    init { require(indices.size == values.size) }

    val size: Int get() = indices.size
    fun isEmpty() = indices.isEmpty()

    fun dot(other: SparseVector): Float {
        var i = 0; var j = 0; var s = 0f
        while (i < indices.size && j < other.indices.size) {
            val a = indices[i]; val b = other.indices[j]
            when {
                a == b -> { s += values[i] * other.values[j]; i++; j++ }
                a < b -> i++
                else -> j++
            }
        }
        return s
    }

    fun norm(): Float { var s = 0f; for (v in values) s += v * v; return sqrt(s) }

    /** Cosine similarity; 0 when either vector is empty. */
    fun cosine(other: SparseVector): Float {
        val n = norm() * other.norm()
        return if (n == 0f) 0f else dot(other) / n
    }

    fun normalized(): SparseVector {
        val n = norm()
        return if (n == 0f) this else SparseVector(indices, FloatArray(values.size) { values[it] / n })
    }

    companion object {
        val EMPTY = SparseVector(IntArray(0), FloatArray(0))

        fun of(map: Map<Int, Float>): SparseVector {
            val keys = map.keys.sorted().toIntArray()
            return SparseVector(keys, FloatArray(keys.size) { map.getValue(keys[it]) })
        }

        /** Element-wise sum of several vectors, L2-normalised. */
        fun sumNormalized(vectors: List<SparseVector>): SparseVector {
            val acc = HashMap<Int, Float>()
            for (v in vectors) for (k in v.indices.indices) acc.merge(v.indices[k], v.values[k], Float::plus)
            return of(acc).normalized()
        }
    }
}

/** Reads `model/skill_vectors.bin` (precomputed `tfidf.transform(final["text"])`). */
object SkillVectors {
    fun read(bytes: ByteArray): List<SparseVector> {
        val buf = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val n = buf.int
        return List(n) {
            val nnz = buf.short.toInt() and 0xFFFF
            val idx = IntArray(nnz) { buf.short.toInt() and 0xFFFF }
            val vals = FloatArray(nnz) { CareerClassifier.halfToFloat(buf.short.toInt() and 0xFFFF) }
            SparseVector(idx, vals)
        }
    }
}

/**
 * Kotlin port of the notebook's scikit-learn `TfidfVectorizer(stop_words="english",
 * ngram_range=(1, 2), min_df=2, sublinear_tf=True)` at transform time, using the
 * fitted vocabulary and IDF weights exported by `pipeline/train_model.py`.
 *
 * Steps (as in scikit-learn): tokens = `\b\w\w+\b` on the cleaned text ->
 * remove English stop words -> unigrams + bigrams -> keep terms in the
 * vocabulary -> tf = 1 + ln(count) -> tf * idf -> L2 normalise.
 */
class TfidfVectorizer(
    private val vocabulary: Map<String, Int>,
    private val idf: FloatArray,
    private val stopWords: Set<String>,
    private val ngramMin: Int = 1,
    private val ngramMax: Int = 2,
    private val sublinearTf: Boolean = true,
) {
    val nFeatures: Int get() = idf.size

    fun tokens(cleaned: String): List<String> =
        TOKEN.findAll(cleaned).map { it.value }.filter { it !in stopWords }.toList()

    fun terms(cleaned: String): List<String> {
        val toks = tokens(cleaned)
        val out = ArrayList<String>()
        for (n in ngramMin..ngramMax) {
            for (i in 0..toks.size - n) out += if (n == 1) toks[i] else toks.subList(i, i + n).joinToString(" ")
        }
        return out
    }

    /** TF-IDF vector of text that has already been through [TextCleaner.clean]. */
    fun transformCleaned(cleaned: String): SparseVector {
        val counts = HashMap<Int, Int>()
        for (t in terms(cleaned)) vocabulary[t]?.let { counts.merge(it, 1, Int::plus) }
        if (counts.isEmpty()) return SparseVector.EMPTY
        val weights = counts.mapValues { (idx, c) ->
            val tf = if (sublinearTf) 1.0 + ln(c.toDouble()) else c.toDouble()
            (tf * idf[idx]).toFloat()
        }
        return SparseVector.of(weights).normalized()
    }

    /** Cleans [text] the notebook's way, then vectorises it. */
    fun transform(text: String): SparseVector = transformCleaned(TextCleaner.clean(text))

    companion object {
        /** scikit-learn's `(?u)\b\w\w+\b`: maximal runs of 2+ Unicode word characters. */
        private val TOKEN = Regex("""[\p{L}\p{N}_]{2,}""")
    }
}
