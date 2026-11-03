package com.techpath

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.techpath.ai.AiClient
import com.techpath.ai.GeminiClient
import com.techpath.ai.RoadmapBuilder
import com.techpath.data.AssetReader
import com.techpath.data.DatasetRepository
import com.techpath.data.UserRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "techpath")

/** Hand-written dependency container. Create things here; ViewModels read them from TechPathApp. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val json = Json { ignoreUnknownKeys = true }

    val dataset = DatasetRepository(
        object : AssetReader {
            override fun read(path: String) = appContext.assets.open(path).bufferedReader().use { it.readText() }
            override fun list(dir: String) = appContext.assets.list(dir).orEmpty().toList()
        },
        json,
    )

    val user = UserRepository(appContext.dataStore, json)

    /** Swap this for another AiClient implementation (e.g. the team's fine-tuned model). */
    val ai: AiClient = GeminiClient(
        apiKey = { user.settings.first().apiKeyOverride.ifBlank { BuildConfig.GEMINI_API_KEY } },
        json = json,
    )

    val roadmapBuilder = RoadmapBuilder(ai, json)
}

/** Lets ViewModel factories reach the container: `initializer { FooViewModel(appContainer().dataset) }`. */
fun androidx.lifecycle.viewmodel.CreationExtras.appContainer(): AppContainer =
    (this[androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TechPathApp).container
