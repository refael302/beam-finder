package io.github.refael302.beamfinder.settings

enum class BeamColor { Red, Green }
enum class BeepStyle { Pulse, Tone, Click }
enum class CameraFacing { Back, Front }
enum class AudioStream { Alarm, Media }

/**
 * Single source of truth for user settings. Property defaults here ARE the app defaults;
 * [SettingsRepository] falls back to [Default] for any missing key.
 */
data class AppSettings(
    val beamColor: BeamColor = BeamColor.Red,
    /** 0..100 */
    val sensitivity: Int = 50,
    val beepStyle: BeepStyle = BeepStyle.Pulse,
    val cameraFacing: CameraFacing = CameraFacing.Back,
    /** Legacy DataStore key; dim screen is only started from the Detect button now. */
    val pocketMode: Boolean = false,
    /** Off on fresh install; once the user enables it, the choice is persisted. */
    val vibrate: Boolean = false,
    /**
     * In-app beep gain 0..1. No Settings slider — use the device volume keys for loudness.
     * Kept so existing DataStore values still apply.
     */
    val volume: Float = 1f,
    val audioStream: AudioStream = AudioStream.Alarm,
    val lowExposure: Boolean = true,
    /** Guide line vertical position, 0..1 of frame height */
    val guideLineY: Float = 0.5f,
    val guideLocked: Boolean = false,
) {
    /** Clamps all ranged values. */
    fun sanitized(): AppSettings = copy(
        sensitivity = sensitivity.coerceIn(SENSITIVITY_RANGE),
        volume = volume.coerceIn(0f, 1f),
        guideLineY = guideLineY.coerceIn(0f, 1f),
    )

    companion object {
        val Default = AppSettings()
        val SENSITIVITY_RANGE = 0..100
    }
}
