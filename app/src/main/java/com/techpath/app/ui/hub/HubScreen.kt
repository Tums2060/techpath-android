package com.techpath.app.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.techpath.app.AppContainer
import com.techpath.app.data.Article
import com.techpath.app.data.Events
import com.techpath.app.ui.LoadingState
import com.techpath.app.ui.MessageState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(container: AppContainer, onOpen: (String) -> Unit, onOpenSettings: () -> Unit) {
    val articles by produceState<List<Article>?>(null) { value = runCatching { container.articles.all() }.getOrDefault(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Career hub") },
                actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "Settings") } },
            )
        },
    ) { pad ->
        val list = articles
        if (list == null) {
            LoadingState(modifier = Modifier.padding(pad)); return@Scaffold
        }
        val shown = remember(list, query) {
            list.map { it to it.matches(query) }.filter { it.second > 0 }.sortedByDescending { if (query.isBlank()) 0 else it.second }.map { it.first }
        }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Practical guides for getting your first tech role.", style = MaterialTheme.typography.bodyMedium)
            }
            item {
                OutlinedTextField(
                    query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text("Search: CV, interview, GitHub…") },
                )
            }
            if (list.isEmpty()) item { MessageState("No articles bundled", "Reinstall the app to restore the career hub.") }
            else if (shown.isEmpty()) item { MessageState("No articles match \"$query\"", "Try another word.", icon = Icons.Outlined.SearchOff) }
            items(shown, key = { it.id }) { a ->
                Card(onClick = { onOpen(a.id) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(a.title, style = MaterialTheme.typography.titleMedium)
                        if (a.summary.isNotBlank()) Text(a.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(container: AppContainer, id: String, onBack: () -> Unit) {
    val article by produceState<Result<Article?>?>(null, id) { value = runCatching { container.articles.get(id) } }
    LaunchedEffect(id) { container.logger.log(Events.ARTICLE_OPENED, itemId = id) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(article?.getOrNull()?.title ?: "Article", maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        val r = article
        val a = r?.getOrNull()
        when {
            r == null -> LoadingState(modifier = Modifier.padding(pad))
            a == null -> MessageState("Article not found", modifier = Modifier.padding(pad))
            else -> LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MarkdownBlocks.parse(a.body)) { b -> MarkdownBlock(b) }
            }
        }
    }
}

/** A deliberately small Markdown subset: headings, quotes, bullets, numbers, bold/italic/code. */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Para(val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Bullet(val text: String, val marker: String) : MdBlock
}

object MarkdownBlocks {
    fun parse(md: String): List<MdBlock> {
        val out = mutableListOf<MdBlock>()
        val para = StringBuilder()
        fun flush() { if (para.isNotBlank()) out += MdBlock.Para(para.toString().trim()); para.clear() }
        for (raw in md.lines()) {
            val line = raw.trimEnd()
            val numbered = Regex("^(\\d+)\\.\\s+(.*)").find(line.trimStart())
            when {
                line.isBlank() -> flush()
                line.startsWith("#") -> { flush(); val lvl = line.takeWhile { it == '#' }.length; out += MdBlock.Heading(lvl, line.drop(lvl).trim()) }
                line.startsWith("> ") -> { flush(); out += MdBlock.Quote(line.removePrefix("> ")) }
                line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> { flush(); out += MdBlock.Bullet(line.trimStart().drop(2), "•") }
                numbered != null -> { flush(); out += MdBlock.Bullet(numbered.groupValues[2], numbered.groupValues[1] + ".") }
                else -> para.append(line).append(' ')
            }
        }
        flush()
        return out
    }

    /** **bold**, *italic*, `code`. */
    fun inline(text: String): AnnotatedString = buildAnnotatedString {
        val re = Regex("\\*\\*(.+?)\\*\\*|\\*(.+?)\\*|`(.+?)`")
        var i = 0
        for (m in re.findAll(text)) {
            append(text.substring(i, m.range.first))
            when {
                m.groups[1] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
                m.groups[2] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(m.groupValues[2]) }
                else -> withStyle(SpanStyle(fontWeight = FontWeight.Medium, background = androidx.compose.ui.graphics.Color(0x22888888))) { append(m.groupValues[3]) }
            }
            i = m.range.last + 1
        }
        append(text.substring(i))
    }
}

@Composable
private fun MarkdownBlock(b: MdBlock) {
    when (b) {
        is MdBlock.Heading -> Text(
            MarkdownBlocks.inline(b.text),
            style = when (b.level) { 1 -> MaterialTheme.typography.headlineSmall; 2 -> MaterialTheme.typography.titleLarge; else -> MaterialTheme.typography.titleMedium },
            modifier = Modifier.padding(top = if (b.level == 1) 0.dp else 8.dp),
        )
        is MdBlock.Para -> Text(MarkdownBlocks.inline(b.text), style = MaterialTheme.typography.bodyLarge)
        is MdBlock.Quote -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Text(MarkdownBlocks.inline(b.text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
        }
        is MdBlock.Bullet -> Row(Modifier.padding(start = 8.dp)) {
            Text(b.marker + "  ", style = MaterialTheme.typography.bodyLarge)
            Text(MarkdownBlocks.inline(b.text), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
