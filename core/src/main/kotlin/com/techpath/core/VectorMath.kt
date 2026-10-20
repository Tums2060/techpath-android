package com.techpath.core

import java.util.Base64
import kotlin.math.sqrt

object VectorMath {

    fun dot(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "dimension mismatch: ${a.size} vs ${b.size}" }
        var s = 0f
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    fun norm(a: FloatArray): Float = sqrt(dot(a, a))

    /** Cosine similarity; 0 when either vector is all zeros. */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        val na = norm(a)
        val nb = norm(b)
        if (na == 0f || nb == 0f) return 0f
        return dot(a, b) / (na * nb)
    }

    fun normalize(a: FloatArray): FloatArray {
        val n = norm(a)
        return if (n == 0f) a.copyOf() else FloatArray(a.size) { a[it] / n }
    }

    /** Element-wise mean, then L2-normalised. Empty input gives null. */
    fun meanNormalized(vectors: List<FloatArray>): FloatArray? {
        if (vectors.isEmpty()) return null
        val out = FloatArray(vectors.first().size)
        for (v in vectors) for (i in out.indices) out[i] += v[i]
        return normalize(out)
    }

    data class Scored<T>(val item: T, val score: Float)

    /**
     * Top-k nearest neighbours by cosine similarity, best first. Ties keep the
     * input order, so results are deterministic.
     */
    fun <T> topK(query: FloatArray, items: List<T>, k: Int, vectorOf: (T) -> FloatArray): List<Scored<T>> {
        if (k <= 0) return emptyList()
        return items.mapIndexed { i, item -> Triple(i, item, cosine(query, vectorOf(item))) }
            .sortedWith(compareByDescending<Triple<Int, T, Float>> { it.third }.thenBy { it.first })
            .take(k)
            .map { Scored(it.second, it.third) }
    }
}

/** Decodes the pipeline's "float16-le-base64" vectors. */
object VectorCodec {

    fun decode(b64: String): FloatArray {
        val bytes = Base64.getDecoder().decode(b64)
        val out = FloatArray(bytes.size / 2)
        for (i in out.indices) {
            val bits = (bytes[2 * i].toInt() and 0xFF) or ((bytes[2 * i + 1].toInt() and 0xFF) shl 8)
            out[i] = halfToFloat(bits)
        }
        return out
    }

    fun encode(v: FloatArray): String {
        val bytes = ByteArray(v.size * 2)
        for (i in v.indices) {
            val h = floatToHalf(v[i])
            bytes[2 * i] = (h and 0xFF).toByte()
            bytes[2 * i + 1] = ((h shr 8) and 0xFF).toByte()
        }
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun halfToFloat(h: Int): Float {
        val sign = (h ushr 15) and 0x1
        val exp = (h ushr 10) and 0x1F
        val mant = h and 0x3FF
        val bits = when {
            exp == 0 && mant == 0 -> sign shl 31
            exp == 0 -> { // subnormal: renormalise
                var m = mant
                var e = -14
                while (m and 0x400 == 0) { m = m shl 1; e-- }
                (sign shl 31) or ((e + 127) shl 23) or ((m and 0x3FF) shl 13)
            }
            exp == 0x1F -> (sign shl 31) or (0xFF shl 23) or (mant shl 13)
            else -> (sign shl 31) or ((exp - 15 + 127) shl 23) or (mant shl 13)
        }
        return java.lang.Float.intBitsToFloat(bits)
    }

    /** Round-to-nearest float -> half (used by tests and for re-encoding). */
    fun floatToHalf(f: Float): Int {
        val bits = java.lang.Float.floatToIntBits(f)
        val sign = (bits ushr 16) and 0x8000
        val value = (bits and 0x7FFFFFFF) + 0x1000
        return when {
            value >= 0x47800000 -> sign or 0x7C00
            value >= 0x38800000 -> sign or ((value - 0x38000000) ushr 13)
            value < 0x33000000 -> sign
            else -> sign or Math.round(Math.abs(f) * 16777216f) // subnormal: units of 2^-24
        }
    }
}
