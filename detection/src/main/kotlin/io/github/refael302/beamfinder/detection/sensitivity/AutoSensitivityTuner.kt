package io.github.refael302.beamfinder.detection.sensitivity

/**
 * Session-based auto sensitivity: tracks the strongest dazzle intensity in the current
 * session and maps it to a sensitivity via [SensitivityModel.sensitivityForFullThreshold].
 *
 * A gap longer than [sessionGapMs] since the last dazzle starts a new session (peak resets).
 * Within a session, only a stronger peak retunes sensitivity.
 *
 * Observation uses a fixed low floor (max-sensitivity start threshold) so dazzles are visible
 * regardless of the currently applied sensitivity.
 */
class AutoSensitivityTuner(
    private val sessionGapMs: Long = DEFAULT_SESSION_GAP_MS,
) {
    private var lastActivityMs: Long = NO_ACTIVITY
    private var sessionPeak: Float = 0f
    private var lastEmitted: Int? = null

    /** Strongest intensity seen in the current session (0 if none yet). */
    val peak: Float get() = sessionPeak

    fun reset() {
        lastActivityMs = NO_ACTIVITY
        sessionPeak = 0f
        lastEmitted = null
    }

    /**
     * Feeds one intensity sample.
     * @return new sensitivity 0..100 when the tune changes; null when unchanged.
     */
    fun onSample(intensity: Float, nowMs: Long): Int? {
        if (intensity < OBSERVE_FLOOR) return null

        val newSession = lastActivityMs == NO_ACTIVITY || nowMs - lastActivityMs > sessionGapMs
        lastActivityMs = nowMs

        if (newSession) {
            sessionPeak = intensity
            return emit(SensitivityModel.sensitivityForFullThreshold(sessionPeak))
        }

        if (intensity > sessionPeak) {
            sessionPeak = intensity
            return emit(SensitivityModel.sensitivityForFullThreshold(sessionPeak))
        }
        return null
    }

    private fun emit(sensitivity: Int): Int? {
        if (sensitivity == lastEmitted) return null
        lastEmitted = sensitivity
        return sensitivity
    }

    companion object {
        const val DEFAULT_SESSION_GAP_MS = 30_000L
        private const val NO_ACTIVITY = Long.MIN_VALUE

        /** Intensity floor that counts as dazzle activity (independent of live sensitivity). */
        val OBSERVE_FLOOR: Float =
            SensitivityModel.thresholds(SensitivityModel.MAX_SENSITIVITY).startThreshold
    }
}
