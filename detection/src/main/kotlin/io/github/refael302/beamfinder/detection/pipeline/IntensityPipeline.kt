package io.github.refael302.beamfinder.detection.pipeline

import io.github.refael302.beamfinder.detection.calibration.AmbientCalibrator
import io.github.refael302.beamfinder.detection.calibration.Calibrator
import io.github.refael302.beamfinder.detection.frame.LumaChromaFrame
import io.github.refael302.beamfinder.detection.scoring.BeamHue
import io.github.refael302.beamfinder.detection.scoring.ColorLaserScorer
import io.github.refael302.beamfinder.detection.scoring.FlashDetector
import io.github.refael302.beamfinder.detection.scoring.LaserScorer
import io.github.refael302.beamfinder.detection.scoring.Roi
import io.github.refael302.beamfinder.detection.scoring.ScoreBreakdown
import io.github.refael302.beamfinder.detection.scoring.ScorerParams
import io.github.refael302.beamfinder.detection.sensitivity.SensitivityModel
import kotlin.math.exp
import kotlin.math.max

/** Output of one analyzed frame. */
data class DetectionResult(
    /** Smoothed, baseline-normalized beam intensity 0..1. Drives beeps and the level meter. */
    val intensity: Float,
    /** Analyzed frames per second. */
    val fps: Float,
    /** Raw scorer output before normalization. */
    val rawScore: Float = 0f,
    val calibrating: Boolean = false,
    val calibrationProgress: Float = 1f,
    /** Beam considered present (hysteresis around the start threshold). */
    val detected: Boolean = false,
    val breakdown: ScoreBreakdown = ScoreBreakdown.Zero,
) {
    companion object {
        val Empty = DetectionResult(intensity = 0f, fps = 0f)
    }
}

/** Tunables for [IntensityPipeline] (SSOT for smoothing constants). */
data class PipelineParams(
    /** Noise std-devs added to the baseline before normalization. */
    val noiseK: Float = 3f,
    /** Fixed margin above baseline+noise. */
    val margin: Float = 0.02f,
    /** Upper bound for the normalization floor. */
    val maxFloor: Float = 0.9f,
    /** Peaks are held for this long before following the signal down. */
    val peakHoldMs: Long = 250L,
    /** EMA time constant while rising. */
    val attackTauMs: Float = 40f,
    /** EMA time constant while falling. */
    val releaseTauMs: Float = 150f,
    /** `detected` turns off below startThreshold - hysteresis. */
    val hysteresis: Float = 0.05f,
    /** Time constant for the fps estimate. */
    val fpsTauMs: Float = 500f,
    /**
     * Secondary dense-crop refine: run when wide raw score is in this band (weak-but-interesting).
     * Below [refineMinRaw] = noise; above [refineMaxRaw] = already strong enough.
     */
    val refineMinRaw: Float = 0.04f,
    val refineMaxRaw: Float = 0.50f,
    /** Require at least this flash (or peak) on the wide pass before refining. */
    val refineMinFlash: Float = 0.04f,
    val refineMinPeak: Float = 0.12f,
)

/**
 * Frame -> intensity (0..1) pipeline:
 * full-frame flash score ([FlashDetector]) -> optional dense crop refine -> ambient calibration ->
 * normalize vs baseline -> peak-hold -> EMA -> hysteresis. Sensitivity enters via [thresholds].
 *
 * Frame timing comes from [LumaChromaFrame.timestampNanos]. [process] must be called from a
 * single thread; [recalibrate] and [thresholds] may be set from any thread.
 */
class IntensityPipeline(
    private val scorer: LaserScorer = FlashDetector(),
    private val calibrator: Calibrator = AmbientCalibrator(),
    private val params: PipelineParams = PipelineParams(),
    /** Static scorer on a dense crop; separate from [scorer] temporal state. */
    private val cropScorer: LaserScorer = ColorLaserScorer(ScorerParams(cellSize = 2)),
) {
    @Volatile
    var thresholds: SensitivityModel.Thresholds = SensitivityModel.thresholds(50)

    @Volatile
    private var recalibrateRequested = false

    private var lastMs: Long? = null
    private var frameIntervalMs = 0f
    private var heldValue = 0f
    private var heldAtMs = 0L
    private var smoothed = 0f
    private var detected = false

    /**
     * @param secondaryCrop optional factory for a dense crop around the wide peak; only invoked
     * when the wide pass looks weakly interesting. Return null to skip.
     */
    fun process(
        frame: LumaChromaFrame,
        hue: BeamHue,
        roi: Roi = Roi.Full,
        secondaryCrop: ((peakX: Float, peakY: Float) -> LumaChromaFrame?)? = null,
    ): DetectionResult {
        if (recalibrateRequested) {
            recalibrateRequested = false
            calibrator.reset()
            scorer.reset()
            cropScorer.reset()
            resetSmoothing()
        }
        val nowMs = frame.timestampNanos / 1_000_000L
        val dt = lastMs?.let { (nowMs - it).coerceAtLeast(0L).toFloat() } ?: 0f
        lastMs = nowMs
        if (dt > 0f) {
            frameIntervalMs = if (frameIntervalMs == 0f) dt else frameIntervalMs + alpha(dt, params.fpsTauMs) * (dt - frameIntervalMs)
        }
        val fps = if (frameIntervalMs > 0f) 1000f / frameIntervalMs else 0f

        val wide = scorer.analyze(frame, roi, hue)
        var breakdown = wide
        var raw = wide.score.coerceIn(0f, 1f)

        if (secondaryCrop != null && shouldRefine(wide)) {
            val crop = secondaryCrop(wide.peakX, wide.peakY)
            if (crop != null) {
                val cropBd = cropScorer.analyze(crop, Roi.Full, hue)
                if (cropBd.score > raw) {
                    raw = cropBd.score.coerceIn(0f, 1f)
                    breakdown = wide.copy(
                        score = raw,
                        peak = max(wide.peak, cropBd.peak),
                        flash = max(wide.flash, cropBd.flash),
                    )
                }
            }
        }

        if (calibrator.isCalibrating) {
            calibrator.update(raw, breakdown.meanLuma, nowMs)
            resetSmoothing()
            return DetectionResult(
                intensity = 0f,
                fps = fps,
                rawScore = raw,
                calibrating = calibrator.isCalibrating,
                calibrationProgress = calibrator.progress,
                breakdown = breakdown,
            )
        }

        val normalized = normalize(raw)

        // Peak hold.
        if (normalized >= heldValue || nowMs - heldAtMs > params.peakHoldMs) {
            heldValue = normalized
            heldAtMs = nowMs
        }

        // Asymmetric EMA (fast attack, slower release), frame-rate independent.
        val tau = if (heldValue > smoothed) params.attackTauMs else params.releaseTauMs
        smoothed += alpha(if (dt > 0f) dt else 33f, tau) * (heldValue - smoothed)
        val intensity = smoothed.coerceIn(0f, 1f)

        val t = thresholds
        detected = if (detected) {
            intensity >= (t.startThreshold - params.hysteresis).coerceAtLeast(0f)
        } else {
            intensity >= t.startThreshold
        }

        return DetectionResult(
            intensity = intensity,
            fps = fps,
            rawScore = raw,
            calibrating = false,
            calibrationProgress = 1f,
            detected = detected,
            breakdown = breakdown,
        )
    }

    /** Requests a new ambient calibration; applied on the next frame. */
    fun recalibrate() {
        recalibrateRequested = true
    }

    fun reset() = recalibrate()

    internal fun shouldRefine(wide: ScoreBreakdown): Boolean {
        val raw = wide.score
        if (raw < params.refineMinRaw || raw > params.refineMaxRaw) return false
        return wide.flash >= params.refineMinFlash || wide.peak >= params.refineMinPeak
    }

    private fun normalize(raw: Float): Float {
        val cal = calibrator.result
        val floor = (cal.baselineScore + params.noiseK * cal.noiseFloor + params.margin)
            .coerceIn(0f, params.maxFloor)
        return ((raw - floor) / (1f - floor)).coerceIn(0f, 1f)
    }

    private fun resetSmoothing() {
        heldValue = 0f
        heldAtMs = 0L
        smoothed = 0f
        detected = false
    }

    private fun alpha(dtMs: Float, tauMs: Float): Float = 1f - exp(-dtMs / tauMs)
}
