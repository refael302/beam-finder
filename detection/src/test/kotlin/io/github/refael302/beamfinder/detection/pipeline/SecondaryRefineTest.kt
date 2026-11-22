package io.github.refael302.beamfinder.detection.pipeline

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

class SecondaryRefineTest {
    private class FakeScorer(
        var value: Float = 0f,
        var peakX: Float = 0.5f,
        var peakY: Float = 0.5f,
        var flash: Float = 0f,
        var peak: Float = 0f,
    ) : LaserScorer {
        override fun analyze(frame: LumaChromaFrame, roi: Roi, hue: BeamHue) =
            ScoreBreakdown(
                score = value,
                flash = flash,
                peak = peak,
                coverage = 0f,
                meanLuma = 50f,
                peakX = peakX,
                peakY = peakY,
            )
    }

    private fun blank(ms: Long) = LumaChromaFrame(
        width = 4,
        height = 4,
        luma = ByteArray(16),
        chromaU = ByteArray(16) { 128.toByte() },
        chromaV = ByteArray(16) { 128.toByte() },
        timestampNanos = ms * 1_000_000L,
        chromaStep = 1,
    )

    @Test
    fun shouldRefineOnlyInWeakInterestingBand() {
        val pipeline = IntensityPipeline(
            scorer = FakeScorer(),
            calibrator = AmbientCalibrator(durationMs = 100),
        )
        assertFalse(pipeline.shouldRefine(ScoreBreakdown(0.01f, 0.01f, 0.01f, 0f, 50f)))
        assertFalse(pipeline.shouldRefine(ScoreBreakdown(0.8f, 0.8f, 0.8f, 0f, 50f)))
        assertTrue(pipeline.shouldRefine(ScoreBreakdown(0.2f, 0.15f, 0.2f, 0f, 50f)))
        assertTrue(pipeline.shouldRefine(ScoreBreakdown(0.2f, 0f, 0.2f, 0f, 50f)))
    }

    @Test
    fun secondaryCropCanRaiseRawScore() {
        val wide = FakeScorer(value = 0.15f, flash = 0.15f, peak = 0.2f, peakX = 0.3f, peakY = 0.7f)
        val crop = FakeScorer(value = 0.7f, flash = 0f, peak = 0.7f)
        val pipeline = IntensityPipeline(
            scorer = wide,
            calibrator = AmbientCalibrator(durationMs = 200),
            cropScorer = crop,
        ).apply {
            thresholds = SensitivityModel.Thresholds(0.2f, 0.8f)
        }

        // Finish calibration on weak ambient.
        var t = 0L
        while (t <= 250L) {
            pipeline.process(blank(t), BeamHue.Red)
            t += 33L
        }

        var requestedX = -1f
        var requestedY = -1f
        val result = pipeline.process(blank(t), BeamHue.Red) { x, y ->
            requestedX = x
            requestedY = y
            blank(t)
        }
        assertEquals(0.3f, requestedX)
        assertEquals(0.7f, requestedY)
        assertEquals(0.7f, result.rawScore, 0.001f)
    }

    @Test
    fun secondaryNotInvokedWhenWideAlreadyStrong() {
        val wide = FakeScorer(value = 0.9f, flash = 0.9f, peak = 0.9f)
        val crop = FakeScorer(value = 1f)
        val pipeline = IntensityPipeline(
            scorer = wide,
            calibrator = AmbientCalibrator(durationMs = 200),
            cropScorer = crop,
        )
        var t = 0L
        while (t <= 250L) {
            pipeline.process(blank(t), BeamHue.Red)
            t += 33L
        }
        var called = false
        val result = pipeline.process(blank(t), BeamHue.Red) { _, _ ->
            called = true
            blank(t)
        }
        assertFalse(called)
        assertEquals(0.9f, result.rawScore, 0.001f)
    }
}
