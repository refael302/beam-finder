package io.github.refael302.beamfinder.detection.scoring

import io.github.refael302.beamfinder.detection.Rgb
import io.github.refael302.beamfinder.detection.Spot
import io.github.refael302.beamfinder.detection.SyntheticFrames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Temporal (flash) behaviour of the live scorer. */
class FlashDetectorTest {
    private class Feed(val hue: BeamHue = BeamHue.Red) {
        val detector = FlashDetector()
        var now = 0L

        fun frame(vararg spots: Spot, background: Rgb = Rgb.DimGray): ScoreBreakdown {
            val r = detector.analyze(SyntheticFrames.spots(*spots, background = background, timestampMs = now), Roi.Full, hue)
            now += 33
            return r
        }

        fun uniform(color: Rgb): ScoreBreakdown {
            val r = detector.analyze(SyntheticFrames.uniform(color, now), Roi.Full, hue)
            now += 33
            return r
        }

        fun repeat(n: Int, block: Feed.() -> ScoreBreakdown): ScoreBreakdown {
            var last = ScoreBreakdown.Zero
            kotlin.repeat(n) { last = block() }
            return last
        }
    }

    private val lamp = Spot(Rgb.RedLamp, 20, 15, radius = 4)

    @Test
    fun firstFrameSeedsAndScoresZero() {
        assertEquals(0f, Feed().frame(Spot(Rgb.RedLaser, 80, 30)).score)
    }

    @Test
    fun heldSmallLaserSpotKeepsScoring() {
        val f = Feed()
        f.repeat(20) { frame() }
        val held = f.repeat(60) { frame(lamp) } // ~2 s on a small spot
        assertTrue(held.score > 0.5f, "small held laser must keep scoring: $held")
        assertTrue(held.peak > 0.9f, "lamp is still visible statically: $held")
    }

    @Test
    fun fullFrameRedScenerySettlesQuiet() {
        val f = Feed()
        f.repeat(20) { frame() }
        // Whole frame turning red (camera panned onto red scenery) is motion-attenuated...
        val pan = f.uniform(Rgb.RedLaser)
        assertTrue(pan.coverage > 0.9f && pan.score < 0.35f, "frame-wide change: $pan")
        // ...and then settles into the background (too large for sustained spot).
        assertTrue(f.repeat(90) { uniform(Rgb.RedLaser) }.score < 0.05f, "steady red frame")
    }

    @Test
    fun suddenRedFlashAnywhereScoresHigh() {
        for ((cx, cy) in listOf(80 to 30, 6 to 5, 150 to 54)) {
            val f = Feed()
            f.repeat(30) { frame(lamp) }
            val r = f.frame(lamp, Spot(Rgb.RedLaser, cx, cy, radius = 2))
            assertTrue(r.score > 0.9f, "flash at ($cx,$cy): $r")
        }
    }

    @Test
    fun tinyFarSpotFlashScores() {
        val f = Feed()
        f.repeat(20) { frame() }
        val r = f.frame(Spot(Rgb.RedLaser, 101, 41, radius = 0))
        assertTrue(r.score > 0.9f, "1px flash: $r")
    }

    @Test
    fun orangeFlashDoesNotScoreInRedMode() {
        val f = Feed()
        f.repeat(20) { frame() }
        for (c in listOf(Rgb.Orange, Rgb.Amber, Rgb.Warm, Rgb.White)) {
            val r = f.frame(Spot(c, 80, 30, radius = 4))
            assertEquals(0f, r.score, "flash colour $c: $r")
            f.repeat(10) { frame() }
        }
    }

    @Test
    fun greenModeAcceptsGreenRejectsYellowAndCyan() {
        val f = Feed(BeamHue.Green)
        f.repeat(20) { frame() }
        for (c in listOf(Rgb.Yellow, Rgb.YellowGreen, Rgb.Cyan, Rgb.RedLaser)) {
            assertEquals(0f, f.frame(Spot(c, 80, 30)).score, "flash colour $c")
            f.repeat(10) { frame() }
        }
        assertTrue(f.frame(Spot(Rgb.GreenLaser, 80, 30)).score > 0.9f)
        f.repeat(10) { frame() }
        assertTrue(f.frame(Spot(Rgb.PhoneGreenLaser, 80, 30)).score > 0.65f, "phone teal-green laser")
    }

    @Test
    fun heldSpotKeepsScoreThenRearmsAfterDisappearing() {
        val f = Feed()
        f.repeat(20) { frame() }
        val spot = Spot(Rgb.RedLaser, 60, 20)
        val held = f.repeat(60) { frame(spot) } // ~2 s on screen
        assertTrue(held.score > 0.5f, "held small spot stays loud: $held")
        f.repeat(15) { frame() } // gone for ~0.5 s — background falls
        assertTrue(f.frame(spot).score > 0.9f, "re-appearance (sweep past again) scores")
    }

    @Test
    fun hueSwitchReseeds() {
        val f = Feed()
        f.repeat(10) { frame(Spot(Rgb.RedLaser, 80, 30)) }
        val g = f.detector.analyze(SyntheticFrames.spots(Spot(Rgb.GreenLaser, 80, 30), timestampMs = f.now), Roi.Full, BeamHue.Green)
        assertEquals(0f, g.score)
    }

    @Test
    fun resetReseeds() {
        val f = Feed()
        f.repeat(10) { frame() }
        f.detector.reset()
        assertEquals(0f, f.frame(Spot(Rgb.RedLaser, 80, 30)).score)
    }

    @Test
    fun longGapReseeds() {
        val f = Feed()
        f.repeat(10) { frame() }
        f.now += 5_000
        assertEquals(0f, f.frame(Spot(Rgb.RedLaser, 80, 30)).score)
    }
}
