package io.github.refael302.beamfinder.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.refael302.beamfinder.BeamFinderApp
import io.github.refael302.beamfinder.BuildConfig
import io.github.refael302.beamfinder.audio.AudioTrackBeepPlayer
import io.github.refael302.beamfinder.audio.BeepEngine
import io.github.refael302.beamfinder.audio.Haptics
import io.github.refael302.beamfinder.ui.about.AboutViewModel
import io.github.refael302.beamfinder.ui.detect.DetectViewModel
import io.github.refael302.beamfinder.ui.settings.SettingsViewModel

private fun CreationExtras.container() = (this[APPLICATION_KEY] as BeamFinderApp).container

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { SettingsViewModel(container().settingsRepository) }
        initializer {
            val app = this[APPLICATION_KEY] as BeamFinderApp
            DetectViewModel(
                repository = app.container.settingsRepository,
                beepEngine = BeepEngine(AudioTrackBeepPlayer(), Haptics(app)),
            )
        }
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
