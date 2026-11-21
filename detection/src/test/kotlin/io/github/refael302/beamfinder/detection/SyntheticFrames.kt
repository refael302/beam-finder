package io.github.refael302.beamfinder.detection

import io.github.refael302.beamfinder.detection.frame.LumaChromaFrame
import kotlin.math.roundToInt

/** RGB color 0..255 per channel. */
data class Rgb(val r: Int, val g: Int, val b: Int) {
    companion object {
        val Black = Rgb(10, 10, 10)
        val DimGray = Rgb(60, 60, 60)
        val White = Rgb(255, 255, 255)
        val Wall = Rgb(215, 215, 210)
        val RedLaser = Rgb(235, 25, 25)
        val GreenLaser = Rgb(25, 235, 40)
        val DullRed = Rgb(80, 50, 45)
    }
}

/** Builds full-chroma-resolution YUV frames from an RGB painter (BT.601 full range). */
object SyntheticFrames {
    fun frame(
        width: Int = 160,
        height: Int = 60,
        timestampMs: Long = 0L,
        chromaStep: Int = 1,
        paint: (x: Int, y: Int) -> Rgb,
    ): LumaChromaFrame {
        val luma = ByteArray(width * height)
        val cw = (width + chromaStep - 1) / chromaStep
        val ch = (height + chromaStep - 1) / chromaStep
        val u = ByteArray(cw * ch)
        val v = ByteArray(cw * ch)
        for (y in 0 until height) for (x in 0 until width) {
            val c = paint(x, y)
            luma[y * width + x] = clamp(0.299 * c.r + 0.587 * c.g + 0.114 * c.b).toByte()
            if (x % chromaStep == 0 && y % chromaStep == 0) {
                val i = (y / chromaStep) * cw + x / chromaStep
                u[i] = clamp(128 - 0.168736 * c.r - 0.331264 * c.g + 0.5 * c.b).toByte()
                v[i] = clamp(128 + 0.5 * c.r - 0.418688 * c.g - 0.081312 * c.b).toByte()
            }
        }
        return LumaChromaFrame(width, height, luma, u, v, timestampMs * 1_000_000L, chromaStep)
    }

    fun uniform(color: Rgb, timestampMs: Long = 0L, width: Int = 160, height: Int = 60) =
        frame(width, height, timestampMs) { _, _ -> color }

    /** Horizontal beam line [thickness] px tall, centred at row [rowY], spanning [x0, x1). */
    fun horizontalLine(
        beam: Rgb,
        background: Rgb = Rgb.Black,
        rowY: Int = 30,
        thickness: Int = 3,
        x0: Int = 0,
        x1: Int = 160,
        width: Int = 160,
        height: Int = 60,
        timestampMs: Long = 0L,
    ) = frame(width, height, timestampMs) { x, y ->
        if (x in x0 until x1 && y in (rowY - thickness / 2)..(rowY + thickness / 2)) beam else background
    }

    private fun clamp(d: Double): Int = d.roundToInt().coerceIn(0, 255)
}
