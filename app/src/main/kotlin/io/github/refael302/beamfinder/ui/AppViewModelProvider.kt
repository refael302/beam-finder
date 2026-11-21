package io.github.refael302.beamfinder.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.refael302.beamfinder.BeamFinderApp
import io.github.refael302.beamfinder.BuildConfig
import io.github.refael302.beamfinder.ui.about.AboutViewModel
import io.github.refael302.beamfinder.ui.detect.DetectViewModel
import io.github.refael302.beamfinder.ui.settings.SettingsViewModel

private fun CreationExtras.container() = (this[APPLICATION_KEY] as BeamFinderApp).container

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { SettingsViewModel(container().settingsRepository) }
        initializer { DetectViewModel(container().settingsRepository) }
        initializer {
            val c = container()
            AboutViewModel(
                currentVersion = BuildConfig.VERSION_NAME,
                checker = c.updateChecker,
                installer = c.updateInstaller,
            )
        }
    }
}
