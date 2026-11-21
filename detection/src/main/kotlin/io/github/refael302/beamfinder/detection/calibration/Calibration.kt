package io.github.refael302.beamfinder.detection.calibration

import kotlin.math.sqrt

data class CalibrationResult(
    val baselineLuma: Float = 0f,
    /** Standard deviation of the ambient raw score. */
    val noiseFloor: Float = 0f,
    /** Mean ambient raw score (what the scorer reports with no beam present). */
    val baselineScore: Float = 0f,
)

interface Calibrator {
    val result: CalibrationResult

    /** True while collecting the ambient baseline. */
    val isCalibrating: Boolean

    /** 0..1 progress of the current calibration (1 when done). */
    val progress: Float

    /** Feeds one ambient sample. Ignored when not calibrating. */
    fun update(rawScore: Float, meanLuma: Float, nowMs: Long)

    /** Discards the baseline and starts a new calibration on the next sample. */
    fun reset()
}

/** Never calibrates; baseline stays zero. */
class NoopCalibrator : Calibrator {
    override val result: CalibrationResult = CalibrationResult()
    override val isCalibrating: Boolean = false
    override val progress: Float = 1f
    override fun update(rawScore: Float, meanLuma: Float, nowMs: Long) = Unit
    override fun reset() = Unit
}

/**
 * Collects the ambient raw score for [durationMs] (default ~1.5 s) and stores its mean and
 * standard deviation. Starts calibrating immediately on creation.
 */
class AmbientCalibrator(
    private val durationMs: Long = DEFAULT_DURATION_MS,
    private val minSamples: Int = 5,
    /** Upper bound for the stored baseline so a beam present during calibration can't mute detection entirely. */
    private val maxBaselineScore: Float = 0.5f,
) : Calibrator {
    override var result: CalibrationResult = CalibrationResult()
        private set
    override var isCalibrating: Boolean = true
        private set
    override var progress: Float = 0f
        private set

    private var startMs: Long? = null
    private var count = 0
    private var sum = 0.0
    private var sumSq = 0.0
    private var lumaSum = 0.0

    override fun update(rawScore: Float, meanLuma: Float, nowMs: Long) {
        if (!isCalibrating) return
        val start = startMs ?: nowMs.also { startMs = it }
        count++
        sum += rawScore
        sumSq += rawScore.toDouble() * rawScore
        lumaSum += meanLuma
        val elapsed = nowMs - start
        progress = (elapsed.toFloat() / durationMs).coerceIn(0f, 1f)
        if (elapsed >= durationMs && count >= minSamples) {
            val mean = sum / count
            val variance = (sumSq / count - mean * mean).coerceAtLeast(0.0)
            result = CalibrationResult(
                baselineLuma = (lumaSum / count).toFloat(),
                noiseFloor = sqrt(variance).toFloat(),
                baselineScore = mean.toFloat().coerceAtMost(maxBaselineScore),
            )
            isCalibrating = false
            progress = 1f
        }
    }

    override fun reset() {
        result = CalibrationResult()
        isCalibrating = true
        progress = 0f
        startMs = null
        count = 0
        sum = 0.0
        sumSq = 0.0
        lumaSum = 0.0
    }

    companion object {
        const val DEFAULT_DURATION_MS = 1_500L
    }
}
