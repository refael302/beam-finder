package io.github.refael302.beamfinder.detection.sensitivity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutoSensitivityTunerTest {
    @Test
    fun firstDazzleEmitsSensitivityFromPeak() {
        val tuner = AutoSensitivityTuner()
        val peak = 0.65f
        val expected = SensitivityModel.sensitivityForFullThreshold(peak)
        assertEquals(expected, tuner.onSample(peak, nowMs = 1_000L))
        assertEquals(peak, tuner.peak)
    }

    @Test
    fun strongerPeakInSameSessionRetunes() {
        val tuner = AutoSensitivityTuner()
        val first = tuner.onSample(0.50f, 1_000L)
        assertEquals(SensitivityModel.sensitivityForFullThreshold(0.50f), first)

        assertNull(tuner.onSample(0.45f, 2_000L), "weaker sample must not retune")
        assertEquals(0.50f, tuner.peak)

        val stronger = tuner.onSample(0.80f, 3_000L)
        assertEquals(SensitivityModel.sensitivityForFullThreshold(0.80f), stronger)
        assertEquals(0.80f, tuner.peak)
        assertTrue(stronger!! < first!!, "stronger peak -> lower sensitivity")
    }

    @Test
    fun gapOver30SecondsStartsNewSession() {
        val tuner = AutoSensitivityTuner(sessionGapMs = 30_000L)
        val t0 = 1_000L
        tuner.onSample(0.90f, t0)
        assertEquals(0.90f, tuner.peak)

        // Weaker activity still counts as the same session (extends last-activity time).
        assertNull(tuner.onSample(0.40f, t0 + 5_000L))
        assertEquals(0.90f, tuner.peak)

        // No samples for >30s after last activity, then a new dazzle → new session.
        val next = tuner.onSample(0.40f, t0 + 5_000L + 30_001L)
        assertEquals(SensitivityModel.sensitivityForFullThreshold(0.40f), next)
        assertEquals(0.40f, tuner.peak)

        // Just under the gap after that: still the new session's peak.
        assertNull(tuner.onSample(0.35f, t0 + 5_000L + 30_001L + 29_999L))
        assertEquals(0.40f, tuner.peak)
    }

    @Test
    fun belowObserveFloorIsIgnored() {
        val tuner = AutoSensitivityTuner()
        assertNull(tuner.onSample(AutoSensitivityTuner.OBSERVE_FLOOR - 0.001f, 1_000L))
        assertEquals(0f, tuner.peak)
    }

    @Test
    fun resetClearsSession() {
        val tuner = AutoSensitivityTuner()
        tuner.onSample(0.70f, 1_000L)
        tuner.reset()
        assertEquals(0f, tuner.peak)
        val again = tuner.onSample(0.70f, 2_000L)
        assertEquals(SensitivityModel.sensitivityForFullThreshold(0.70f), again)
    }

    @Test
    fun sameSensitivityNotReEmitted() {
        val tuner = AutoSensitivityTuner()
        val s = tuner.onSample(0.60f, 1_000L)
        // Tiny increase that rounds to the same sensitivity bucket.
        val tiny = 0.60f + 0.001f
        if (SensitivityModel.sensitivityForFullThreshold(tiny) == s) {
            assertNull(tuner.onSample(tiny, 2_000L))
        }
    }
}
