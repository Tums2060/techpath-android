package com.techpath.ui.skill

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.techpath.R
import com.techpath.data.Link
import com.techpath.ui.components.EmptyState
import com.techpath.ui.components.ErrorState
import com.techpath.ui.components.LinkItem
import com.techpath.ui.components.LoadingState
import com.techpath.ui.components.openUrl
import com.techpath.ui.theme.Dimens
import com.techpath.ui.theme.TechPathTheme

@Composable
fun SkillRoute(onBack: () -> Unit, viewModel: SkillViewModel = viewModel(factory = SkillViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SkillScreen(state) { event ->
        when (event) {
            is SkillEvent.OpenLink -> context.openUrl(event.url)
            SkillEvent.Back -> onBack()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillScreen(state: SkillUiState, onEvent: (SkillEvent) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(SkillEvent.Back) }) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when {
            state.error -> ErrorState(stringResource(R.string.error_skill), modifier)
            state.loading -> LoadingState(modifier)
            else -> LazyColumn(modifier, contentPadding = PaddingValues(Dimens.spaceM)) {
                item {
                    Text(stringResource(R.string.skill_about), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Dimens.spaceS))
                    Text(state.explanation, style = MaterialTheme.typography.bodyLarge)
                    if (state.aiFallback) {
                        Spacer(Modifier.height(Dimens.spaceS))
                        Text(
                            stringResource(R.string.notice_ai_fallback_explanation),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(Dimens.spaceL))
                    Text(stringResource(R.string.skill_learn_more), style = MaterialTheme.typography.titleMedium)
                    if (state.links.isEmpty()) {
                        EmptyState(stringResource(R.string.empty_links), Modifier.height(Dimens.spaceXl * 3))
                    }
                }
                items(state.links, key = { it.url }) { link ->
                    LinkItem(link, onOpen = { onEvent(SkillEvent.OpenLink(link.url)) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SkillScreenPreview() {
    TechPathTheme {
        SkillScreen(
            SkillUiState(
                loading = false,
                title = "REST",
                explanation = "REST is an architectural style for designing networked APIs…",
                links = listOf(Link("articles", "What is REST?", "https://example.org/rest")),
            ),
            onEvent = {},
        )
    }
}
