package io.github.refael302.beamfinder.audio

import android.os.SystemClock
import io.github.refael302.beamfinder.detection.beep.BeepConfig
import io.github.refael302.beamfinder.detection.beep.BeepState
import io.github.refael302.beamfinder.detection.beep.BeepStateMachine
import io.github.refael302.beamfinder.detection.beep.Clock
import io.github.refael302.beamfinder.detection.sensitivity.SensitivityModel
import io.github.refael302.beamfinder.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Drives [BeepStateMachine] on its own ticker (independent of camera fps) and routes its
 * output to [BeepPlayer] and [Haptics]. Beep interval shrinks with intensity until the tone
 * becomes continuous at the full threshold (rules live in the state machine / SensitivityModel).
 */
class BeepEngine(
    private val player: BeepPlayer,
    private val haptics: Haptics,
    private val clock: Clock = Clock { SystemClock.elapsedRealtime() },
) {
    @Volatile private var intensity = 0f
    @Volatile private var lastInputMs = Long.MIN_VALUE / 2
    @Volatile private var config = configFor(AppSettings.Default)

    private val machine = BeepStateMachine(config, clock)
    private var job: Job? = null
    private var continuousActive = false

    /** Latest smoothed intensity; may be called from any thread. */
    fun submit(intensity: Float) {
        this.intensity = intensity
        lastInputMs = clock.nowMs()
    }

    fun configure(settings: AppSettings) {
        config = configFor(settings)
        player.configure(settings.beepStyle, settings.volume, settings.audioStream)
        haptics.enabled = settings.vibrate
    }

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        player.open()
        job = scope.launch(Dispatchers.Default) {
            machine.reset()
            continuousActive = false
            try {
                while (isActive) {
                    tick()
                    delay(TICK_MS)
                }
            } finally {
                silence()
            }
        }
    }

    /** Silences output and releases the audio track until the next [start]. */
    fun stop() {
        job?.cancel()
        job = null
        player.stop()
        haptics.stop()
        player.release()
    }

    private fun tick() {
        val now = clock.nowMs()
        // No fresh frames (camera stalled/stopped): treat as no beam rather than beeping forever.
        val level = if (now - lastInputMs > STALE_MS) 0f else intensity
        machine.config = config
        val out = machine.update(level)
        when {
            out.state == BeepState.Idle -> if (continuousActive) silence()
            out.continuous -> if (!continuousActive) {
                continuousActive = true
                player.startContinuous()
                haptics.startContinuous()
            }
            else -> {
                if (continuousActive) silence()
                if (out.beepNow) {
                    val ms = player.beep(out.intervalMs)
                    haptics.pulse(ms)
                }
            }
        }
    }

    private fun silence() {
        continuousActive = false
        player.stop()
        haptics.stop()
    }

    private companion object {
        const val TICK_MS = 10L
        const val STALE_MS = 500L

        fun configFor(settings: AppSettings): BeepConfig {
            val t = SensitivityModel.thresholds(settings.sensitivity)
            return BeepConfig(startThreshold = t.startThreshold, fullThreshold = t.fullThreshold)
        }
    }
}
