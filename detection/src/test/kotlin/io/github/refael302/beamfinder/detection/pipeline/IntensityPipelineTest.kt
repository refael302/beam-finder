package io.github.refael302.beamfinder.detection.pipeline

import io.github.refael302.beamfinder.detection.Rgb
import io.github.refael302.beamfinder.detection.Spot
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
            ScoreBreakdown(score = value, flash = value, peak = 0f, coverage = 0f, meanLuma = 50f)
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
            last = pipeline.process(at(now), BeamHue.Red)
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

    /** Real [IntensityPipeline] (FlashDetector) fed synthetic full frames. */
    private class EndToEnd(val hue: BeamHue = BeamHue.Red) {
        val pipeline = IntensityPipeline().apply { thresholds = SensitivityModel.thresholds(50) }
        var t = 0L

        /** Runs [n] frames and returns (last result, max intensity, any detected). */
        fun run(n: Int, vararg spots: Spot): Triple<DetectionResult, Float, Boolean> =
            run(n) { SyntheticFrames.spots(*spots, timestampMs = it) }

        fun run(
            n: Int,
            frameAt: (timestampMs: Long) -> io.github.refael302.beamfinder.detection.frame.LumaChromaFrame,
        ): Triple<DetectionResult, Float, Boolean> {
            var last = DetectionResult.Empty
            var max = 0f
            var any = false
            repeat(n) {
                last = pipeline.process(frameAt(t), hue)
                if (!last.calibrating) {
                    max = maxOf(max, last.intensity)
                    any = any || last.detected
                }
                t += 33
            }
            return Triple(last, max, any)
        }
    }

    @Test
    fun endToEndFullFrameRedAmbientStaysQuiet() {
        val e = EndToEnd()
        e.run(60) // calibrate on empty scene
        // Pan onto a full red frame, then hold — must settle (not a small laser spot).
        e.run(5) { SyntheticFrames.uniform(Rgb.RedLaser, it) }
        val (last, max, any) = e.run(60) { SyntheticFrames.uniform(Rgb.RedLaser, it) }
        assertFalse(last.calibrating)
        assertTrue(max < 0.35f, "full-frame red must not stay loud: max=$max")
        // After settle, no detection.
        assertFalse(any && last.intensity > 0.05f)
        assertTrue(last.intensity < 0.05f, "settled intensity=${last.intensity}")
    }

    @Test
    fun endToEndHeldSmallLaserKeepsDetecting() {
        val e = EndToEnd()
        e.run(60)
        val (last, max, any) = e.run(60, Spot(Rgb.RedLaser, 80, 30, radius = 2))
        assertTrue(max > 0.9f, "max=$max")
        assertTrue(any)
        assertTrue(last.intensity > 0.5f, "held laser must keep intensity: ${last.intensity}")
    }

    @Test
    fun endToEndSuddenRedFlashAnywhereDetects() {
        val e = EndToEnd()
        e.run(60)
        // Small spot far from the centre / guide line (top-right corner).
        val (_, max, any) = e.run(8, Spot(Rgb.RedLaser, 150, 4, radius = 1))
        assertTrue(max > 0.9f, "max=$max")
        assertTrue(any)
    }

    @Test
    fun endToEndOrangeFlashIsIgnoredInRedMode() {
        val e = EndToEnd()
        e.run(60)
        val (_, max, any) = e.run(15, Spot(Rgb.Orange, 80, 30, radius = 4))
        assertTrue(max < 0.05f, "max=$max")
        assertFalse(any)
    }

    @Test
    fun endToEndGreenFlashDetectsInGreenMode() {
        val e = EndToEnd(BeamHue.Green)
        e.run(60)
        assertFalse(e.run(15, Spot(Rgb.Yellow, 80, 30)).third, "yellow must not trigger green mode")
        val (_, max, any) = e.run(8, Spot(Rgb.GreenLaser, 40, 50, radius = 2))
        assertTrue(max > 0.9f, "max=$max")
        assertTrue(any)
    }

    @Test
    fun sweepingPastTheBeamBeepsEachPass() {
        val e = EndToEnd()
        e.run(60)
        repeat(3) { pass ->
            val (_, max, any) = e.run(3, Spot(Rgb.RedLaser, 30 + pass * 40, 30, radius = 2))
            assertTrue(max > 0.5f && any, "pass $pass max=$max")
            e.run(45) // beam out of view; intensity releases
        }
    }
}
