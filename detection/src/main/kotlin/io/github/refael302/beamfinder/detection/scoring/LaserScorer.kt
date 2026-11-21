package io.github.refael302.beamfinder.detection.scoring

import io.github.refael302.beamfinder.detection.frame.LumaChromaFrame

enum class BeamHue { Red, Green }

/** Region of interest in normalized frame coordinates (0..1). */
data class Roi(
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val width: Float = 0.4f,
    val height: Float = 1f,
)

interface LaserScorer {
    /** Returns a laser-likeness score in 0..1 for the given frame/ROI. */
    fun score(frame: LumaChromaFrame, roi: Roi, hue: BeamHue): Float
}

/** Phase 1 stub: always scores 0. Replaced in Phase 3. */
object NoopLaserScorer : LaserScorer {
    override fun score(frame: LumaChromaFrame, roi: Roi, hue: BeamHue): Float = 0f
}
