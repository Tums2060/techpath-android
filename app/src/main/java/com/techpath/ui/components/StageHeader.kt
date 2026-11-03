package com.techpath.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.techpath.R
import com.techpath.ui.theme.Dimens
import com.techpath.ui.theme.TechPathTheme

@Composable
fun StageHeader(title: String, why: String?, done: Int, total: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = Dimens.spaceL, bottom = Dimens.spaceS)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        if (why != null) Text(why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            stringResource(R.string.stage_count_format, done, total),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StageHeaderPreview() {
    TechPathTheme { StageHeader("Foundations", "Start with the web basics.", done = 2, total = 8) }
}
