package io.github.refael302.beamfinder.audio

/** Placeholder for the Phase 3 audio/vibration output driven by BeepStateMachine. */
interface BeepPlayer {
    fun beep()
    fun startContinuous()
    fun stop()
}
