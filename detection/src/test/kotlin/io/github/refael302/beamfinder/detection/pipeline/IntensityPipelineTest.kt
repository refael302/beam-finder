package io.github.refael302.beamfinder.detection.pipeline

import io.github.refael302.beamfinder.detection.Rgb
import io.github.refael302.beamfinder.detection.SyntheticFrames
import io.github.refael302.beamfinder.detection.calibration.AmbientCalibrator
import io.github.refael302.beamfinder.detection.frame.LumaChromaFrame
import io.github.refael302.beamfinder.detection.scoring.BeamHue
import io.github.refael302.beamfinder.detection.scoring.LaserScorer
import io.github.refael302.beamfinder.detection.scoring.Roi
import io.github.refael302.beamfinder.detection.scoring.ScoreBreakdown
import io.github.refael302.beamfinder.detection.sensitivity.SensitivityModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntensityPipelineTest {
    /** Scorer whose output is set directly by the test. */
    private class FakeScorer(var value: Float = 0f) : LaserScorer {
        override fun analyze(frame: LumaChromaFrame, roi: Roi, hue: BeamHue) =
            ScoreBreakdown(value, value, 0f, 0f, 50f)
    }

    private val tiny = SyntheticFrames.uniform(Rgb.Black, width = 4, height = 4)
    private fun at(ms: Long) = LumaChromaFrame(4, 4, tiny.luma, tiny.chromaU, tiny.chromaV, ms * 1_000_000L, 1)

    private class Harness(val scorer: FakeScorer = FakeScorer()) {
        val pipeline = IntensityPipeline(scorer, AmbientCalibrator(durationMs = 1500)).apply {
            thresholds = SensitivityModel.Thresholds(startThreshold = 0.2f, fullThreshold = 0.8f)
        }
        var now = 0L
    }

    private fun Harness.run(score: Float, untilMs: Long, stepMs: Long = 33): DetectionResult {
        scorer.value = score
        var last = DetectionResult.Empty
        while (now <= untilMs) {
            last = pipeline.process(at(now), Roi.Full, BeamHue.Red)
            now += stepMs
        }
        return last
    }

    @Test
    fun calibratesForAboutOneAndAHalfSeconds() {
        val h = Harness()
        val early = h.run(0.1f, untilMs = 1000)
        assertTrue(early.calibrating)
        assertEquals(0f, early.intensity)
        assertTrue(early.calibrationProgress in 0.5f..0.8f, "progress=${early.calibrationProgress}")
        val done = h.run(0.1f, untilMs = 1600)
        assertFalse(done.calibrating)
    }

    @Test
    fun ambientBaselineIsSubtracted() {
        val h = Harness()
        h.run(0.3f, untilMs = 1600) // calibrate on a steady 0.3 ambient score
        val r = h.run(0.3f, untilMs = 2500)
        assertTrue(r.intensity < 0.02f, "intensity=${r.intensity}")
        assertFalse(r.detected)
    }

    @Test
    fun strongBeamRaisesIntensityAndDetects() {
        val h = Harness()
        h.run(0f, untilMs = 1600)
        val r = h.run(1f, untilMs = 2000)
        assertTrue(r.intensity > 0.95f, "intensity=${r.intensity}")
        assertTrue(r.detected)
    }

    @Test
    fun peakIsHeldThenReleased() {
        val h = Harness()
        h.run(0f, untilMs = 1600)
        h.run(1f, untilMs = 2000)
        // Beam vanishes: during the hold window intensity stays at the peak.
        val held = h.run(0f, untilMs = h.now + 200)
        assertTrue(held.intensity > 0.95f, "held=${held.intensity}")
        // Well after hold + release, intensity decays to ~0 and detection clears.
        val released = h.run(0f, untilMs = h.now + 1500)
        assertTrue(released.intensity < 0.05f, "released=${released.intensity}")
        assertFalse(released.detected)
    }

    @Test
    fun singleFrameSpikeIsSmoothedButHeld() {
        val h = Harness()
        h.run(0f, untilMs = 1600)
        val spike = h.run(1f, untilMs = h.now) // exactly one frame
        assertTrue(spike.intensity in 0.2f..0.9f, "EMA should damp a 1-frame spike: ${spike.intensity}")
        val afterHold = h.run(0f, untilMs = h.now + 150)
        assertTrue(afterHold.intensity > spike.intensity, "peak-hold keeps rising toward the held peak")
    }

    @Test
    fun hysteresisKeepsDetectionThroughSmallDips() {
        val h = Harness()
        h.run(0f, untilMs = 1600)
        // floor = 0.02 margin (noise 0) -> raw 0.30 normalizes to ~0.286, above start 0.2.
        assertTrue(h.run(0.30f, untilMs = h.now + 1000).detected)
        // raw 0.19 -> ~0.173: below start (0.2) but above start - hysteresis (0.15).
        assertTrue(h.run(0.19f, untilMs = h.now + 1500).detected)
        // raw 0.10 -> ~0.08: below the release level.
        assertFalse(h.run(0.10f, untilMs = h.now + 1500).detected)
        // raw 0.19 again: not enough to re-trigger from idle.
        assertFalse(h.run(0.19f, untilMs = h.now + 1500).detected)
    }

    @Test
    fun recalibrateRestartsCalibration() {
        val h = Harness()
        h.run(0f, untilMs = 1600)
        h.pipeline.recalibrate()
        val r = h.run(0f, untilMs = h.now)
        assertTrue(r.calibrating)
        assertEquals(0f, r.intensity)
    }

    @Test
    fun fpsFollowsFrameTimestamps() {
        val h = Harness()
        val r = h.run(0f, untilMs = 3000, stepMs = 33)
        assertTrue(r.fps in 29f..32f, "fps=${r.fps}")
    }

    @Test
    fun endToEndWithRealScorer() {
        val pipeline = IntensityPipeline().apply { thresholds = SensitivityModel.thresholds(50) }
        var t = 0L
        repeat(60) { pipeline.process(SyntheticFrames.uniform(Rgb.DimGray, t), Roi.Full, BeamHue.Red); t += 33 }
        var last = DetectionResult.Empty
        repeat(15) { last = pipeline.process(SyntheticFrames.horizontalLine(Rgb.RedLaser, background = Rgb.DimGray, timestampMs = t), Roi.Full, BeamHue.Red); t += 33 }
        assertFalse(last.calibrating)
        assertTrue(last.intensity > 0.9f, "intensity=${last.intensity}")
        assertTrue(last.detected)
    }
}
