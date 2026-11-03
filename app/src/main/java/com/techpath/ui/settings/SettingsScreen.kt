package com.techpath.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.techpath.R
import com.techpath.data.PathInfo
import com.techpath.data.StudyMode
import com.techpath.ui.components.LoadingState
import com.techpath.ui.components.pathLabel
import com.techpath.ui.onboarding.YearChips
import com.techpath.ui.theme.Dimens
import com.techpath.ui.theme.TechPathTheme

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onChangePath: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(state) { event ->
        when (event) {
            SettingsEvent.Back -> onBack()
            SettingsEvent.ChangePath -> onChangePath()
            else -> viewModel.onEvent(event)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(SettingsEvent.Back) }) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(Dimens.spaceM)) {
            Section(stringResource(R.string.settings_year))
            YearChips(state.year) { onEvent(SettingsEvent.SetYear(it)) }

            Section(stringResource(R.string.settings_path))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.path?.let { pathLabel(it) } ?: stringResource(R.string.settings_no_path),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onEvent(SettingsEvent.ChangePath) }) { Text(stringResource(R.string.action_change_path)) }
            }

            Section(stringResource(R.string.settings_study_mode))
            StudyMode.entries.forEach { mode ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = mode == state.studyMode, role = Role.RadioButton) { onEvent(SettingsEvent.SetStudyMode(mode)) }
                        .padding(vertical = Dimens.spaceXs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = mode == state.studyMode, onClick = null)
                    Column(Modifier.padding(start = Dimens.spaceS)) {
                        Text(stringResource(if (mode == StudyMode.AI) R.string.mode_ai else R.string.mode_static))
                        Text(
                            stringResource(if (mode == StudyMode.AI) R.string.mode_ai_hint else R.string.mode_static_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Section(stringResource(R.string.settings_api_key))
            Text(
                stringResource(if (state.hasBuildKey) R.string.api_key_build_present else R.string.api_key_build_missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.apiKeyDraft,
                onValueChange = { onEvent(SettingsEvent.ApiKeyChanged(it)) },
                label = { Text(stringResource(R.string.api_key_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.spaceS),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onEvent(SettingsEvent.SaveApiKey) }) { Text(stringResource(R.string.action_save)) }
                if (state.apiKeySaved) {
                    Text(stringResource(R.string.api_key_saved), Modifier.padding(start = Dimens.spaceM), style = MaterialTheme.typography.labelMedium)
                }
            }

            Section(stringResource(R.string.settings_reset))
            OutlinedButton(onClick = { onEvent(SettingsEvent.RequestReset) }) { Text(stringResource(R.string.action_reset_progress)) }
            if (state.resetDone) Text(stringResource(R.string.reset_done), style = MaterialTheme.typography.labelMedium)
        }
    }

    if (state.confirmReset) {
        AlertDialog(
            onDismissRequest = { onEvent(SettingsEvent.DismissReset) },
            title = { Text(stringResource(R.string.reset_confirm_title)) },
            text = { Text(stringResource(R.string.reset_confirm_body)) },
            confirmButton = { TextButton(onClick = { onEvent(SettingsEvent.ConfirmReset) }) { Text(stringResource(R.string.action_reset)) } },
            dismissButton = { TextButton(onClick = { onEvent(SettingsEvent.DismissReset) }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(Dimens.spaceL))
    HorizontalDivider()
    Spacer(Modifier.height(Dimens.spaceM))
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(Dimens.spaceS))
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    TechPathTheme {
        SettingsScreen(
            SettingsUiState(loading = false, year = 3, path = PathInfo("android", "Android", "role", null, 114), studyMode = StudyMode.AI),
            onEvent = {},
        )
    }
}
