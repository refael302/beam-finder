package io.github.refael302.beamfinder.ui.detect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.refael302.beamfinder.settings.AppSettings
import io.github.refael302.beamfinder.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class DetectViewModel(repository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings.Default)
}
