package io.github.refael302.beamfinder.ui.about

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.refael302.beamfinder.BuildConfig
import io.github.refael302.beamfinder.R
import io.github.refael302.beamfinder.ui.AppViewModelProvider
import io.github.refael302.beamfinder.ui.components.BeamTopBar

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    viewModel: AboutViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(topBar = { BeamTopBar(stringResource(R.string.about_title), onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(stringResource(R.string.about_license), style = MaterialTheme.typography.bodyMedium)

            OutlinedButton(
                onClick = viewModel::checkForUpdate,
                enabled = state != UpdateUiState.Checking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.about_check_update))
            }

            val statusText = when (val st = state) {
                UpdateUiState.Idle -> null
                UpdateUiState.Checking -> stringResource(R.string.about_checking)
                UpdateUiState.UpToDate -> stringResource(R.string.about_up_to_date)
                UpdateUiState.NotSupported -> stringResource(R.string.about_update_not_supported)
                is UpdateUiState.Available ->
                    stringResource(R.string.about_update_available, st.info.versionName)
                UpdateUiState.Installing -> stringResource(R.string.about_updating)
                is UpdateUiState.NeedsPermission -> stringResource(R.string.about_update_needs_permission)
                is UpdateUiState.Error -> if (st.message == "no releases yet") {
                    stringResource(R.string.about_no_releases)
                } else {
                    stringResource(R.string.about_update_error, st.message)
                }
            }
            if (statusText != null) {
                Text(statusText, style = MaterialTheme.typography.bodyMedium)
            }

            if (state is UpdateUiState.Available) {
                Button(onClick = viewModel::install, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.about_update_button))
                }
            }
            if (state is UpdateUiState.NeedsPermission) {
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${context.packageName}"),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.about_update_open_settings))
                }
                // Retry after the user returns from settings.
                OutlinedButton(onClick = viewModel::install, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.about_update_button))
                }
            }
        }
    }
}
