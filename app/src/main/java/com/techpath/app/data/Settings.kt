package com.techpath.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.techpath.app.BuildConfig
import com.techpath.core.StudyMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

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
    val hasApiKey: Boolean = false,
    val textModel: String = BuildConfig.DEFAULT_TEXT_MODEL,
    /** null = use the default shipped in careers.json for the embedding kind. */
    val knownThreshold: Float? = null,
    val profile: Profile = Profile(),
)

class SettingsRepository(private val context: Context) {

    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val participant = stringPreferencesKey("participant_id")
        val mode = stringPreferencesKey("study_mode")
        val apiKey = stringPreferencesKey("api_key_enc")
        val textModel = stringPreferencesKey("text_model")
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
        hasApiKey = this[K.apiKey] != null || BuildConfig.DEFAULT_GEMINI_KEY.isNotBlank(),
        textModel = this[K.textModel]?.takeIf { it.isNotBlank() } ?: BuildConfig.DEFAULT_TEXT_MODEL,
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
    suspend fun setTextModel(model: String) = store.edit { it[K.textModel] = model.trim() }
    suspend fun setKnownThreshold(v: Float?) = store.edit { if (v == null) it.remove(K.threshold) else it[K.threshold] = v }

    suspend fun resetOnboarding() = store.edit {
        it[K.onboarded] = false
        listOf(K.name, K.interest, K.path).forEach { k -> it.remove(k) }
        it.remove(K.skills); it.remove(K.chips); it.remove(K.year)
    }

    // ---- API key: AES-GCM with a key held in the Android Keystore ----

    suspend fun setApiKey(key: String?) = store.edit {
        if (key.isNullOrBlank()) it.remove(K.apiKey) else it[K.apiKey] = KeyCrypto.encrypt(key.trim())
    }

    suspend fun apiKey(): String? {
        val enc = store.data.first()[K.apiKey]
        val stored = enc?.let { runCatching { KeyCrypto.decrypt(it) }.getOrNull() }
        return stored ?: BuildConfig.DEFAULT_GEMINI_KEY.takeIf { it.isNotBlank() }
    }

    private object KeyCrypto {
        private const val ALIAS = "techpath_api_key"
        private const val TRANSFORM = "AES/GCM/NoPadding"

        private fun secretKey(): SecretKey {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            gen.init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            return gen.generateKey()
        }

        fun encrypt(plain: String): String {
            val c = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
            val out = c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(out, Base64.NO_WRAP)
        }

        fun decrypt(b64: String): String {
            val all = Base64.decode(b64, Base64.NO_WRAP)
            val c = Cipher.getInstance(TRANSFORM)
            c.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, all, 0, 12))
            return String(c.doFinal(all, 12, all.size - 12), Charsets.UTF_8)
        }
    }
}
