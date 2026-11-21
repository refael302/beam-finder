package io.github.refael302.beamfinder.detection.beep

/** Injectable time source (milliseconds, monotonic). */
fun interface Clock {
    fun nowMs(): Long
}

data class BeepConfig(
    val startThreshold: Float,
    val fullThreshold: Float,
    /** Intensity must fall below start - hysteresis to begin releasing. */
    val hysteresis: Float = 0.03f,
    /** Beep interval at full intensity (just below the continuous threshold). */
    val minIntervalMs: Long = 80L,
    /** Beep interval right at the start threshold. */
    val maxIntervalMs: Long = 600L,
    /** How long intensity must stay low before returning to Idle. */
    val releaseHoldMs: Long = 250L,
)

enum class BeepState { Idle, Beeping }

data class BeepOutput(
    val state: BeepState,
    /** Normalized signal level 0..1 between start and full thresholds. */
    val level: Float,
    /** True when intensity reached the full threshold: play a continuous tone. */
    val continuous: Boolean,
    /** True when a single beep should be emitted for this update. */
    val beepNow: Boolean,
    val intervalMs: Long,
)

/**
 * Pure state machine turning an intensity stream into beep commands.
 * Call [update] for every analyzed frame.
 */
class BeepStateMachine(
    var config: BeepConfig,
    private val clock: Clock,
) {
    var state: BeepState = BeepState.Idle
        private set

    private var lastBeepAtMs: Long? = null
    private var belowSinceMs: Long? = null

    fun update(intensity: Float): BeepOutput {
        val cfg = config
        val now = clock.nowMs()

        when (state) {
            BeepState.Idle -> {
                if (intensity >= cfg.startThreshold) {
                    state = BeepState.Beeping
                    lastBeepAtMs = null
                    belowSinceMs = null
                }
            }
            BeepState.Beeping -> {
                if (intensity < cfg.startThreshold - cfg.hysteresis) {
                    val since = belowSinceMs ?: now.also { belowSinceMs = it }
                    if (now - since >= cfg.releaseHoldMs) {
                        reset()
                    }
                } else {
                    belowSinceMs = null
                }
            }
        }

        if (state == BeepState.Idle) {
            return BeepOutput(BeepState.Idle, 0f, false, false, cfg.maxIntervalMs)
        }

        val span = (cfg.fullThreshold - cfg.startThreshold).coerceAtLeast(1e-6f)
        val level = ((intensity - cfg.startThreshold) / span).coerceIn(0f, 1f)
        val continuous = intensity >= cfg.fullThreshold
        val interval = (cfg.maxIntervalMs + (cfg.minIntervalMs - cfg.maxIntervalMs) * level).toLong()

        val last = lastBeepAtMs
        val beepNow = !continuous && (last == null || now - last >= interval)
        if (beepNow) lastBeepAtMs = now

        return BeepOutput(BeepState.Beeping, level, continuous, beepNow, interval)
    }

    fun reset() {
        state = BeepState.Idle
        lastBeepAtMs = null
        belowSinceMs = null
    }
}
