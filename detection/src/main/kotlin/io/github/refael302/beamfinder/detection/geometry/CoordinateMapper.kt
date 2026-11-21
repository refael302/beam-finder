package io.github.refael302.beamfinder.detection.geometry

/** Integer rectangle in analysis-buffer pixel coordinates. */
data class PixelRect(val left: Int, val top: Int, val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0) { "Rect size must be positive" }
    }
}

/**
 * Maps between the upright (as displayed in the preview) coordinate space and the raw
 * analysis buffer.
 *
 * - [crop] is the analysis image crop rect (CameraX `ImageProxy.cropRect`). With a shared
 *   ViewPort it covers exactly the area visible in the preview.
 * - [rotationDegrees] is the clockwise rotation that makes the buffer upright
 *   (CameraX `ImageInfo.rotationDegrees`).
 * - [mirrored] mirrors horizontally in upright space (front camera preview). Vertical
 *   positions such as the guide line are unaffected.
 *
 * Upright pixel coordinates run over [0, uprightWidth) x [0, uprightHeight).
 */
class CoordinateMapper(
    val crop: PixelRect,
    rotationDegrees: Int,
    val mirrored: Boolean = false,
) {
    val rotation: Int = ((rotationDegrees % 360) + 360) % 360

    init {
        require(rotation % 90 == 0) { "Rotation must be a multiple of 90, was $rotationDegrees" }
    }

    private val swapsAxes = rotation == 90 || rotation == 270

    val uprightWidth: Int = if (swapsAxes) crop.height else crop.width
    val uprightHeight: Int = if (swapsAxes) crop.width else crop.height

    /** Buffer x for upright pixel (xu, yu). */
    fun bufferX(xu: Int, yu: Int): Int {
        val x = if (mirrored) uprightWidth - 1 - xu else xu
        return crop.left + when (rotation) {
            0 -> x
            90 -> yu
            180 -> crop.width - 1 - x
            else -> crop.width - 1 - yu
        }
    }

    /** Buffer y for upright pixel (xu, yu). */
    fun bufferY(xu: Int, yu: Int): Int {
        val x = if (mirrored) uprightWidth - 1 - xu else xu
        return crop.top + when (rotation) {
            0 -> yu
            90 -> crop.height - 1 - x
            180 -> crop.height - 1 - yu
            else -> x
        }
    }

    /** Upright pixel row for a normalized vertical position (e.g. the guide line), 0..1. */
    fun uprightRow(normalizedY: Float): Int =
        (normalizedY.coerceIn(0f, 1f) * uprightHeight).toInt().coerceIn(0, uprightHeight - 1)

    /**
     * Maps a normalized upright point (as seen in the preview) to normalized buffer
     * coordinates relative to the full buffer of size [bufferWidth] x [bufferHeight].
     */
    fun normalizedToBuffer(xn: Float, yn: Float, bufferWidth: Int, bufferHeight: Int): Pair<Float, Float> {
        val xu = (xn.coerceIn(0f, 1f) * (uprightWidth - 1)).toInt()
        val yu = (yn.coerceIn(0f, 1f) * (uprightHeight - 1)).toInt()
        return bufferX(xu, yu).toFloat() / bufferWidth to bufferY(xu, yu).toFloat() / bufferHeight
    }

    /**
     * The buffer-space rectangle covered by an upright horizontal band (normalized top..bottom).
     * For a 90/270 rotation a horizontal guide line becomes a vertical column in the buffer.
     */
    fun bandToBufferRect(band: GuideBand): PixelRect {
        val r0 = uprightRow(band.top)
        val r1 = uprightRow(band.bottom).coerceAtLeast(r0)
        val xs = intArrayOf(bufferX(0, r0), bufferX(uprightWidth - 1, r0), bufferX(0, r1), bufferX(uprightWidth - 1, r1))
        val ys = intArrayOf(bufferY(0, r0), bufferY(uprightWidth - 1, r0), bufferY(0, r1), bufferY(uprightWidth - 1, r1))
        val left = xs.min()
        val top = ys.min()
        return PixelRect(left, top, xs.max() - left + 1, ys.max() - top + 1)
    }
}
