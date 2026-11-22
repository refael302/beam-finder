package io.github.refael302.beamfinder.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.refael302.beamfinder.R
import io.github.refael302.beamfinder.settings.AppLanguage
import io.github.refael302.beamfinder.settings.AppSettings
import io.github.refael302.beamfinder.settings.AudioStream
import io.github.refael302.beamfinder.settings.BeepStyle
import io.github.refael302.beamfinder.ui.AppViewModelProvider
import io.github.refael302.beamfinder.ui.components.BeamTopBar
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()

    Scaffold(topBar = { BeamTopBar(stringResource(R.string.settings_title), onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionHeader(R.string.settings_section_feedback)
            ChoiceRow(
                title = stringResource(R.string.setting_beep_style),
                options = BeepStyle.entries,
                selected = s.beepStyle,
                label = { stringResource(it.labelRes()) },
                onSelect = { v -> viewModel.update { it.copy(beepStyle = v) } },
            )
            ChoiceRow(
                title = stringResource(R.string.setting_audio_stream),
                options = AudioStream.entries,
                selected = s.audioStream,
                label = { stringResource(it.labelRes()) },
                onSelect = { v -> viewModel.update { it.copy(audioStream = v) } },
            )
            SwitchRow(
                title = stringResource(R.string.setting_vibrate),
                checked = s.vibrate,
                onChange = { v -> viewModel.update { it.copy(vibrate = v) } },
            )

            SectionHeader(R.string.settings_section_camera)
            SwitchRow(
                title = stringResource(R.string.setting_low_exposure),
                checked = s.lowExposure,
                onChange = { v -> viewModel.update { it.copy(lowExposure = v) } },
            )
            SliderRow(
                title = stringResource(R.string.setting_roi_width),
                value = s.roiWidth,
                range = AppSettings.ROI_WIDTH_RANGE,
                valueText = percent(s.roiWidth),
                onCommit = { v -> viewModel.update { it.copy(roiWidth = v) } },
            )

            SectionHeader(R.string.settings_section_general)
            ChoiceRow(
                title = stringResource(R.string.setting_language),
                options = AppLanguage.entries,
                selected = language,
                label = { stringResource(it.labelRes()) },
                onSelect = viewModel::setLanguage,
            )
        }
    }
}

private fun percent(v: Float) = "${(v * 100).roundToInt()}%"

@Composable
private fun SectionHeader(titleRes: Int) {
    Column(Modifier.padding(top = 20.dp, bottom = 4.dp)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider(Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Keeps drag state locally and commits to DataStore only when the drag ends. */
@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onCommit: (Float) -> Unit,
    steps: Int = 0,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(valueText, style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local) },
            valueRange = range,
            steps = steps,
        )
    }
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    Text(label(option))
                }
            }
        }
    }
}
