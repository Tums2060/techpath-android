package com.techpath.ui.opportunities

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.techpath.R
import com.techpath.data.ApplicationStatus
import com.techpath.data.Opportunity
import com.techpath.data.OpportunityType
import com.techpath.ui.components.EmptyState
import com.techpath.ui.components.ErrorState
import com.techpath.ui.components.LoadingState
import com.techpath.ui.components.openUrl
import com.techpath.ui.theme.Dimens
import com.techpath.ui.theme.TechPathTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun OpportunitiesRoute(viewModel: OpportunitiesViewModel = viewModel(factory = OpportunitiesViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    OpportunitiesScreen(state) { event ->
        if (event is OpportunitiesEvent.Open) context.openUrl(event.url) else viewModel.onEvent(event)
    }
}

@Composable
fun OpportunitiesScreen(state: OpportunitiesUiState, onEvent: (OpportunitiesEvent) -> Unit) {
    Column {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Dimens.spaceM),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS),
        ) {
            FilterChip(state.filter == null, { onEvent(OpportunitiesEvent.Filter(null)) }, { Text(stringResource(R.string.filter_all)) })
            OpportunityType.entries.forEach { type ->
                FilterChip(state.filter == type, { onEvent(OpportunitiesEvent.Filter(type)) }, { Text(typeLabel(type)) })
            }
        }
        when {
            state.loading -> LoadingState()
            state.error -> ErrorState(stringResource(R.string.error_opportunities))
            state.items.isEmpty() -> EmptyState(stringResource(R.string.empty_opportunities))
            else -> LazyColumn(
                contentPadding = PaddingValues(Dimens.spaceM),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceS),
            ) {
                items(state.items, key = { it.opportunity.id }) { OpportunityCard(it, onEvent) }
            }
        }
    }
}

@Composable
private fun OpportunityCard(item: OpportunityUi, onEvent: (OpportunitiesEvent) -> Unit) {
    val o = item.opportunity
    Card(Modifier.fillMaxWidth().alpha(if (item.expired) Dimens.DISABLED_ALPHA else 1f)) {
        Column(Modifier.padding(Dimens.spaceM)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(o.title, style = MaterialTheme.typography.titleMedium)
                    Text(o.organisation, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.opportunity_meta_format, typeLabel(o.type), o.location),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onEvent(OpportunitiesEvent.ToggleSaved(o.id)) }) {
                    Icon(
                        painterResource(if (item.status != null) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border),
                        contentDescription = stringResource(if (item.status != null) R.string.action_unsave else R.string.action_save),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            item.deadline?.let {
                Text(
                    stringResource(
                        if (item.expired) R.string.opportunity_closed_format else R.string.opportunity_deadline_format,
                        it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (o.requirements.isNotEmpty()) {
                Text(
                    o.requirements.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimens.spaceXs),
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                item.status?.let { StatusPicker(it) { status -> onEvent(OpportunitiesEvent.SetStatus(o.id, status)) } }
                Box(Modifier.weight(1f))
                TextButton(onClick = { onEvent(OpportunitiesEvent.Open(o.url)) }) { Text(stringResource(R.string.action_apply)) }
            }
        }
    }
}

@Composable
private fun StatusPicker(current: ApplicationStatus, onSelect: (ApplicationStatus) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { open = true }, label = { Text(statusLabel(current)) })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ApplicationStatus.entries.forEach { status ->
                DropdownMenuItem(text = { Text(statusLabel(status)) }, onClick = { open = false; onSelect(status) })
            }
        }
    }
}

@Composable
private fun typeLabel(type: OpportunityType): String = stringResource(
    when (type) {
        OpportunityType.INTERNSHIP -> R.string.type_internship
        OpportunityType.ATTACHMENT -> R.string.type_attachment
        OpportunityType.JOB -> R.string.type_job
        OpportunityType.HACKATHON -> R.string.type_hackathon
    },
)

@Composable
private fun statusLabel(status: ApplicationStatus): String = stringResource(
    when (status) {
        ApplicationStatus.SAVED -> R.string.status_saved
        ApplicationStatus.APPLIED -> R.string.status_applied
        ApplicationStatus.INTERVIEW -> R.string.status_interview
        ApplicationStatus.OFFER -> R.string.status_offer
        ApplicationStatus.REJECTED -> R.string.status_rejected
    },
)

@Preview(showBackground = true)
@Composable
private fun OpportunitiesScreenPreview() {
    val sample = Opportunity("1", "Android Intern", "Example Labs", OpportunityType.INTERNSHIP, "Nairobi", "2026-12-01", listOf("Kotlin", "Git"), "https://example.org")
    TechPathTheme {
        OpportunitiesScreen(
            OpportunitiesUiState(
                loading = false,
                items = listOf(
                    OpportunityUi(sample, LocalDate.of(2026, 12, 1), ApplicationStatus.APPLIED, expired = false),
                    OpportunityUi(sample.copy(id = "2", title = "Data Attachment"), LocalDate.of(2026, 1, 1), null, expired = true),
                ),
            ),
            onEvent = {},
        )
    }
}
