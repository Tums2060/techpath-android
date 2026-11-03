package com.techpath.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.techpath.R
import com.techpath.ui.theme.Dimens
import com.techpath.ui.theme.TechPathTheme

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    CenteredMessage(message, modifier)
}

@Composable
fun ErrorState(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    CenteredMessage(message, modifier) {
        if (onRetry != null) TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

@Composable
private fun CenteredMessage(message: String, modifier: Modifier, action: @Composable () -> Unit = {}) {
    Column(
        modifier.fillMaxSize().padding(Dimens.spaceL),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action()
    }
}

@Preview(showBackground = true)
@Composable
private fun StatesPreview() {
    TechPathTheme { ErrorState("Something went wrong", onRetry = {}) }
}
