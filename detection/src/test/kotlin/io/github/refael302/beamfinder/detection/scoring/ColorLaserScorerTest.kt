package io.github.refael302.beamfinder.detection.scoring

import io.github.refael302.beamfinder.detection.Rgb
import io.github.refael302.beamfinder.detection.Spot
import io.github.refael302.beamfinder.detection.SyntheticFrames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Static (single-frame) hue gate + cell map behaviour. */
class ColorLaserScorerTest {
    private val scorer = ColorLaserScorer()

    private fun spotScore(color: Rgb, hue: BeamHue, cx: Int = 80, cy: Int = 30, radius: Int = 3) =
        scorer.score(SyntheticFrames.spots(Spot(color, cx, cy, radius)), Roi.Full, hue)

    @Test
    fun darkAndGrayFramesScoreZero() {
        assertEquals(0f, scorer.score(SyntheticFrames.uniform(Rgb.Black), Roi.Full, BeamHue.Red))
        assertEquals(0f, scorer.score(SyntheticFrames.uniform(Rgb.DimGray), Roi.Full, BeamHue.Green))
    }

    @Test
    fun smallRedSpotAnywhereInFrameScoresHigh() {
        // Corners and edges: the full frame is scanned, not a guide band.
        for ((cx, cy) in listOf(5 to 5, 154 to 4, 80 to 30, 3 to 56, 156 to 55)) {
            val s = spotScore(Rgb.RedLaser, BeamHue.Red, cx, cy)
            assertTrue(s > 0.9f, "spot at ($cx,$cy) score=$s")
        }
    }

    @Test
    fun tinyOnePixelSpotSurvivesMaxPooling() {
        val s = spotScore(Rgb.RedLaser, BeamHue.Red, radius = 0)
        assertTrue(s > 0.9f, "score=$s")
    }

    @Test
    fun hueSelectivity() {
        assertTrue(spotScore(Rgb.GreenLaser, BeamHue.Green) > 0.9f)
        assertEquals(0f, spotScore(Rgb.GreenLaser, BeamHue.Red))
        assertEquals(0f, spotScore(Rgb.RedLaser, BeamHue.Green))
    }

    @Test
    fun orangeAndWarmLightAreRejectedInRedMode() {
        for (c in listOf(Rgb.Orange, Rgb.Amber, Rgb.Warm, Rgb.Yellow)) {
            assertEquals(0f, spotScore(c, BeamHue.Red), "colour $c must not pass the red gate")
            assertEquals(0f, scorer.score(SyntheticFrames.uniform(c), Roi.Full, BeamHue.Red), "uniform $c")
        }
    }

    @Test
    fun yellowAndCyanAreRejectedInGreenMode() {
        for (c in listOf(Rgb.Yellow, Rgb.YellowGreen, Rgb.Cyan, Rgb.Orange)) {
            assertEquals(0f, spotScore(c, BeamHue.Green), "colour $c must not pass the green gate")
        }
    }

    @Test
    fun whiteAndDullRedScoreZero() {
        assertEquals(0f, scorer.score(SyntheticFrames.uniform(Rgb.Wall), Roi.Full, BeamHue.Red))
        assertEquals(0f, scorer.score(SyntheticFrames.uniform(Rgb.White), Roi.Full, BeamHue.Red))
        assertEquals(0f, scorer.score(SyntheticFrames.uniform(Rgb.DullRed), Roi.Full, BeamHue.Red))
    }

    @Test
    fun clippedWhiteCoreWithRedHaloStillScores() {
        val frame = SyntheticFrames.spots(Spot(Rgb.RedLaser, 80, 30, radius = 5), Spot(Rgb.White, 80, 30, radius = 2))
        assertTrue(scorer.score(frame, Roi.Full, BeamHue.Red) > 0.9f)
    }

    @Test
    fun subsampledChromaWorks() {
        val frame = SyntheticFrames.spots(Spot(Rgb.RedLaser, 80, 30, radius = 3), chromaStep = 2)
        assertTrue(scorer.score(frame, Roi.Full, BeamHue.Red) > 0.9f)
    }

    @Test
    fun roiStillRestrictsWhenAskedExplicitly() {
        val frame = SyntheticFrames.spots(Spot(Rgb.RedLaser, 80, 5, radius = 2))
        assertEquals(0f, scorer.score(frame, Roi(centerY = 0.75f, height = 0.3f), BeamHue.Red))
    }
}
