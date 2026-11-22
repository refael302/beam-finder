package io.github.refael302.beamfinder.detection.scoring

import io.github.refael302.beamfinder.detection.frame.LumaChromaFrame

/**
 * Downsampled coloured-intensity map of a frame: the frame (or [Roi]) is split into square
 * cells of [ScorerParams.cellSize] px and each cell holds the MAX hue-gate strength of its
 * pixels (max-pooling keeps a 1-2 px far-away laser spot from being averaged away).
 *
 * Clipped white laser cores (high luma, near-neutral chroma) score via a coloured halo sampled
 * a few pixels away — matching how phone cameras bloom a strong indoor laser into white.
 *
 * Buffers are reused between calls; [values] is valid until the next [build]. Not thread-safe.
 */
class ColorCellMap(private val params: ScorerParams = ScorerParams()) {
    private val gate = HueGate(params)

    var gridWidth = 0
        private set
    var gridHeight = 0
        private set
    val cellCount: Int get() = gridWidth * gridHeight

    /** Per-cell coloured intensity 0..1, row-major, [cellCount] entries in use. */
    var values = FloatArray(0)
        private set

    /** Mean luma (0..255) of the analysed area from the last [build]. */
    var meanLuma = 0f
        private set

    fun build(frame: LumaChromaFrame, roi: Roi, hue: BeamHue) {
        val x0 = ((roi.centerX - roi.width / 2f) * frame.width).toInt().coerceIn(0, frame.width - 1)
        val x1 = ((roi.centerX + roi.width / 2f) * frame.width).toInt().coerceIn(x0 + 1, frame.width)
        val y0 = ((roi.centerY - roi.height / 2f) * frame.height).toInt().coerceIn(0, frame.height - 1)
        val y1 = ((roi.centerY + roi.height / 2f) * frame.height).toInt().coerceIn(y0 + 1, frame.height)
        val cell = params.cellSize.coerceAtLeast(1)
        gridWidth = (x1 - x0 + cell - 1) / cell
        gridHeight = (y1 - y0 + cell - 1) / cell
        val n = gridWidth * gridHeight
        if (values.size < n) values = FloatArray(n)
        values.fill(0f, 0, n)

        var lumaSum = 0L
        for (y in y0 until y1) {
            val rowBase = ((y - y0) / cell) * gridWidth
            for (x in x0 until x1) {
                val luma = frame.lumaAt(x, y)
                lumaSum += luma
                val u = frame.uAt(x, y) - 128
                val v = frame.vAt(x, y) - 128
                var s = gate.strength(luma, u, v, hue)
                if (s <= 0f && luma >= params.clipLuma && isNeutral(u, v)) {
                    s = haloStrength(frame, x, y, hue)
                }
                if (s > 0f) {
                    val c = rowBase + (x - x0) / cell
                    if (s > values[c]) values[c] = s
                }
            }
        }
        meanLuma = lumaSum.toFloat() / ((x1 - x0) * (y1 - y0))
    }

    private fun isNeutral(u: Int, v: Int): Boolean {
        val tol = params.clipChromaTolerance
        return u in -tol..tol && v in -tol..tol
    }

    /** Max hue strength among the four cardinal neighbours at [ScorerParams.haloRadius]. */
    private fun haloStrength(frame: LumaChromaFrame, x: Int, y: Int, hue: BeamHue): Float {
        val d = params.haloRadius
        var best = 0f
        for (k in 0 until 4) {
            val sx = when (k) {
                0 -> x - d
                1 -> x + d
                else -> x
            }.coerceIn(0, frame.width - 1)
            val sy = when (k) {
                2 -> y - d
                3 -> y + d
                else -> y
            }.coerceIn(0, frame.height - 1)
            val s = gate.strength(
                frame.lumaAt(sx, sy),
                frame.uAt(sx, sy) - 128,
                frame.vAt(sx, sy) - 128,
                hue,
            )
            if (s > best) best = s
        }
        return if (best >= params.haloMin) best else 0f
    }
}
