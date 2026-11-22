package io.github.refael302.beamfinder.detection.scoring

import io.github.refael302.beamfinder.detection.frame.LumaChromaFrame

enum class BeamHue { Red, Green }

/** Region of interest in normalized frame coordinates (0..1). Detection uses [Full]. */
data class Roi(
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val width: Float = 1f,
    val height: Float = 1f,
) {
    companion object {
        val Full = Roi(centerX = 0.5f, centerY = 0.5f, width = 1f, height = 1f)
    }
}

/** Detailed scorer output; [score] is the final laser-likeness 0..1 fed to the pipeline. */
data class ScoreBreakdown(
    val score: Float,
    /** Strongest temporal rise of hue-gated intensity in any cell, before motion attenuation (0..1). */
    val flash: Float,
    /** Strongest static hue-gated intensity in any cell (0..1); informational. */
    val peak: Float,
    /** Fraction of cells whose hue-gated intensity rose by at least the presence level (0..1). */
    val coverage: Float,
    /** Mean luma of the analysed area (0..255). */
    val meanLuma: Float,
    /** Peak cell centre in normalized frame coordinates (0..1); used for secondary crop refine. */
    val peakX: Float = 0.5f,
    val peakY: Float = 0.5f,
) {
    companion object {
        val Zero = ScoreBreakdown(0f, 0f, 0f, 0f, 0f)
    }
}

interface LaserScorer {
    fun analyze(frame: LumaChromaFrame, roi: Roi, hue: BeamHue): ScoreBreakdown

    /** Returns a laser-likeness score in 0..1 for the given frame/ROI. */
    fun score(frame: LumaChromaFrame, roi: Roi, hue: BeamHue): Float = analyze(frame, roi, hue).score

    /** Drops any temporal state (new calibration, camera or hue switch). */
    fun reset() = Unit
}

/** Always scores 0 (tests / disabled detection). */
object NoopLaserScorer : LaserScorer {
    override fun analyze(frame: LumaChromaFrame, roi: Roi, hue: BeamHue): ScoreBreakdown = ScoreBreakdown.Zero
}

/**
 * Stateless (single-frame) colour scorer: score = strongest hue-gated cell in the frame.
 * Useful for diagnostics and as the static half of [FlashDetector]; it does NOT ignore steady
 * coloured lights, so the live pipeline uses [FlashDetector] instead.
 */
class ColorLaserScorer(params: ScorerParams = ScorerParams()) : LaserScorer {
    private val map = ColorCellMap(params)

    override fun analyze(frame: LumaChromaFrame, roi: Roi, hue: BeamHue): ScoreBreakdown {
        map.build(frame, roi, hue)
        var peak = 0f
        var present = 0
        val v = map.values
        for (i in 0 until map.cellCount) {
            if (v[i] > peak) peak = v[i]
            if (v[i] > 0f) present++
        }
        return ScoreBreakdown(
            score = peak,
            flash = 0f,
            peak = peak,
            coverage = present.toFloat() / map.cellCount.coerceAtLeast(1),
            meanLuma = map.meanLuma,
        )
    }
}
