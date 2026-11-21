package io.github.refael302.beamfinder.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.refael302.beamfinder.settings.AppLanguage
import io.github.refael302.beamfinder.settings.AppSettings
import io.github.refael302.beamfinder.settings.LanguageManager
import io.github.refael302.beamfinder.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings.Default)

    private val _language = MutableStateFlow(LanguageManager.current())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repository.update(transform) }
    }

    fun setLanguage(language: AppLanguage) {
        _language.value = language
        LanguageManager.apply(language) // recreates the activity; ViewModel survives
    }
}
