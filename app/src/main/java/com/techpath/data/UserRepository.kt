package com.techpath.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * Everything the student changes, stored in DataStore as a few small JSON blobs.
 * To persist something new: add a key below and a pair of read/update functions.
 */
class UserRepository(private val store: DataStore<Preferences>, private val json: Json) {

    private object Keys {
        val settings = stringPreferencesKey("settings")
        val progress = stringPreferencesKey("progress")          // pathId -> ticked node ids
        val opportunities = stringPreferencesKey("opportunities") // opportunity id -> status
        val roadmapCache = stringPreferencesKey("roadmap_cache")  // cache key -> AI roadmap
        val explanationCache = stringPreferencesKey("explanations") // "path|node" -> text
    }

    val settings: Flow<UserSettings> = blob(Keys.settings, UserSettings())
    val progress: Flow<Map<String, Set<String>>> = blob(Keys.progress, emptyMap())
    val opportunityStatus: Flow<Map<String, ApplicationStatus>> = blob(Keys.opportunities, emptyMap())

    suspend fun updateSettings(change: (UserSettings) -> UserSettings) = update(Keys.settings, UserSettings(), change)

    suspend fun toggleDone(pathId: String, nodeId: String) =
        update(Keys.progress, emptyMap<String, Set<String>>()) { all ->
            val ticked = all[pathId].orEmpty()
            all + (pathId to if (nodeId in ticked) ticked - nodeId else ticked + nodeId)
        }

    suspend fun resetProgress() = store.edit { it.remove(Keys.progress) }

    /** Pass null to un-save. */
    suspend fun setOpportunityStatus(id: String, status: ApplicationStatus?) =
        update(Keys.opportunities, emptyMap<String, ApplicationStatus>()) { all ->
            if (status == null) all - id else all + (id to status)
        }

    suspend fun cachedRoadmap(key: String): Roadmap? =
        blob(Keys.roadmapCache, emptyMap<String, Roadmap>()).first()[key]

    /** Keeps one cached roadmap: a new path, year or set of known skills replaces it. */
    suspend fun cacheRoadmap(key: String, roadmap: Roadmap) =
        update(Keys.roadmapCache, emptyMap<String, Roadmap>()) { mapOf(key to roadmap) }

    suspend fun cachedExplanation(pathId: String, nodeId: String): String? =
        blob(Keys.explanationCache, emptyMap<String, String>()).first()["$pathId|$nodeId"]

    suspend fun cacheExplanation(pathId: String, nodeId: String, text: String) =
        update(Keys.explanationCache, emptyMap<String, String>()) { it + ("$pathId|$nodeId" to text) }

    // ---- JSON blob helpers ----

    private inline fun <reified T> blob(key: Preferences.Key<String>, default: T): Flow<T> {
        val serializer = serializer<T>()
        return store.data.map { prefs -> decode(prefs[key], serializer) ?: default }
    }

    private suspend inline fun <reified T> update(key: Preferences.Key<String>, default: T, crossinline change: (T) -> T) {
        val serializer = serializer<T>()
        store.edit { prefs ->
            val current = decode(prefs[key], serializer) ?: default
            prefs[key] = json.encodeToString(serializer, change(current))
        }
    }

    private fun <T> decode(raw: String?, serializer: KSerializer<T>): T? =
        raw?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
}
