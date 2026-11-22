package io.github.refael302.beamfinder.detection.scoring

import io.github.refael302.beamfinder.detection.frame.LumaChromaFrame
import kotlin.math.exp

/** Tunables for [FlashDetector] (SSOT for the temporal part of detection). */
data class FlashParams(
    /** Time constant for a cell's background to follow a RISE (how long a new spot keeps scoring). */
    val riseTauMs: Float = 400f,
    /** Time constant for a cell's background to follow a FALL (re-arms quickly after a flash). */
    val fallTauMs: Float = 120f,
    /** Cell delta counted as "changed" for [ScoreBreakdown.coverage]. */
    val presence: Float = 0.25f,
    /** Changed-cell fraction above which the score is attenuated (camera pans over coloured scenery). */
    val motionStart: Float = 0.25f,
    /** Changed-cell fraction span over which attenuation reaches [motionMinFactor]. */
    val motionSpan: Float = 0.35f,
    val motionMinFactor: Float = 0.3f,
    /** Frame interval assumed when timestamps are missing or equal. */
    val defaultFrameMs: Float = 33f,
    /** A gap longer than this (paused analysis) re-seeds the background instead of producing a delta. */
    val maxGapMs: Long = 1_000L,
    /**
     * Max fraction of cells that may stay "on" and still count as a held laser spot.
     * Larger blobs (red wall, clothing) are treated as scenery and do not sustain the score.
     */
    val maxSpotFraction: Float = 0.08f,
    /** Cell intensity counted as an active coloured spot for sustained scoring. */
    val spotPresence: Float = 0.3f,
)

/**
 * Full-frame temporal flash detector (the live [LaserScorer]).
 *
 * Each frame is turned into a max-pooled, strictly hue-gated intensity map ([ColorCellMap]).
 * Every cell keeps a slowly-rising / quickly-falling background. The raw score is the largest
 * POSITIVE jump of a cell above its background, i.e. a coloured spot that suddenly appears or
 * brightens anywhere in the frame. A *small* matching spot that stays in view also sustains the
 * score (so holding the phone on the laser keeps beeping); large coloured areas (walls, clothing)
 * do not. Steady ambient lamps still fade into the background via the flash path. Non-matching
 * colours (white, orange, yellow, cyan) never enter the map at all.
 *
 * Not thread-safe; call from the analysis thread only.
 */
class FlashDetector(
    scorerParams: ScorerParams = ScorerParams(),
    private val params: FlashParams = FlashParams(),
) : LaserScorer {
    private val map = ColorCellMap(scorerParams)
    private var background = FloatArray(0)
    private var gridW = -1
    private var gridH = -1
    private var lastHue: BeamHue? = null
    private var lastMs = 0L
    private var seeded = false

    override fun analyze(frame: LumaChromaFrame, roi: Roi, hue: BeamHue): ScoreBreakdown {
        map.build(frame, roi, hue)
        val n = map.cellCount
        val cur = map.values
        val nowMs = frame.timestampNanos / 1_000_000L
        val gap = nowMs - lastMs

        val reseed = !seeded || hue != lastHue || map.gridWidth != gridW || map.gridHeight != gridH ||
            gap > params.maxGapMs || gap < 0
        lastMs = nowMs
        if (reseed) {
            if (background.size < n) background = FloatArray(n)
            System.arraycopy(cur, 0, background, 0, n)
            gridW = map.gridWidth
            gridH = map.gridHeight
            lastHue = hue
            seeded = true
            val peakIdx = argMax(cur, n)
            val (px, py) = map.cellCenterNorm(peakIdx)
            return ScoreBreakdown(0f, 0f, maxValue(cur, n), 0f, map.meanLuma, px, py)
        }

        val dt = if (gap > 0) gap.toFloat() else params.defaultFrameMs
        val aUp = 1f - exp(-dt / params.riseTauMs)
        val aDown = 1f - exp(-dt / params.fallTauMs)
        var flash = 0f
        var flashIdx = 0
        var peak = 0f
        var peakIdx = 0
        var changed = 0
        var active = 0
        val bg = background
        for (i in 0 until n) {
            val c = cur[i]
            if (c > peak) {
                peak = c
                peakIdx = i
            }
            if (c >= params.spotPresence) active++
            val d = c - bg[i]
            if (d > 0f) {
                if (d > flash) {
                    flash = d
                    flashIdx = i
                }
                if (d >= params.presence) changed++
                bg[i] += aUp * d
            } else if (d < 0f) {
                bg[i] += aDown * d
            }
        }
        val coverage = changed.toFloat() / n.coerceAtLeast(1)
        val spotFraction = active.toFloat() / n.coerceAtLeast(1)
        val motion = if (coverage <= params.motionStart) {
            1f
        } else {
            (1f - (coverage - params.motionStart) / params.motionSpan).coerceAtLeast(params.motionMinFactor)
        }
        // Small held laser spot: keep scoring. Wide coloured scenery: flash-only.
        val sustained = if (spotFraction > 0f && spotFraction <= params.maxSpotFraction) peak else 0f
        val locIdx = if (flash >= sustained) flashIdx else peakIdx
        val (px, py) = map.cellCenterNorm(locIdx)
        return ScoreBreakdown(
            score = (maxOf(flash, sustained) * motion).coerceIn(0f, 1f),
            flash = flash,
            peak = peak,
            coverage = coverage,
            meanLuma = map.meanLuma,
            peakX = px,
            peakY = py,
        )
    }

    override fun reset() {
        seeded = false
    }

    private fun maxValue(values: FloatArray, n: Int): Float {
        var m = 0f
        for (i in 0 until n) if (values[i] > m) m = values[i]
        return m
    }

    private fun argMax(values: FloatArray, n: Int): Int {
        var best = 0
        var m = Float.NEGATIVE_INFINITY
        for (i in 0 until n) {
            if (values[i] > m) {
                m = values[i]
                best = i
            }
        }
        return best
    }
}
