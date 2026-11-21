package io.github.refael302.beamfinder.detection.geometry

/**
 * The detection band: a full-width horizontal strip centred on the guide line.
 * All values are normalized to the upright (as displayed) frame height, 0..1.
 */
data class GuideBand(val top: Float, val bottom: Float) {
    val height: Float get() = bottom - top
    val center: Float get() = (top + bottom) / 2f

    companion object {
        /**
         * Band half-height per unit of the user's "detection area width" setting.
         * roiWidth 0.4 (default) -> +/-10% of the frame height around the line.
         */
        const val HALF_HEIGHT_PER_ROI = 0.25f
        private const val MIN_ROI = 0.05f

        /** Business rule (SSOT) mapping guide line + roiWidth setting to the detection band. */
        fun of(guideY: Float, roiWidth: Float): GuideBand {
            val half = roiWidth.coerceIn(MIN_ROI, 1f) * HALF_HEIGHT_PER_ROI
            val y = guideY.coerceIn(0f, 1f)
            return GuideBand(
                top = (y - half).coerceAtLeast(0f),
                bottom = (y + half).coerceAtMost(1f),
            )
        }
    }
}
