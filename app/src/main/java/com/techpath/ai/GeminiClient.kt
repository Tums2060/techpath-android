package com.techpath.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
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

/** Google Gemini API (generateContent) over plain HTTPS. */
class GeminiClient(
    private val apiKey: suspend () -> String,
    private val json: Json,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .build(),
) : AiClient {

    companion object {
        /** See https://ai.google.dev/gemini-api/docs/models for current model codes. */
        const val MODEL = "gemini-3.8-flash"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    }

    override suspend fun complete(system: String, user: String, json: Boolean): String {
        val key = apiKey().trim()
        if (key.isEmpty()) throw NoApiKeyException()

        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { addJsonObject { put("text", system) } }
            }
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { addJsonObject { put("text", user) } }
                }
            }
            if (json) putJsonObject("generationConfig") { put("responseMimeType", "application/json") }
        }
        val request = Request.Builder()
            .url("$BASE_URL/$MODEL:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val raw = withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                val text = response.body.string()
                if (!response.isSuccessful) throw IOException("Gemini HTTP ${response.code}: ${text.take(200)}")
                text
            }
        }
        return extractText(this.json.parseToJsonElement(raw).jsonObject)
    }

    /** Joins the text parts of the first candidate, skipping any "thought" parts. */
    internal fun extractText(response: JsonObject): String {
        val parts = response["candidates"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?: throw IOException("Gemini response has no candidates")
        val text = parts.map { it.jsonObject }
            .filter { it["thought"]?.jsonPrimitive?.booleanOrNull != true }
            .mapNotNull { it["text"]?.jsonPrimitive?.contentOrNull }
            .joinToString("")
        if (text.isBlank()) throw IOException("Gemini response is empty")
        return text
    }
}
