package com.techpath.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.techpath.core.StudyMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "techpath_settings")

/** Everything the student entered during onboarding. Kept only on this device. */
data class Profile(
    val name: String = "",
    val year: Int = 1,
    val interest: String = "",
    val careerPath: String? = null,
    val knownSkills: Set<String> = emptySet(),
    val knownChipIds: Set<String> = emptySet(),
)

data class AppSettings(
    val onboarded: Boolean = false,
    val participantId: String = "",
    val mode: StudyMode = StudyMode.AI,
    /** "Already known" TF-IDF similarity threshold; null = SkillGap.DEFAULT_THRESHOLD. */
    val knownThreshold: Float? = null,
    val profile: Profile = Profile(),
)

class SettingsRepository(private val context: Context) {

    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val participant = stringPreferencesKey("participant_id")
        val mode = stringPreferencesKey("study_mode")
        val threshold = floatPreferencesKey("known_threshold")
        val name = stringPreferencesKey("name")
        val year = intPreferencesKey("year")
        val interest = stringPreferencesKey("interest")
        val path = stringPreferencesKey("career_path")
        val skills = stringSetPreferencesKey("known_skills")
        val chips = stringSetPreferencesKey("known_chip_ids")
    }

    private val store get() = context.dataStore

    val settings: Flow<AppSettings> = store.data.map { p -> p.toSettings() }

    private fun Preferences.toSettings() = AppSettings(
        onboarded = this[K.onboarded] ?: false,
        participantId = this[K.participant].orEmpty(),
        mode = runCatching { StudyMode.valueOf(this[K.mode] ?: "AI") }.getOrDefault(StudyMode.AI),
        knownThreshold = this[K.threshold],
        profile = Profile(
            name = this[K.name].orEmpty(),
            year = this[K.year] ?: 1,
            interest = this[K.interest].orEmpty(),
            careerPath = this[K.path],
            knownSkills = this[K.skills].orEmpty(),
            knownChipIds = this[K.chips].orEmpty(),
        ),
    )

    suspend fun current(): AppSettings = settings.first()

    /** Anonymous participant id, created on first launch. */
    suspend fun ensureParticipantId(): String {
        current().participantId.takeIf { it.isNotEmpty() }?.let { return it }
        val id = "P-" + UUID.randomUUID().toString().take(8).uppercase()
        store.edit { it[K.participant] = id }
        return id
    }

    suspend fun saveProfile(p: Profile) = store.edit {
        it[K.name] = p.name.trim()
        it[K.year] = p.year
        it[K.interest] = p.interest.trim()
        val path = p.careerPath
        if (path != null) it[K.path] = path else it.remove(K.path)
        it[K.skills] = p.knownSkills
        it[K.chips] = p.knownChipIds
    }

    suspend fun setCareerPath(path: String) = store.edit { it[K.path] = path }
    suspend fun setOnboarded(v: Boolean) = store.edit { it[K.onboarded] = v }
    suspend fun setMode(mode: StudyMode) = store.edit { it[K.mode] = mode.name }
    suspend fun setKnownThreshold(v: Float?) = store.edit { if (v == null) it.remove(K.threshold) else it[K.threshold] = v }

    suspend fun resetOnboarding() = store.edit {
        it[K.onboarded] = false
        listOf(K.name, K.interest, K.path).forEach { k -> it.remove(k) }
        it.remove(K.skills); it.remove(K.chips); it.remove(K.year)
    }
}
