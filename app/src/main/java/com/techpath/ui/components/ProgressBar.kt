package com.techpath.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
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
fun ProgressBar(done: Int, total: Int, modifier: Modifier = Modifier) {
    Column(modifier) {
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done.toFloat() / total },
            modifier = Modifier.fillMaxWidth().height(Dimens.progressHeight),
        )
        Spacer(Modifier.height(Dimens.spaceXs))
        Text(
            stringResource(R.string.progress_format, done, total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProgressBarPreview() {
    TechPathTheme { ProgressBar(done = 12, total = 40) }
}
