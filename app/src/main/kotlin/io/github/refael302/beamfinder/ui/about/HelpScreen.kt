package io.github.refael302.beamfinder.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.refael302.beamfinder.R
import io.github.refael302.beamfinder.ui.components.BeamTopBar

@Composable
fun HelpScreen(onBack: () -> Unit) {
    Scaffold(topBar = { BeamTopBar(stringResource(R.string.help_title), onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.help_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HelpSection(R.string.help_section_menu)
            HelpItem(R.string.menu_start, R.string.help_menu_start)
            HelpItem(R.string.menu_settings, R.string.help_menu_settings)
            HelpItem(R.string.menu_about, R.string.help_menu_about)

            HelpSection(R.string.help_section_detect)
            HelpItem(R.string.help_detect_preview_title, R.string.help_detect_preview)
            HelpItem(R.string.help_detect_guide_title, R.string.help_detect_guide)
            HelpItem(R.string.detect_level, R.string.help_detect_level)
            HelpItem(R.string.help_detect_quick_title, R.string.help_detect_quick)
            HelpItem(R.string.detect_zoom, R.string.help_detect_zoom)
            HelpItem(R.string.setting_sensitivity, R.string.help_detect_sensitivity)
            HelpItem(R.string.detect_sensitivity_auto, R.string.help_detect_sensitivity_auto)
            HelpItem(R.string.detect_lock_exposure, R.string.help_detect_lock_exposure)
            HelpItem(R.string.detect_lock_focus, R.string.help_detect_lock_focus)
            HelpItem(R.string.detect_calibrate, R.string.help_detect_calibrate)
            HelpItem(R.string.detect_pocket, R.string.help_detect_pocket)
            HelpItem(R.string.detect_debug, R.string.help_detect_debug)
            HelpItem(R.string.menu_settings, R.string.help_detect_settings)

            HelpSection(R.string.help_section_settings)
            HelpItem(R.string.setting_beep_style, R.string.help_setting_beep_style)
            HelpItem(R.string.setting_audio_stream, R.string.help_setting_audio_stream)
            HelpItem(R.string.setting_vibrate, R.string.help_setting_vibrate)
            HelpItem(R.string.setting_low_exposure, R.string.help_setting_low_exposure)
            HelpItem(R.string.setting_language, R.string.help_setting_language)
        }
    }
}

@Composable
private fun HelpSection(titleRes: Int) {
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
private fun HelpItem(titleRes: Int, bodyRes: Int) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(stringResource(titleRes), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
