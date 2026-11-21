package io.github.refael302.beamfinder.ui.detect

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.refael302.beamfinder.R
import io.github.refael302.beamfinder.detection.sensitivity.SensitivityModel
import io.github.refael302.beamfinder.ui.AppViewModelProvider
import io.github.refael302.beamfinder.ui.components.BeamTopBar
import io.github.refael302.beamfinder.ui.settings.labelRes
import java.util.Locale
import kotlin.math.roundToInt

/** Phase 2 placeholder. Phase 3 replaces the note with the CameraX preview + detection. */
@Composable
fun DetectScreen(
    onBack: () -> Unit,
    viewModel: DetectViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val thresholds = SensitivityModel.thresholds(s.sensitivity)

    Scaffold(topBar = { BeamTopBar(stringResource(R.string.detect_title), onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.detect_placeholder_note),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.detect_current_settings),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    SummaryLine(R.string.setting_beam_color, stringResource(s.beamColor.labelRes()))
                    SummaryLine(R.string.setting_sensitivity, s.sensitivity.toString())
                    SummaryLine(R.string.setting_beep_style, stringResource(s.beepStyle.labelRes()))
                    SummaryLine(R.string.setting_camera_facing, stringResource(s.cameraFacing.labelRes()))
                    SummaryLine(R.string.setting_pocket_mode, onOff(s.pocketMode))
                    SummaryLine(R.string.setting_vibrate, onOff(s.vibrate))
                    SummaryLine(R.string.setting_volume, percent(s.volume))
                    SummaryLine(R.string.setting_audio_stream, stringResource(s.audioStream.labelRes()))
                    SummaryLine(R.string.setting_roi_width, percent(s.roiWidth))
                    SummaryLine(R.string.setting_low_exposure, onOff(s.lowExposure))
                    SummaryLine(R.string.setting_guide_line_y, percent(s.guideLineY))
                    SummaryLine(R.string.setting_guide_locked, onOff(s.guideLocked))
                    Text(
                        stringResource(
                            R.string.detect_thresholds,
                            String.format(Locale.getDefault(), "%.2f", thresholds.startThreshold),
                            String.format(Locale.getDefault(), "%.2f", thresholds.fullThreshold),
                        ),
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryLine(labelRes: Int, value: String) {
    Text(
        text = "${stringResource(labelRes)}: $value",
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun onOff(v: Boolean) = stringResource(if (v) R.string.on else R.string.off)

private fun percent(v: Float) = "${(v * 100).roundToInt()}%"
