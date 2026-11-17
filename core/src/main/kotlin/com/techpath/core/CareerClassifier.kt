package com.techpath.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp

/**
 * The notebook's multinomial Logistic Regression (C = 30) at predict time:
 * P(path | x) = softmax(W·x + b), with W exported as float16 in
 * [n_features][n_classes] order so a sparse query only touches its own rows.
 */
class CareerClassifier(
    val classes: List<String>,
    /** float16 bit patterns, row-major [n_features][n_classes]. */
    private val coef: ShortArray,
    private val intercept: FloatArray,
) {
    private val nClasses = classes.size
    private val classIndex = classes.withIndex().associate { (i, c) -> c to i }

    init {
        require(intercept.size == nClasses) { "intercept size ${intercept.size} != classes $nClasses" }
        require(coef.size % nClasses == 0) { "coef size is not a multiple of the class count" }
    }

    val nFeatures: Int get() = coef.size / nClasses

    fun indexOf(path: String): Int = classIndex[path] ?: -1

    /** Class probabilities in [classes] order. */
    fun predictProba(x: SparseVector): FloatArray {
        val logits = DoubleArray(nClasses) { intercept[it].toDouble() }
        for (k in x.indices.indices) {
            val base = x.indices[k] * nClasses
            val v = x.values[k].toDouble()
            for (c in 0 until nClasses) logits[c] += v * HALF[coef[base + c].toInt() and 0xFFFF]
        }
        val max = logits.max()
        var sum = 0.0
        for (c in logits.indices) { logits[c] = exp(logits[c] - max); sum += logits[c] }
        return FloatArray(nClasses) { (logits[it] / sum).toFloat() }
    }

    data class Prediction(val path: String, val probability: Float)

    /** The [k] most likely career paths, best first (ties keep class order). */
    fun topK(x: SparseVector, k: Int = 3): List<Prediction> {
        val p = predictProba(x)
        return p.indices.sortedWith(compareByDescending<Int> { p[it] }.thenBy { it })
            .take(k).map { Prediction(classes[it], p[it]) }
    }

    companion object {
        /** Lookup table: float16 bit pattern -> float. */
        private val HALF = FloatArray(65536) { halfToFloat(it) }

        fun halfToFloat(h: Int): Float {
            val sign = (h ushr 15) and 0x1
            val exp = (h ushr 10) and 0x1F
            val mant = h and 0x3FF
            val bits = when {
                exp == 0 && mant == 0 -> sign shl 31
                exp == 0 -> {
                    var m = mant; var e = -14
                    while (m and 0x400 == 0) { m = m shl 1; e-- }
                    (sign shl 31) or ((e + 127) shl 23) or ((m and 0x3FF) shl 13)
                }
                exp == 0x1F -> (sign shl 31) or (0xFF shl 23) or (mant shl 13)
                else -> (sign shl 31) or ((exp - 15 + 127) shl 23) or (mant shl 13)
            }
            return java.lang.Float.intBitsToFloat(bits)
        }
    }
}

/** Contents of `model/model_meta.json`. */
@Serializable
data class ModelMeta(
    val version: String,
    val model: String,
    val classes: List<String>,
    @SerialName("n_features") val nFeatures: Int,
    val intercept: List<Double>,
    @SerialName("stop_words") val stopWords: List<String>,
    val tfidf: TfidfParams,
    @SerialName("logistic_regression") val logisticRegression: LrParams,
    @SerialName("fit_on") val fitOn: String = "all",
    val rows: Int = 0,
    val metrics: Metrics,
) {
    @Serializable
    data class TfidfParams(
        @SerialName("ngram_range") val ngramRange: List<Int>,
        @SerialName("min_df") val minDf: Int,
        @SerialName("sublinear_tf") val sublinearTf: Boolean,
    )

    @Serializable
    data class LrParams(@SerialName("C") val c: Double, @SerialName("max_iter") val maxIter: Int)

    @Serializable
    data class Metrics(
        @SerialName("test_accuracy") val testAccuracy: Double,
        @SerialName("test_macro_f1") val testMacroF1: Double,
        @SerialName("test_top3_accuracy") val testTop3Accuracy: Double,
        @SerialName("cv_macro_f1") val cvMacroF1: Double? = null,
        @SerialName("train_rows") val trainRows: Int = 0,
        @SerialName("test_rows") val testRows: Int = 0,
    )
}

/** The trained ML engine: vectoriser + classifier, loaded from the bundled model files. */
class MlModel(val meta: ModelMeta, val vectorizer: TfidfVectorizer, val classifier: CareerClassifier) {

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun load(metaJson: String, vocabText: String, idfBytes: ByteArray, coefBytes: ByteArray): MlModel {
            val meta = json.decodeFromString(ModelMeta.serializer(), metaJson)
            val vocab = HashMap<String, Int>(meta.nFeatures * 2)
            var i = 0
            for (line in vocabText.lineSequence()) if (line.isNotEmpty()) vocab[line] = i++
            require(i == meta.nFeatures) { "vocab has $i terms, meta says ${meta.nFeatures}" }

            val idf = FloatArray(meta.nFeatures)
            ByteBuffer.wrap(idfBytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(idf)
            val coef = ShortArray(meta.nFeatures * meta.classes.size)
            require(coefBytes.size == coef.size * 2) { "coef.bin has ${coefBytes.size} bytes, expected ${coef.size * 2}" }
            ByteBuffer.wrap(coefBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(coef)

            val vec = TfidfVectorizer(
                vocab, idf, meta.stopWords.toSet(),
                ngramMin = meta.tfidf.ngramRange[0], ngramMax = meta.tfidf.ngramRange[1],
                sublinearTf = meta.tfidf.sublinearTf,
            )
            val clf = CareerClassifier(meta.classes, coef, FloatArray(meta.classes.size) { meta.intercept[it].toFloat() })
            return MlModel(meta, vec, clf)
        }
    }
}
