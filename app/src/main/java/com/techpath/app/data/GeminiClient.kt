package com.techpath.app.data

import com.techpath.core.TextEmbedder
import com.techpath.core.VectorMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiException(message: String, val code: Int = 0) : IOException(message)

/** One conversation turn sent to generateContent. */
data class Turn(val role: String, val text: String)

/**
 * Minimal Gemini REST client (generateContent + batchEmbedContents) on OkHttp.
 * The API key is passed per call so it is only ever read from secure storage.
 */
class GeminiClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private suspend fun post(path: String, apiKey: String, body: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$BASE/$path")
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody(jsonType))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body.string()
            if (!resp.isSuccessful) {
                val msg = runCatching {
                    json.parseToJsonElement(text).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                }.getOrNull()
                throw GeminiException(msg ?: "HTTP ${resp.code}", resp.code)
            }
            json.parseToJsonElement(text).jsonObject
        }
    }

    /** Returns the model's text (thought parts excluded). */
    suspend fun generate(
        apiKey: String,
        model: String,
        system: String,
        turns: List<Turn>,
        jsonOutput: Boolean = true,
        temperature: Double = 0.4,
    ): String {
        val body = buildJsonObject {
            putJsonObject("systemInstruction") { putJsonArray("parts") { addJsonObject { put("text", system) } } }
            putJsonArray("contents") {
                for (t in turns) addJsonObject {
                    put("role", t.role)
                    putJsonArray("parts") { addJsonObject { put("text", t.text) } }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", temperature)
                if (jsonOutput) put("responseMimeType", "application/json")
            }
        }
        val res = post("models/$model:generateContent", apiKey, body)
        val candidate = res["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: throw GeminiException("No response from model (it may have been blocked by safety filters)")
        val parts = candidate["content"]?.jsonObject?.get("parts")?.jsonArray ?: JsonArray(emptyList())
        val text = parts.joinToString("") { p ->
            val o = p.jsonObject
            val thought = o["thought"]?.jsonPrimitive?.boolean == true
            if (thought) "" else o["text"]?.jsonPrimitive?.content.orEmpty()
        }
        if (text.isBlank()) throw GeminiException("Empty response from model")
        return text
    }

    suspend fun embed(apiKey: String, model: String, dims: Int, texts: List<String>): List<FloatArray> {
        val out = ArrayList<FloatArray>(texts.size)
        for (chunk in texts.chunked(100)) {
            val body = buildJsonObject {
                putJsonArray("requests") {
                    for (t in chunk) addJsonObject {
                        put("model", "models/$model")
                        putJsonObject("content") { putJsonArray("parts") { addJsonObject { put("text", t) } } }
                        put("outputDimensionality", dims)
                    }
                }
            }
            val res = post("models/$model:batchEmbedContents", apiKey, body)
            res["embeddings"]!!.jsonArray.forEach { e ->
                val values = e.jsonObject["values"]!!.jsonArray
                out += VectorMath.normalize(FloatArray(values.size) { values[it].jsonPrimitive.float })
            }
        }
        return out
    }

    /** Adapter so the core retrieval code can use Gemini embeddings. */
    fun embedder(apiKey: String, model: String, dims: Int) = object : TextEmbedder {
        override suspend fun embed(texts: List<String>) = this@GeminiClient.embed(apiKey, model, dims, texts)
    }

    companion object {
        const val BASE = "https://generativelanguage.googleapis.com/v1beta"
    }
}
