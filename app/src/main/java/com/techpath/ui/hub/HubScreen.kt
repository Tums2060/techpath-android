package com.techpath.ui.hub

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.techpath.R
import com.techpath.data.Article
import com.techpath.ui.components.EmptyState
import com.techpath.ui.components.ErrorState
import com.techpath.ui.components.LoadingState
import com.techpath.ui.theme.Dimens
import com.techpath.ui.theme.TechPathTheme

@Composable
fun HubRoute(viewModel: HubViewModel = viewModel(factory = HubViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = state.openArticle != null) { viewModel.onEvent(HubEvent.Close) }
    HubScreen(state, viewModel::onEvent)
}

@Composable
fun HubScreen(state: HubUiState, onEvent: (HubEvent) -> Unit) {
    when {
        state.loading -> LoadingState()
        state.error -> ErrorState(stringResource(R.string.error_hub))
        state.openArticle != null -> Column(Modifier.verticalScroll(rememberScrollState()).padding(Dimens.spaceM)) {
            TextButton(onClick = { onEvent(HubEvent.Close) }) { Text(stringResource(R.string.hub_all_guides)) }
            MarkdownText(state.articleBlocks)
        }
        state.articles.isEmpty() -> EmptyState(stringResource(R.string.empty_hub))
        else -> LazyColumn(contentPadding = PaddingValues(Dimens.spaceM)) {
            item {
                Text(
                    stringResource(R.string.hub_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Dimens.spaceS),
                )
            }
            items(state.articles, key = { it.file }) { article ->
                Text(
                    article.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth().clickable { onEvent(HubEvent.Open(article)) }.padding(vertical = Dimens.spaceM),
                )
                HorizontalDivider()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HubListPreview() {
    TechPathTheme {
        HubScreen(HubUiState(loading = false, articles = listOf(Article("01.md", "Writing a Tech CV"), Article("02.md", "Cover Letters"))), onEvent = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun HubArticlePreview() {
    TechPathTheme {
        HubScreen(
            HubUiState(
                loading = false,
                openArticle = Article("01.md", "Writing a Tech CV"),
                articleBlocks = parseMarkdown("# Writing a Tech CV\n\nKeep it to **one page**.\n\n- Projects first\n- See [GitHub](https://github.com)"),
            ),
            onEvent = {},
        )
    }
}
