package com.techpath.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.techpath.app.BuildConfig

private val sections = listOf(
    "What TechPath is" to "TechPath is a research prototype from a Strathmore University (BICS) Research Methodology project. " +
        "It brings career skills guidance, opportunities and preparation resources for technology students into one app, " +
        "and compares an AI-personalised roadmap with a static one.",
    "How the AI roadmap works" to "TechPath uses Retrieval-Augmented Generation (RAG). Every topic in a curated roadmap dataset " +
        "(roadmap.sh, 91 career paths, about 10,400 topics) has a precomputed text embedding. Your career interest is embedded and " +
        "compared with each path by cosine similarity; the closest topics for your goal are retrieved, and a Google Gemini model " +
        "orders them into stages and writes a one-line reason for each. The model may only use topics from the retrieved set; " +
        "anything else is discarded. No model was trained or fine-tuned for TechPath.",
    "Static mode" to "In Static mode the same career path's topics are shown as a fixed checklist with the dataset's own descriptions. " +
        "No AI is used. TechPath also switches to Static mode when there is no API key or no internet connection.",
    "What is sent to Google" to "In AI mode TechPath sends your career interest, year of study, the skills you said you have, and the " +
        "retrieved topic titles and descriptions to the Gemini API. Your name and participant ID are never sent. Google's Gemini API " +
        "terms apply to that data; on the free tier Google may use it to improve its products.",
    "What stays on your phone" to "Your name (optional), profile, progress, saved opportunities and the anonymised usage log are stored " +
        "only on this device. The usage log records which screens and items were used and the study mode, under a random " +
        "participant ID. It leaves the phone only if you export it from Settings.",
    "Limitations" to "AI-generated orderings and explanations can be wrong and have not been validated by industry experts; check " +
        "them against the linked resources. Many topics in the dataset have few or no learning links. Opportunity listings are " +
        "fictional sample data, and the Relevance score is a ranking aid based on text similarity, not a judgement of your " +
        "suitability. TechPath never submits applications.",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About & AI use") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(sections) { (title, body) ->
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
            item {
                Text("Version ${BuildConfig.VERSION_NAME}. Roadmap content © roadmap.sh contributors.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
