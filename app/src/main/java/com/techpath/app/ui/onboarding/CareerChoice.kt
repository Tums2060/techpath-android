package com.techpath.app.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.techpath.core.CareerMatcher
import com.techpath.core.CareerPath

/** Top-3 career matches with similarity scores (AI mode). */
fun LazyListScope.matchCards(matches: List<CareerMatcher.Match>, selected: String?, onSelect: (String) -> Unit) {
    items(matches, key = { "m-" + it.career.id }) { m ->
        val isSel = m.career.id == selected
        Card(
            onClick = { onSelect(m.career.id) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.career.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (isSel) Icon(Icons.Filled.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary)
                }
                Text(m.career.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.padding(4.dp))
                if (m.direct) {
                    Text("Exact match for the path name", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(progress = { m.score.coerceIn(0f, 1f) }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Text("similarity %.2f".format(m.score), style = MaterialTheme.typography.labelMedium)
                    }
                }
                Text("${m.career.nodeCount} topics", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Plain list of every career path (Static mode, or "browse all"). Roles first. */
fun LazyListScope.careerList(careers: List<CareerPath>, filter: String, selected: String?, onSelect: (String) -> Unit) {
    val q = filter.trim().lowercase()
    val shown = careers.filter { q.isEmpty() || q in it.name.lowercase() || q in it.id }
        .sortedWith(compareBy<CareerPath> { it.type != "role" }.thenBy { it.name.lowercase() })
    if (shown.isEmpty()) {
        item { Text("No career path matches \"$filter\".", Modifier.padding(16.dp)) }
    }
    items(shown, key = { "c-" + it.id }) { c ->
        Column {
            ListItem(
                headlineContent = { Text(c.name) },
                supportingContent = { Text((if (c.type == "role") "Role · " else "Skill · ") + "${c.nodeCount} topics") },
                trailingContent = { if (c.id == selected) Icon(Icons.Filled.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary) },
                colors = ListItemDefaults.colors(
                    containerColor = if (c.id == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.clickable { onSelect(c.id) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
fun CareerFilterField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        placeholder = { Text("Filter career paths") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}
