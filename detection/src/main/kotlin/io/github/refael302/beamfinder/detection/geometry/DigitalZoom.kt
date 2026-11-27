package io.github.refael302.beamfinder.detection.geometry

/**
 * App-side digital zoom (SSOT). Hardware zoom stays at 1× on a single physical lens;
 * [scaleFromLinear] drives both the preview scale and the analysis center-crop.
 */
object DigitalZoom {
    /** Maximum magnification at linearZoom = 1 (1×…[MAX_SCALE]×). */
    const val MAX_SCALE = 4f

    /** Maps session linear zoom 0..1 to a magnification ≥ 1. */
    fun scaleFromLinear(linearZoom: Float): Float =
        1f + linearZoom.coerceIn(0f, 1f) * (MAX_SCALE - 1f)

    /**
     * Center-crops [crop] by [scale] so the remaining window is `1/scale` of each side
     * (same FOV as scaling the preview by [scale] and clipping).
     */
    fun insetCrop(crop: PixelRect, scale: Float): PixelRect {
        val s = scale.coerceAtLeast(1f)
        if (s <= 1.0001f) return crop
        val frac = 1f / s
        val newW = (crop.width * frac).toInt().coerceAtLeast(1)
        val newH = (crop.height * frac).toInt().coerceAtLeast(1)
        val left = crop.left + (crop.width - newW) / 2
        val top = crop.top + (crop.height - newH) / 2
        return PixelRect(left, top, newW, newH)
    }
}
