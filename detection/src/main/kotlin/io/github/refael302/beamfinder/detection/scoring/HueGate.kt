package io.github.refael302.beamfinder.detection.scoring

import kotlin.math.max
import kotlin.math.min

/** Tunables for the hue gate and the colour cell map. All colour constants live here (SSOT). */
data class ScorerParams(
    /** Minimum target-channel level (0..255) for a pixel to count at all. */
    val minTarget: Float = 60f,
    /** Target channel level treated as "dark"; brightness ramps from here. */
    val darkFloor: Float = 40f,
    /** Target channel span above [darkFloor] that counts as fully bright. */
    val brightSpan: Float = 140f,
    /** Channel dominance (target - max(other two)) that counts as fully coloured. */
    val colorFull: Float = 80f,
    /**
     * Minimum dominance (target - max(other two)) to pass the gate.
     * Tuned so outdoor/weak green (dom≈30–40) still passes; pure cyan has dom≈0.
     */
    val minDominance: Float = 28f,
    /**
     * Red mode: max G/R. Rejects orange/amber/warm light where R and G are both high
     * (orange 255,140,0 has G/R 0.55; a red laser is ~0.1).
     */
    val redMaxGreenRatio: Float = 0.40f,
    /** Red mode: max B/R (rejects pink/magenta). */
    val redMaxBlueRatio: Float = 0.55f,
    /** Green mode: max R/G (rejects yellow / yellow-green). */
    val greenMaxRedRatio: Float = 0.55f,
    /**
     * Green mode: max B/G. Phone cameras push green lasers toward teal (B/G≈0.78–0.90);
     * pure cyan is B/G≈1.0 and is still rejected. Dominance also kills cyan (G≈B → dom≈0).
     */
    val greenMaxBlueRatio: Float = 0.95f,
    /** Side (px of the analysed frame) of the square cells used for max-pooling. */
    val cellSize: Int = 4,
    /** Luma at/above which a near-neutral pixel is treated as a clipped laser core. */
    val clipLuma: Int = 230,
    /** Max |Cb-128|, |Cr-128| for a clipped pixel to count as white/neutral. */
    val clipChromaTolerance: Int = 28,
    /** Distance (px) at which the coloured halo around a clipped core is sampled. */
    val haloRadius: Int = 4,
    /** Minimum neighbour hue strength for a clipped core to count via its halo. */
    val haloMin: Float = 0.2f,
)

/**
 * Per-pixel hue gate. Converts YCbCr (BT.601 full range) to RGB and returns a
 * coloured-intensity strength 0..1 only when the selected hue dominates.
 *
 * Phone-captured green lasers often look teal (high B); orange/warm reds are rejected.
 * Pure white / clipped cores return 0 here — [ColorCellMap] recovers them via a coloured halo.
 */
class HueGate(private val params: ScorerParams = ScorerParams()) {

    /** [u]/[v] are zero-centred Cb/Cr. */
    fun strength(luma: Int, u: Int, v: Int, hue: BeamHue): Float {
        val p = params
        // Reconstruct RGB; do not early-out on Cr sign alone — bloomed cores sit near neutral Cr.
        val r = (luma + 1.402f * v).coerceIn(0f, 255f)
        val g = (luma - 0.344f * u - 0.714f * v).coerceIn(0f, 255f)
        val b = (luma + 1.772f * u).coerceIn(0f, 255f)
        when (hue) {
            BeamHue.Red -> {
                if (r < p.minTarget) return 0f
                if (g > r * p.redMaxGreenRatio || b > r * p.redMaxBlueRatio) return 0f
                return score(r, r - max(g, b))
            }
            BeamHue.Green -> {
                if (g < p.minTarget) return 0f
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
