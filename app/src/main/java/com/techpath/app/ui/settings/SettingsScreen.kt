package com.techpath.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.techpath.app.AppContainer
import com.techpath.app.data.AppSettings
import com.techpath.app.data.Events
import com.techpath.app.data.ModeState
import com.techpath.app.ui.LoadingState
import com.techpath.app.ui.NoticeBanner
import com.techpath.core.EmbeddingMeta
import com.techpath.core.StudyMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit, onAbout: () -> Unit) {
    val settings by container.settings.settings.collectAsState(initial = null)
    val eventCount by container.db.events().count().collectAsState(initial = 0)
    val meta by produceState<EmbeddingMeta?>(null) { value = container.knowledgeBase.get().embedding }
    val modeState by produceState<ModeState?>(null, settings) { value = container.roadmaps.modeState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val n = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)!!.bufferedWriter().use { container.logger.exportCsv(it) }
                }
            }
            Toast.makeText(context, n.fold({ "Exported $it events" }, { "Export failed: ${it.message}" }), Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        val s = settings
        if (s == null) {
            LoadingState(modifier = Modifier.padding(pad)); return@Scaffold
        }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { StudyModeCard(s, modeState) { m -> scope.launch { container.settings.setMode(m); container.logger.log(Events.MODE_CHANGED, detail = m.name) } } }
            item { ApiKeyCard(s, onSave = { k -> scope.launch { container.settings.setApiKey(k) } }) }
            item { ModelCard(s, meta, onModel = { m -> scope.launch { container.settings.setTextModel(m) } }, onThreshold = { t -> scope.launch { container.settings.setKnownThreshold(t) } }) }
            item {
                SettingsCard("Study data") {
                    Text("Participant ID: ${s.participantId.ifEmpty { "—" }}", style = MaterialTheme.typography.bodyMedium)
                    Text("$eventCount anonymised events logged on this device.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { exportLauncher.launch("techpath_${s.participantId}_${LocalDate.now()}.csv") }) { Text("Export data (CSV)") }
                }
            }
            item {
                SettingsCard("Maintenance") {
                    OutlinedButton(onClick = {
                        scope.launch { container.roadmaps.clearCaches(); Toast.makeText(context, "Cached roadmaps and explanations cleared", Toast.LENGTH_SHORT).show() }
                    }) { Text("Clear AI cache") }
                    Text("Removes saved roadmaps and skill explanations; your progress is kept.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { confirmReset = true }) { Text("Reset onboarding") }
                }
            }
            item {
                ListItem(
                    headlineContent = { Text("About TechPath & AI use") },
                    supportingContent = { Text("How the AI works, what data is used, limitations") },
                    trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) },
                    modifier = Modifier.clickable(onClick = onAbout),
                )
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset onboarding?") },
            text = { Text("You'll answer the welcome questions again. Skill progress, saved opportunities and study data stay on the device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch { container.roadmaps.clearCaches(); container.settings.resetOnboarding() }
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudyModeCard(s: AppSettings, mode: ModeState?, onMode: (StudyMode) -> Unit) {
    SettingsCard("Study mode") {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            StudyMode.entries.forEachIndexed { i, m ->
                SegmentedButton(selected = s.mode == m, onClick = { onMode(m) }, shape = SegmentedButtonDefaults.itemShape(i, StudyMode.entries.size)) {
                    Text(if (m == StudyMode.AI) "AI" else "Static")
                }
            }
        }
        Text(
            if (s.mode == StudyMode.AI) "Personalised roadmap: retrieval over the roadmap dataset plus an LLM that sequences and explains skills."
            else "Control condition: the career path's topics as a fixed checklist with the dataset's own descriptions. No personalisation, no AI.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (mode?.isFallback == true) NoticeBanner("Currently using Static mode: ${mode.reason}")
    }
}

@Composable
private fun ApiKeyCard(s: AppSettings, onSave: (String?) -> Unit) {
    var key by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    SettingsCard("Gemini API key") {
        Text(
            if (s.hasApiKey) "A key is saved (encrypted with the Android Keystore)." else "No key saved. AI mode needs a free key from Google AI Studio (aistudio.google.com).",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            key, { key = it.trim() }, label = { Text(if (s.hasApiKey) "Replace key" else "Paste key") }, singleLine = true,
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton(onClick = { show = !show }) { Icon(if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (show) "Hide" else "Show") } },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(key); key = "" }, enabled = key.length >= 20) { Text("Save key") }
            if (s.hasApiKey) OutlinedButton(onClick = { onSave(null) }) { Text("Remove key") }
        }
    }
}

@Composable
private fun ModelCard(s: AppSettings, meta: EmbeddingMeta?, onModel: (String) -> Unit, onThreshold: (Float?) -> Unit) {
    var model by remember(s.textModel) { mutableStateOf(s.textModel) }
    val default = meta?.knownThreshold?.toFloat() ?: 0.8f
    var threshold by remember(s.knownThreshold, default) { mutableFloatStateOf(s.knownThreshold ?: default) }
    SettingsCard("AI settings") {
        OutlinedTextField(model, { model = it }, label = { Text("Text model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onModel(model) }, enabled = model.isNotBlank() && model != s.textModel) { Text("Use model") }
        }
        Text("Embeddings: ${meta?.let { "${it.model} (${it.dims} dims${if (it.isLocal) ", offline fallback" else ""})" } ?: "loading…"}",
            style = MaterialTheme.typography.bodySmall)
        Text("\"Already known\" threshold: %.2f".format(threshold), style = MaterialTheme.typography.bodyMedium)
        Slider(value = threshold, onValueChange = { threshold = it }, valueRange = 0.3f..0.95f, onValueChangeFinished = { onThreshold(threshold) })
        Text("A skill you typed counts as known when its similarity to a roadmap topic is at least this value. Default %.2f.".format(default),
            style = MaterialTheme.typography.bodySmall)
        if (s.knownThreshold != null) TextButton(onClick = { onThreshold(null) }) { Text("Reset to default") }
    }
}
