package io.github.refael302.beamfinder.detection.frame

/**
 * Platform-independent camera frame: full-resolution luma plane plus optional chroma planes
 * (Cb = [chromaU], Cr = [chromaV], 128-offset). Row stride is assumed to equal [width].
 *
 * Chroma planes are subsampled by [chromaStep] in both axes (2 = YUV 4:2:0, 1 = full resolution).
 */
class LumaChromaFrame(
    val width: Int,
    val height: Int,
    val luma: ByteArray,
    val chromaU: ByteArray? = null,
    val chromaV: ByteArray? = null,
    val timestampNanos: Long = 0L,
    val chromaStep: Int = 2,
) {
    /** Width of the chroma planes. */
    val chromaWidth: Int = (width + chromaStep - 1) / chromaStep

    /** Height of the chroma planes. */
    val chromaHeight: Int = (height + chromaStep - 1) / chromaStep

    init {
        require(width > 0 && height > 0) { "Frame size must be positive" }
        require(chromaStep >= 1) { "chromaStep must be >= 1" }
        require(luma.size == width * height) { "Luma size ${luma.size} != $width x $height" }
        require((chromaU == null) == (chromaV == null)) { "Provide both chroma planes or neither" }
        if (chromaU != null && chromaV != null) {
            val expected = chromaWidth * chromaHeight
            require(chromaU.size == expected && chromaV.size == expected) {
                "Chroma size ${chromaU.size}/${chromaV.size} != $chromaWidth x $chromaHeight"
            }
        }
    }

    /** Luma value 0..255 at (x, y). */
    fun lumaAt(x: Int, y: Int): Int = luma[y * width + x].toInt() and 0xFF

    /** Cb value 0..255 at full-resolution (x, y); 128 (neutral) when no chroma. */
    fun uAt(x: Int, y: Int): Int {
        val u = chromaU ?: return NEUTRAL_CHROMA
        return u[(y / chromaStep) * chromaWidth + x / chromaStep].toInt() and 0xFF
    }

    /** Cr value 0..255 at full-resolution (x, y); 128 (neutral) when no chroma. */
    fun vAt(x: Int, y: Int): Int {
        val v = chromaV ?: return NEUTRAL_CHROMA
        return v[(y / chromaStep) * chromaWidth + x / chromaStep].toInt() and 0xFF
    }

    companion object {
        const val NEUTRAL_CHROMA = 128
    }
}
