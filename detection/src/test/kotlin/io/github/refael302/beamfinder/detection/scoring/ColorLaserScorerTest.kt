package io.github.refael302.beamfinder.detection.scoring

import io.github.refael302.beamfinder.detection.Rgb
import io.github.refael302.beamfinder.detection.SyntheticFrames
import io.github.refael302.beamfinder.detection.geometry.GuideBand
import kotlin.test.Test
import kotlin.test.assertTrue

class ColorLaserScorerTest {
    private val scorer = ColorLaserScorer()

    @Test
    fun darkFrameScoresNearZero() {
        val s = scorer.score(SyntheticFrames.uniform(Rgb.Black), Roi.Full, BeamHue.Red)
        assertTrue(s < 0.05f, "score=$s")
    }

    @Test
    fun redLineAcrossFrameScoresHigh() {
        val s = scorer.analyze(SyntheticFrames.horizontalLine(Rgb.RedLaser), Roi.Full, BeamHue.Red)
        assertTrue(s.score > 0.8f, "breakdown=$s")
        assertTrue(s.coverage > 0.9f, "breakdown=$s")
    }

    @Test
    fun smallRedSpotScoresViaPeak() {
        val frame = SyntheticFrames.horizontalLine(Rgb.RedLaser, x0 = 70, x1 = 78, thickness = 7)
        val s = scorer.analyze(frame, Roi.Full, BeamHue.Red)
        assertTrue(s.score in 0.5f..0.95f, "breakdown=$s")
        assertTrue(s.peak > 0.9f, "breakdown=$s")
    }

    @Test
    fun hueSelectivity() {
        val green = SyntheticFrames.horizontalLine(Rgb.GreenLaser)
        assertTrue(scorer.score(green, Roi.Full, BeamHue.Red) < 0.05f)
        assertTrue(scorer.score(green, Roi.Full, BeamHue.Green) > 0.8f)
        val red = SyntheticFrames.horizontalLine(Rgb.RedLaser)
        assertTrue(scorer.score(red, Roi.Full, BeamHue.Green) < 0.05f)
    }

    @Test
    fun beamOutsideRoiBandIsIgnored() {
        val frame = SyntheticFrames.horizontalLine(Rgb.RedLaser, rowY = 5)
        val band = GuideBand.of(guideY = 0.75f, roiWidth = 0.4f) // rows ~39..51 of 60
        val roi = Roi(centerX = 0.5f, centerY = band.center, width = 1f, height = band.height)
        assertTrue(scorer.score(frame, roi, BeamHue.Red) < 0.05f)
        val inside = SyntheticFrames.horizontalLine(Rgb.RedLaser, rowY = 45)
        assertTrue(scorer.score(inside, roi, BeamHue.Red) > 0.8f)
    }

    @Test
    fun dullRedAmbientScoresLow() {
        val s = scorer.score(SyntheticFrames.uniform(Rgb.DullRed), Roi.Full, BeamHue.Red)
        assertTrue(s < 0.3f, "score=$s")
    }

    @Test
    fun whiteGlareIsPenalised() {
        val s = scorer.analyze(SyntheticFrames.uniform(Rgb.Wall), Roi.Full, BeamHue.Red)
        assertTrue(s.score < 0.05f, "breakdown=$s")
        assertTrue(s.glare > 0.9f, "breakdown=$s")
    }

    @Test
    fun redBeamOnHalfGlareFrameIsAttenuated() {
        val clean = scorer.score(SyntheticFrames.horizontalLine(Rgb.RedLaser), Roi.Full, BeamHue.Red)
        val glary = scorer.score(
            SyntheticFrames.frame { x, y ->
                when {
                    y in 29..31 -> Rgb.RedLaser
                    x < 80 -> Rgb.White
                    else -> Rgb.Black
                }
            },
            Roi.Full,
            BeamHue.Red,
        )
        assertTrue(glary < clean, "clean=$clean glary=$glary")
    }

    @Test
    fun clippedWhiteCoreWithRedHaloScoresHigh() {
        // White saturated core (rows 28..32) wrapped in a red halo (rows 22..38).
        val frame = SyntheticFrames.frame { _, y ->
            when (y) {
                in 28..32 -> Rgb.White
                in 22..38 -> Rgb.RedLaser
                else -> Rgb.Black
            }
        }
        val s = scorer.analyze(frame, Roi.Full, BeamHue.Red)
        assertTrue(s.score > 0.8f, "breakdown=$s")
        assertTrue(s.glare < 0.05f, "core must not count as glare: $s")
    }

    @Test
    fun clippedWhiteWithoutHaloIsGlare() {
        val frame = SyntheticFrames.horizontalLine(Rgb.White, thickness = 5)
        val s = scorer.analyze(frame, Roi.Full, BeamHue.Red)
        assertTrue(s.score < 0.05f, "breakdown=$s")
        assertTrue(s.glare > 0f, "breakdown=$s")
    }

    @Test
    fun subsampledChromaWorks() {
        val frame = SyntheticFrames.frame(chromaStep = 2) { _, y -> if (y in 28..33) Rgb.RedLaser else Rgb.Black }
        assertTrue(scorer.score(frame, Roi.Full, BeamHue.Red) > 0.8f)
    }
}
