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
import androidx.compose.ui.unit.dp
import com.techpath.app.AppContainer
import com.techpath.app.data.AppSettings
import com.techpath.app.data.Events
import com.techpath.app.data.ModeState
import com.techpath.app.ui.LoadingState
import com.techpath.app.ui.NoticeBanner
import com.techpath.app.data.KnowledgeBase
import com.techpath.core.SkillGap
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
    val kb by produceState<KnowledgeBase.Data?>(null) { value = container.knowledgeBase.get() }
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
            item { ModelCard(s, kb, onThreshold = { t -> scope.launch { container.settings.setKnownThreshold(t) } }) }
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
                        scope.launch { container.roadmaps.clearCaches(); Toast.makeText(context, "Saved roadmaps cleared", Toast.LENGTH_SHORT).show() }
                    }) { Text("Clear saved roadmaps") }
                    Text("Roadmaps are rebuilt next time you open them; your progress is kept.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { confirmReset = true }) { Text("Reset onboarding") }
                }
            }
            item {
                ListItem(
                    headlineContent = { Text("About TechPath & AI use") },
                    supportingContent = { Text("How the ML engine works, what data is used, limitations") },
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
            if (s.mode == StudyMode.AI) "Personalised roadmap from the on-device ML engine: TF-IDF + Logistic Regression predicts your career path, cosine similarity picks the skills closest to your goal."
            else "Control condition: the career path's topics as a fixed checklist with the dataset's own descriptions. No personalisation, no AI.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (mode?.isFallback == true) NoticeBanner("Currently using Static mode: ${mode.reason}")
    }
}

@Composable
private fun ModelCard(s: AppSettings, kb: KnowledgeBase.Data?, onThreshold: (Float?) -> Unit) {
    val default = SkillGap.DEFAULT_THRESHOLD
    var threshold by remember(s.knownThreshold) { mutableFloatStateOf(s.knownThreshold ?: default) }
    SettingsCard("ML engine (on-device)") {
        if (kb == null) {
            Text("Loading model…", style = MaterialTheme.typography.bodySmall)
        } else {
            val m = kb.model.meta
            Text("${m.model}: ${m.classes.size} career paths, ${"%,d".format(kb.nodeCount)} skills, " +
                "${"%,d".format(m.nFeatures)} TF-IDF features (unigrams + bigrams), C = ${m.logisticRegression.c.toInt()}.",
                style = MaterialTheme.typography.bodySmall)
            Text("Held-out test set: accuracy %.3f · macro-F1 %.3f · top-3 accuracy %.3f".format(
                m.metrics.testAccuracy, m.metrics.testMacroF1, m.metrics.testTop3Accuracy), style = MaterialTheme.typography.bodySmall)
            Text("Runs fully offline. No API key or internet connection is needed.", style = MaterialTheme.typography.bodySmall)
        }
        Text("\"Already known\" threshold: %.2f".format(threshold), style = MaterialTheme.typography.bodyMedium)
        Slider(value = threshold, onValueChange = { threshold = it }, valueRange = 0.3f..0.95f, onValueChangeFinished = { onThreshold(threshold) })
        Text("A skill you typed counts as known when its TF-IDF similarity to a roadmap topic title is at least this value. Default %.2f.".format(default),
            style = MaterialTheme.typography.bodySmall)
        if (s.knownThreshold != null) TextButton(onClick = { onThreshold(null) }) { Text("Reset to default") }
    }
}
