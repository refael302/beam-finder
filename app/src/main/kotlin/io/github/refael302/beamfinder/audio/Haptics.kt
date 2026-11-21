package io.github.refael302.beamfinder.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Vibration output synced with beeps. Safe to call from any thread. */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    @Volatile var enabled: Boolean = true
        set(value) {
            field = value
            if (!value) stop()
        }

    private val available: Boolean get() = vibrator?.hasVibrator() == true

    @Volatile private var continuousOn = false

    fun pulse(durationMs: Long) {
        if (!enabled || !available) return
        continuousOn = false
        vibrator?.vibrate(
            VibrationEffect.createOneShot(durationMs.coerceIn(MIN_PULSE_MS, MAX_PULSE_MS), VibrationEffect.DEFAULT_AMPLITUDE),
        )
    }

    fun startContinuous() {
        if (!enabled || !available || continuousOn) return
        continuousOn = true
        // Waveform [off 0 ms, on 1000 ms] repeating from index 0 => uninterrupted vibration.
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 1000L), 0))
    }

    fun stop() {
        continuousOn = false
        vibrator?.cancel()
    }

    private companion object {
        const val MIN_PULSE_MS = 15L
        const val MAX_PULSE_MS = 150L
    }
}
