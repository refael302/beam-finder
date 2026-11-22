package io.github.refael302.beamfinder.detection.sensitivity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SensitivityModelTest {
    @Test
    fun higherSensitivityLowersThresholds() {
        val low = SensitivityModel.thresholds(0)
        val high = SensitivityModel.thresholds(100)
        assertTrue(high.startThreshold < low.startThreshold)
        assertTrue(high.fullThreshold < low.fullThreshold)
    }

    @Test
    fun fullAlwaysAboveStartAndMonotonic() {
        var prevStart = Float.MAX_VALUE
        for (s in 0..100) {
            val t = SensitivityModel.thresholds(s)
            assertTrue(t.fullThreshold > t.startThreshold, "s=$s")
            assertTrue(t.startThreshold <= prevStart, "s=$s")
            assertTrue(t.startThreshold in 0f..1f && t.fullThreshold in 0f..1f)
            prevStart = t.startThreshold
        }
    }

    @Test
    fun outOfRangeInputIsClamped() {
        assertEquals(SensitivityModel.thresholds(0), SensitivityModel.thresholds(-50))
        assertEquals(SensitivityModel.thresholds(100), SensitivityModel.thresholds(500))
    }

    @Test
    fun sensitivityForFullThresholdInvertsThresholds() {
        for (s in listOf(0, 25, 50, 75, 100)) {
            val full = SensitivityModel.thresholds(s).fullThreshold
            assertEquals(s, SensitivityModel.sensitivityForFullThreshold(full), "s=$s full=$full")
        }
    }

    @Test
    fun strongerPeakMapsToLowerSensitivity() {
        val weak = SensitivityModel.sensitivityForFullThreshold(0.40f)
        val strong = SensitivityModel.sensitivityForFullThreshold(0.90f)
        assertTrue(strong < weak)
    }
}
