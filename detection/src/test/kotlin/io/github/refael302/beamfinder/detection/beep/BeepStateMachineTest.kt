package io.github.refael302.beamfinder.detection.beep

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BeepStateMachineTest {
    private class FakeClock(var now: Long = 0L) : Clock {
        override fun nowMs(): Long = now
    }

    private val config = BeepConfig(
        startThreshold = 0.2f,
        fullThreshold = 0.8f,
        hysteresis = 0.05f,
        minIntervalMs = 100L,
        maxIntervalMs = 500L,
        releaseHoldMs = 200L,
    )

    @Test
    fun staysIdleBelowStart() {
        val sm = BeepStateMachine(config, FakeClock())
        val out = sm.update(0.1f)
        assertEquals(BeepState.Idle, out.state)
        assertFalse(out.beepNow)
    }

    @Test
    fun beepsImmediatelyThenRespectsInterval() {
        val clock = FakeClock()
        val sm = BeepStateMachine(config, clock)
        assertTrue(sm.update(0.2f).beepNow)
        clock.now = 499
        assertFalse(sm.update(0.2f).beepNow)
        clock.now = 500
        assertTrue(sm.update(0.2f).beepNow)
    }

    @Test
    fun continuousAtFullThreshold() {
        val sm = BeepStateMachine(config, FakeClock())
        val out = sm.update(0.9f)
        assertTrue(out.continuous)
        assertFalse(out.beepNow)
        assertEquals(1f, out.level)
    }

    @Test
    fun intervalShrinksAsIntensityRises() {
        val sm = BeepStateMachine(config, FakeClock())
        val weak = sm.update(0.25f).intervalMs
        val strong = sm.update(0.7f).intervalMs
        assertTrue(strong < weak)
    }

    @Test
    fun releasesToIdleAfterHold() {
        val clock = FakeClock()
        val sm = BeepStateMachine(config, clock)
        sm.update(0.5f)
        clock.now = 1000
        assertEquals(BeepState.Beeping, sm.update(0f).state)
        clock.now = 1199
        assertEquals(BeepState.Beeping, sm.update(0f).state)
        clock.now = 1200
        assertEquals(BeepState.Idle, sm.update(0f).state)
    }

    @Test
    fun brieflyDippingWithinHysteresisDoesNotRelease() {
        val clock = FakeClock()
        val sm = BeepStateMachine(config, clock)
        sm.update(0.5f)
        clock.now = 5000
        assertEquals(BeepState.Beeping, sm.update(0.17f).state) // within hysteresis band
    }
}
