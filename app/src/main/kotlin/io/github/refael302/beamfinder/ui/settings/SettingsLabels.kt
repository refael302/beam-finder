package io.github.refael302.beamfinder.ui.settings

import androidx.annotation.StringRes
import io.github.refael302.beamfinder.R
import io.github.refael302.beamfinder.settings.AppLanguage
import io.github.refael302.beamfinder.settings.AudioStream
import io.github.refael302.beamfinder.settings.BeamColor
import io.github.refael302.beamfinder.settings.BeepStyle
import io.github.refael302.beamfinder.settings.CameraFacing

@StringRes
fun BeamColor.labelRes(): Int = when (this) {
    BeamColor.Red -> R.string.color_red
    BeamColor.Green -> R.string.color_green
}

@StringRes
fun BeepStyle.labelRes(): Int = when (this) {
    BeepStyle.Pulse -> R.string.beep_pulse
    BeepStyle.Tone -> R.string.beep_tone
    BeepStyle.Click -> R.string.beep_click
}

@StringRes
fun CameraFacing.labelRes(): Int = when (this) {
    CameraFacing.Back -> R.string.camera_back
    CameraFacing.Front -> R.string.camera_front
}

@StringRes
fun AudioStream.labelRes(): Int = when (this) {
    AudioStream.Alarm -> R.string.stream_alarm
    AudioStream.Media -> R.string.stream_media
}

@StringRes
fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.System -> R.string.language_system
    AppLanguage.Hebrew -> R.string.language_hebrew
    AppLanguage.English -> R.string.language_english
}
