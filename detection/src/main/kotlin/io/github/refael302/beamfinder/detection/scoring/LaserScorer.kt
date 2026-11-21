package io.github.refael302.beamfinder.detection.scoring

import io.github.refael302.beamfinder.detection.frame.LumaChromaFrame
import kotlin.math.max
import kotlin.math.min

enum class BeamHue { Red, Green }

/** Region of interest in normalized frame coordinates (0..1). */
data class Roi(
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val width: Float = 0.4f,
    val height: Float = 1f,
) {
    companion object {
        /** The whole frame (used when the frame has already been cropped to the guide band). */
        val Full = Roi(centerX = 0.5f, centerY = 0.5f, width = 1f, height = 1f)
    }
}

/** Detailed scorer output; [score] is the final laser-likeness 0..1. */
data class ScoreBreakdown(
    val score: Float,
    /** Mean of the strongest columns (0..1). */
    val peak: Float,
    /** Fraction of ROI columns where the beam colour is present (0..1). */
    val coverage: Float,
    /** Fraction of ROI pixels that are bright but not beam-coloured (0..1). */
    val glare: Float,
    /** Mean luma of the ROI (0..255). */
    val meanLuma: Float,
) {
    companion object {
        val Zero = ScoreBreakdown(0f, 0f, 0f, 0f, 0f)
    }
}

interface LaserScorer {
    fun analyze(frame: LumaChromaFrame, roi: Roi, hue: BeamHue): ScoreBreakdown

    /** Returns a laser-likeness score in 0..1 for the given frame/ROI. */
    fun score(frame: LumaChromaFrame, roi: Roi, hue: BeamHue): Float = analyze(frame, roi, hue).score
}

/** Always scores 0 (tests / disabled detection). */
object NoopLaserScorer : LaserScorer {
    override fun analyze(frame: LumaChromaFrame, roi: Roi, hue: BeamHue): ScoreBreakdown = ScoreBreakdown.Zero
}

/** Tunables for [ColorLaserScorer]. All detection constants live here (SSOT). */
data class ScorerParams(
    /** Channel dominance (target - max(other two), 0..255) that counts as fully coloured. */
    val colorFull: Float = 90f,
    /** Target channel level below which a pixel is considered dark. */
    val darkFloor: Float = 30f,
    /** Target channel span above [darkFloor] that counts as fully bright. */
    val brightSpan: Float = 120f,
    /** Luma at/above which a pixel is treated as clipped. */
    val clipLuma: Int = 235,
    /** Max |Cb-128|, |Cr-128| for a clipped pixel to count as white. */
    val clipChromaTolerance: Int = 24,
    /** Distance (px) at which the coloured halo around a clipped core is sampled. */
    val haloRadius: Int = 4,
    /** Minimum halo colour score for a clipped core to count as beam. */
    val haloMin: Float = 0.3f,
    /** Halo score that maps a clipped core to a full score. */
    val haloFull: Float = 0.6f,
    /** Luma above which a non-beam-coloured pixel counts as glare. */
    val glareLuma: Int = 200,
    /** Pixel score below which a bright pixel counts as glare. */
    val glareMaxScore: Float = 0.15f,
    /** Column score that counts as "beam present" for coverage. */
    val presence: Float = 0.3f,
    /** Coverage fraction at which the coverage term saturates. */
    val coverageSaturation: Float = 0.2f,
    val peakWeight: Float = 0.65f,
    val coverageWeight: Float = 0.35f,
    /** Score multiplier lost per unit glare fraction. */
    val glareWeight: Float = 2f,
    /** Floor for the glare multiplier. */
    val glareMinFactor: Float = 0.2f,
    /** Number of top columns averaged into the peak (noise rejection). */
    val peakColumns: Int = 3,
)

/**
 * YUV colour scorer for red/green laser beams.
 *
 * For each pixel in the ROI it estimates RGB from YCbCr and scores channel dominance of the
 * beam colour weighted by brightness. Clipped white pixels (a saturated laser core) are scored
 * by the colour of their surrounding halo; clipped white pixels without a coloured halo, and
 * bright non-beam pixels, count as glare and attenuate the score.
 *
 * Per column, the max pixel score forms a column profile; the final score combines the
 * strongest columns (peak) with the fraction of columns containing beam colour (coverage).
 */
class ColorLaserScorer(private val params: ScorerParams = ScorerParams()) : LaserScorer {
    private var columns = FloatArray(0)

    override fun analyze(frame: LumaChromaFrame, roi: Roi, hue: BeamHue): ScoreBreakdown {
        val x0 = ((roi.centerX - roi.width / 2f) * frame.width).toInt().coerceIn(0, frame.width - 1)
        val x1 = ((roi.centerX + roi.width / 2f) * frame.width).toInt().coerceIn(x0 + 1, frame.width)
        val y0 = ((roi.centerY - roi.height / 2f) * frame.height).toInt().coerceIn(0, frame.height - 1)
        val y1 = ((roi.centerY + roi.height / 2f) * frame.height).toInt().coerceIn(y0 + 1, frame.height)
        val cols = x1 - x0
        if (columns.size < cols) columns = FloatArray(cols)
        columns.fill(0f, 0, cols)

        var glarePixels = 0
        var lumaSum = 0L
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val luma = frame.lumaAt(x, y)
                lumaSum += luma
                val u = frame.uAt(x, y) - 128
                val v = frame.vAt(x, y) - 128
                var s = pixelScore(luma, u, v, hue)
                if (luma >= params.clipLuma && isNeutral(u, v)) {
                    val halo = haloScore(frame, x, y, hue)
                    if (halo >= params.haloMin) {
                        s = max(s, min(1f, halo / params.haloFull))
                    } else {
                        glarePixels++
                    }
                } else if (luma >= params.glareLuma && s < params.glareMaxScore) {
                    glarePixels++
                }
                val c = x - x0
                if (s > columns[c]) columns[c] = s
            }
        }

        val pixels = cols * (y1 - y0)
        val peak = topMean(columns, cols, params.peakColumns)
        var present = 0
        for (c in 0 until cols) if (columns[c] >= params.presence) present++
        val coverage = present.toFloat() / cols
        val glare = glarePixels.toFloat() / pixels

        val base = params.peakWeight * peak +
            params.coverageWeight * min(1f, coverage / params.coverageSaturation)
        val glareFactor = (1f - params.glareWeight * glare).coerceIn(params.glareMinFactor, 1f)
        return ScoreBreakdown(
            score = (base * glareFactor).coerceIn(0f, 1f),
            peak = peak,
            coverage = coverage,
            glare = glare,
            meanLuma = lumaSum.toFloat() / pixels,
        )
    }

    private fun isNeutral(u: Int, v: Int): Boolean {
        val tol = params.clipChromaTolerance
        return u in -tol..tol && v in -tol..tol
    }

    /** Colour+brightness score for one pixel, 0..1. [u]/[v] are zero-centred. */
    private fun pixelScore(luma: Int, u: Int, v: Int, hue: BeamHue): Float {
        val r = luma + 1.402f * v
        val g = luma - 0.344f * u - 0.714f * v
        val b = luma + 1.772f * u
        val target: Float
        val dominance: Float
        when (hue) {
            BeamHue.Red -> { target = r; dominance = r - max(g, b) }
            BeamHue.Green -> { target = g; dominance = g - max(r, b) }
        }
        if (dominance <= 0f) return 0f
        val chroma = min(1f, dominance / params.colorFull)
        val bright = ((min(target, 255f) - params.darkFloor) / params.brightSpan).coerceIn(0f, 1f)
        return chroma * bright
    }

    /** Max colour score among the four pixels at [ScorerParams.haloRadius] around (x, y). */
    private fun haloScore(frame: LumaChromaFrame, x: Int, y: Int, hue: BeamHue): Float {
        val d = params.haloRadius
        var best = 0f
        for (k in 0 until 4) {
            val sx = when (k) { 0 -> x - d; 1 -> x + d; else -> x }.coerceIn(0, frame.width - 1)
            val sy = when (k) { 2 -> y - d; 3 -> y + d; else -> y }.coerceIn(0, frame.height - 1)
            val l = frame.lumaAt(sx, sy)
            val s = pixelScore(l, frame.uAt(sx, sy) - 128, frame.vAt(sx, sy) - 128, hue)
            if (s > best) best = s
        }
        return best
    }

    private fun topMean(values: FloatArray, count: Int, k: Int): Float {
        // Small fixed k: keep a sorted top-k without allocating a sorted copy.
        val kk = min(k, count).coerceAtLeast(1)
        val top = FloatArray(kk)
        for (i in 0 until count) {
            val v = values[i]
            if (v <= top[kk - 1]) continue
            var j = kk - 1
            while (j > 0 && top[j - 1] < v) {
                top[j] = top[j - 1]
                j--
            }
            top[j] = v
        }
        return top.sum() / kk
    }
}
