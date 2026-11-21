package io.github.refael302.beamfinder.detection.frame

/**
 * Platform-independent camera frame: full-resolution luma plane plus optional
 * (subsampled) chroma planes. Row stride is assumed to equal [width].
 */
class LumaChromaFrame(
    val width: Int,
    val height: Int,
    val luma: ByteArray,
    val chromaU: ByteArray? = null,
    val chromaV: ByteArray? = null,
    val timestampNanos: Long = 0L,
) {
    init {
        require(width > 0 && height > 0) { "Frame size must be positive" }
        require(luma.size == width * height) { "Luma size ${luma.size} != $width x $height" }
    }

    /** Luma value 0..255 at (x, y). */
    fun lumaAt(x: Int, y: Int): Int = luma[y * width + x].toInt() and 0xFF
}
