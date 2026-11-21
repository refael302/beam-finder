package io.github.refael302.beamfinder.detection.calibration

import io.github.refael302.beamfinder.detection.frame.LumaChromaFrame

data class CalibrationResult(
    val baselineLuma: Float = 0f,
    val noiseFloor: Float = 0f,
)

interface Calibrator {
    val result: CalibrationResult
    fun update(frame: LumaChromaFrame)
    fun reset()
}

/** Phase 1 stub: never calibrates. */
class NoopCalibrator : Calibrator {
    override var result: CalibrationResult = CalibrationResult()
        private set

    override fun update(frame: LumaChromaFrame) = Unit

    override fun reset() {
        result = CalibrationResult()
    }
}
