package io.github.refael302.beamfinder.detection.scoring

import kotlin.math.max
import kotlin.math.min

/** Tunables for the strict hue gate and the colour cell map. All colour constants live here (SSOT). */
data class ScorerParams(
    /** Minimum target-channel level (0..255) for a pixel to count at all. */
    val minTarget: Float = 80f,
    /** Target channel level treated as "dark"; brightness ramps from here. */
    val darkFloor: Float = 50f,
    /** Target channel span above [darkFloor] that counts as fully bright. */
    val brightSpan: Float = 120f,
    /** Channel dominance (target - max(other two)) that counts as fully coloured. */
    val colorFull: Float = 90f,
    /** Minimum dominance (target - max(other two)) to pass the gate. */
    val minDominance: Float = 40f,
    /**
     * Red mode: max G/R. Rejects orange/amber/warm light where R and G are both high
     * (orange 255,140,0 has G/R 0.55; a red laser is ~0.1).
     */
    val redMaxGreenRatio: Float = 0.40f,
    /** Red mode: max B/R (rejects pink/magenta). */
    val redMaxBlueRatio: Float = 0.55f,
    /** Green mode: max R/G (rejects yellow / yellow-green). */
    val greenMaxRedRatio: Float = 0.55f,
    /** Green mode: max B/G (rejects cyan). */
    val greenMaxBlueRatio: Float = 0.70f,
    /** Side (px of the analysed frame) of the square cells used for max-pooling. */
    val cellSize: Int = 4,
)

/**
 * Strict per-pixel hue gate. Converts YCbCr (BT.601 full range) to RGB and returns a
 * coloured-intensity strength 0..1 only when the selected hue truly dominates; everything
 * else (white, warm/orange, yellow, cyan, pink, dull colours) returns 0.
 */
class HueGate(private val params: ScorerParams = ScorerParams()) {

    /** [u]/[v] are zero-centred Cb/Cr. */
    fun strength(luma: Int, u: Int, v: Int, hue: BeamHue): Float {
        val p = params
        when (hue) {
            BeamHue.Red -> {
                if (v <= 0) return 0f // red always has Cr above neutral
                val r = min(255f, luma + 1.402f * v)
                if (r < p.minTarget) return 0f
                val g = max(0f, luma - 0.344f * u - 0.714f * v)
                val b = max(0f, luma + 1.772f * u)
                if (g > r * p.redMaxGreenRatio || b > r * p.redMaxBlueRatio) return 0f
                return score(r, r - max(g, b))
            }
            BeamHue.Green -> {
                if (v >= 0) return 0f // green always has Cr below neutral
                val g = min(255f, luma - 0.344f * u - 0.714f * v)
                if (g < p.minTarget) return 0f
                val r = max(0f, luma + 1.402f * v)
                val b = max(0f, luma + 1.772f * u)
                if (r > g * p.greenMaxRedRatio || b > g * p.greenMaxBlueRatio) return 0f
                return score(g, g - max(r, b))
            }
        }
    }

    private fun score(target: Float, dominance: Float): Float {
        if (dominance < params.minDominance) return 0f
        val chroma = min(1f, dominance / params.colorFull)
        val bright = ((target - params.darkFloor) / params.brightSpan).coerceIn(0f, 1f)
        return chroma * bright
    }
}
