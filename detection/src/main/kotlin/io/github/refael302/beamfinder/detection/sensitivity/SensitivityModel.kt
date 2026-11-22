package io.github.refael302.beamfinder.detection.sensitivity

import kotlin.math.roundToInt

/**
 * Maps a user sensitivity (0..100) to intensity thresholds (0..1).
 * Higher sensitivity -> lower thresholds. Invariant: full > start.
 */
object SensitivityModel {
    const val MIN_SENSITIVITY = 0
    const val MAX_SENSITIVITY = 100

    private const val START_AT_MIN = 0.50f
    private const val START_AT_MAX = 0.05f
    /** Full threshold at sensitivity 0 (least sensitive). */
    private const val FULL_AT_MIN = 0.95f
    /** Full threshold at sensitivity 100 (most sensitive). */
    private const val FULL_AT_MAX = 0.35f

    data class Thresholds(
        /** Intensity at which beeping begins. */
        val startThreshold: Float,
        /** Intensity at which the signal is considered maximal (continuous). */
        val fullThreshold: Float,
    )

    fun thresholds(sensitivity: Int): Thresholds {
        val t = sensitivity.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY) / MAX_SENSITIVITY.toFloat()
        return Thresholds(
            startThreshold = lerp(START_AT_MIN, START_AT_MAX, t),
            fullThreshold = lerp(FULL_AT_MIN, FULL_AT_MAX, t),
        )
    }

    /**
     * Inverse of [thresholds]: sensitivity whose [Thresholds.fullThreshold] matches [peakIntensity].
     * Used by auto-tune so the strongest dazzle in a session becomes the "full" reference.
     */
    fun sensitivityForFullThreshold(peakIntensity: Float): Int {
        val peak = peakIntensity.coerceIn(FULL_AT_MAX, FULL_AT_MIN)
        val t = (peak - FULL_AT_MIN) / (FULL_AT_MAX - FULL_AT_MIN)
        return (t * MAX_SENSITIVITY).roundToInt().coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
