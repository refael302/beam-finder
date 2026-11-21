package io.github.refael302.beamfinder.detection.pipeline

import io.github.refael302.beamfinder.detection.calibration.Calibrator
import io.github.refael302.beamfinder.detection.calibration.NoopCalibrator
import io.github.refael302.beamfinder.detection.frame.LumaChromaFrame
import io.github.refael302.beamfinder.detection.scoring.BeamHue
import io.github.refael302.beamfinder.detection.scoring.LaserScorer
import io.github.refael302.beamfinder.detection.scoring.NoopLaserScorer
import io.github.refael302.beamfinder.detection.scoring.Roi

/**
 * Frame -> intensity (0..1) pipeline. Phase 1 stub: passes the scorer output through.
 * Smoothing / calibration compensation arrive in Phase 3.
 */
class IntensityPipeline(
    private val scorer: LaserScorer = NoopLaserScorer,
    private val calibrator: Calibrator = NoopCalibrator(),
) {
    fun process(frame: LumaChromaFrame, roi: Roi, hue: BeamHue): Float {
        calibrator.update(frame)
        return scorer.score(frame, roi, hue).coerceIn(0f, 1f)
    }

    fun reset() = calibrator.reset()
}
