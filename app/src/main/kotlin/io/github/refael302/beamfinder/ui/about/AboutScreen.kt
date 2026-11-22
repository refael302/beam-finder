package io.github.refael302.beamfinder.ui.about

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.refael302.beamfinder.BuildConfig
import io.github.refael302.beamfinder.R
import io.github.refael302.beamfinder.ui.AppViewModelProvider
import io.github.refael302.beamfinder.ui.components.BeamTopBar
import io.github.refael302.beamfinder.update.InstallErrorCodes
import io.github.refael302.beamfinder.update.UpdateInfo

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onHelp: () -> Unit,
    viewModel: AboutViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val email = stringResource(R.string.about_developer_email)

    Scaffold(topBar = { BeamTopBar(stringResource(R.string.about_title), onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(stringResource(R.string.about_license), style = MaterialTheme.typography.bodyMedium)

            HorizontalDivider()

            Text(stringResource(R.string.about_developer_section), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.about_developer_name), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = email,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                ),
                modifier = Modifier.clickable {
                    context.startActivity(
                        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )

            HorizontalDivider()

            Text(stringResource(R.string.about_disclaimer_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.about_disclaimer_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(onClick = onHelp, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.about_help))
            }

            val busy = state is UpdateUiState.Downloading ||
                state is UpdateUiState.Preparing ||
                state is UpdateUiState.AwaitingConfirmation

            OutlinedButton(
                onClick = viewModel::checkForUpdate,
                enabled = state !is UpdateUiState.Checking && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.about_check_update))
            }

            UpdateStatusSection(
                state = state,
                onInstall = viewModel::install,
                onCancel = viewModel::cancelInstall,
                onOpenUnknownSources = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}"),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                onOpenReleasePage = { info ->
                    val url = info.pageUrl ?: return@UpdateStatusSection
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
        }
    }
}

@Composable
private fun UpdateStatusSection(
    state: UpdateUiState,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onOpenUnknownSources: () -> Unit,
    onOpenReleasePage: (UpdateInfo) -> Unit,
) {
    val statusText = when (val st = state) {
        UpdateUiState.Idle -> null
        UpdateUiState.Checking -> stringResource(R.string.about_checking)
        UpdateUiState.UpToDate -> stringResource(R.string.about_up_to_date)
        UpdateUiState.NotSupported -> stringResource(R.string.about_update_not_supported)
        is UpdateUiState.Available ->
            stringResource(R.string.about_update_available, st.info.versionName)
        is UpdateUiState.Downloading -> downloadLabel(st.bytesRead, st.totalBytes)
        is UpdateUiState.Preparing -> stringResource(R.string.about_update_preparing)
        is UpdateUiState.AwaitingConfirmation -> stringResource(R.string.about_update_confirm)
        is UpdateUiState.NeedsPermission -> stringResource(R.string.about_update_needs_permission)
        is UpdateUiState.Error -> when (st.message) {
            "no releases yet" -> stringResource(R.string.about_no_releases)
            InstallErrorCodes.CANCELLED -> stringResource(R.string.about_update_cancelled)
            InstallErrorCodes.SIGNATURE -> stringResource(R.string.about_update_signature_mismatch)
            else -> stringResource(R.string.about_update_error, st.message)
        }
    }
    if (statusText != null) {
        Text(statusText, style = MaterialTheme.typography.bodyMedium)
    }

    when (val st = state) {
        is UpdateUiState.Downloading -> {
            val total = st.totalBytes
            if (total != null && total > 0L) {
                LinearProgressIndicator(
                    progress = { (st.bytesRead.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.about_update_cancel))
            }
        }
        is UpdateUiState.Preparing -> {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.about_update_cancel))
            }
        }
        is UpdateUiState.AwaitingConfirmation -> {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.about_update_cancel))
            }
            if (st.info.pageUrl != null) {
                OutlinedButton(
                    onClick = { onOpenReleasePage(st.info) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.about_update_open_release))
                }
            }
        }
        is UpdateUiState.Available -> {
            Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.about_update_button))
            }
        }
        is UpdateUiState.NeedsPermission -> {
            Button(onClick = onOpenUnknownSources, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.about_update_open_settings))
            }
            OutlinedButton(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.about_update_button))
            }
        }
        is UpdateUiState.Error -> {
            if (st.info != null) {
                Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.about_update_button))
                }
                if (st.info.pageUrl != null) {
                    OutlinedButton(
                        onClick = { onOpenReleasePage(st.info) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.about_update_open_release))
                    }
                }
            }
        }
        else -> Unit
    }
}

@Composable
private fun downloadLabel(bytesRead: Long, totalBytes: Long?): String {
    val readMb = bytesRead / (1024f * 1024f)
    return if (totalBytes != null && totalBytes > 0L) {
        val totalMb = totalBytes / (1024f * 1024f)
        val percent = ((100L * bytesRead) / totalBytes).toInt().coerceIn(0, 100)
        stringResource(R.string.about_update_downloading_progress, percent, readMb, totalMb)
    } else {
        stringResource(R.string.about_update_downloading_bytes, readMb)
    }
}
