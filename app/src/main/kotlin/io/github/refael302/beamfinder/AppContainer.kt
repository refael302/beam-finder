package io.github.refael302.beamfinder

import android.content.Context
import io.github.refael302.beamfinder.settings.SettingsRepository
import io.github.refael302.beamfinder.update.UpdateChecker
import io.github.refael302.beamfinder.update.UpdateInstaller
import io.github.refael302.beamfinder.update.createUpdateChecker
import io.github.refael302.beamfinder.update.createUpdateInstaller
import okhttp3.OkHttpClient

/** Manual dependency container (no Hilt). Created once in [BeamFinderApp]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val httpClient: OkHttpClient by lazy { OkHttpClient() }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    val updateChecker: UpdateChecker by lazy { createUpdateChecker(appContext, httpClient) }

    val updateInstaller: UpdateInstaller by lazy { createUpdateInstaller(appContext) }
}
