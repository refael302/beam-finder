package io.github.refael302.beamfinder.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data.map { it.toSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            prefs.write(transform(prefs.toSettings()).sanitized())
        }
    }

    private object Keys {
        val beamColor = stringPreferencesKey("beam_color")
        val sensitivity = intPreferencesKey("sensitivity")
        val beepStyle = stringPreferencesKey("beep_style")
        val cameraFacing = stringPreferencesKey("camera_facing")
        val pocketMode = booleanPreferencesKey("pocket_mode")
        val vibrate = booleanPreferencesKey("vibrate")
        val volume = floatPreferencesKey("volume")
        val audioStream = stringPreferencesKey("audio_stream")
        val roiWidth = floatPreferencesKey("roi_width")
        val lowExposure = booleanPreferencesKey("low_exposure")
        val guideLineY = floatPreferencesKey("guide_line_y")
        val guideLocked = booleanPreferencesKey("guide_locked")
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings.Default
        return AppSettings(
            beamColor = enumOf(this[Keys.beamColor], d.beamColor),
            sensitivity = this[Keys.sensitivity] ?: d.sensitivity,
            beepStyle = enumOf(this[Keys.beepStyle], d.beepStyle),
            cameraFacing = enumOf(this[Keys.cameraFacing], d.cameraFacing),
            pocketMode = this[Keys.pocketMode] ?: d.pocketMode,
            vibrate = this[Keys.vibrate] ?: d.vibrate,
            volume = this[Keys.volume] ?: d.volume,
            audioStream = enumOf(this[Keys.audioStream], d.audioStream),
            roiWidth = this[Keys.roiWidth] ?: d.roiWidth,
            lowExposure = this[Keys.lowExposure] ?: d.lowExposure,
            guideLineY = this[Keys.guideLineY] ?: d.guideLineY,
            guideLocked = this[Keys.guideLocked] ?: d.guideLocked,
        ).sanitized()
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.write(s: AppSettings) {
        this[Keys.beamColor] = s.beamColor.name
        this[Keys.sensitivity] = s.sensitivity
        this[Keys.beepStyle] = s.beepStyle.name
        this[Keys.cameraFacing] = s.cameraFacing.name
        this[Keys.pocketMode] = s.pocketMode
        this[Keys.vibrate] = s.vibrate
        this[Keys.volume] = s.volume
        this[Keys.audioStream] = s.audioStream.name
        this[Keys.roiWidth] = s.roiWidth
        this[Keys.lowExposure] = s.lowExposure
        this[Keys.guideLineY] = s.guideLineY
        this[Keys.guideLocked] = s.guideLocked
    }

    private inline fun <reified E : Enum<E>> enumOf(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
