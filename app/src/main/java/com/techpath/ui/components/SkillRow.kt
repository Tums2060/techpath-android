package com.techpath.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.techpath.R
import com.techpath.ui.theme.Dimens
import com.techpath.ui.theme.TechPathTheme

/**
 * One skill in a list. [known] skills (ticked in onboarding) show as done and can't be unticked here.
 * [onClick] is null in lists that only tick, such as onboarding.
 */
@Composable
fun SkillRow(
    title: String,
    done: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    known: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable { (onClick ?: onToggle)() }
            .padding(vertical = Dimens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = done || known, onCheckedChange = { onToggle() }, enabled = !known)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (known) {
                Text(
                    stringResource(R.string.skill_already_known),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SkillRowPreview() {
    TechPathTheme {
        Column {
            SkillRow("HTTP basics", done = false, onToggle = {})
            SkillRow("Git", done = false, known = true, onToggle = {})
        }
    }
}
