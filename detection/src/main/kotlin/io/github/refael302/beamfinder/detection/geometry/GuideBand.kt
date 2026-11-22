package io.github.refael302.beamfinder.detection.geometry

/**
 * A full-width horizontal strip (normalized to the upright frame height, 0..1).
 *
 * Live detection scans the full frame; the on-screen guide line is visual only.
 * This type remains for geometry utilities and tests.
 */
data class GuideBand(val top: Float, val bottom: Float) {
    val height: Float get() = bottom - top
    val center: Float get() = (top + bottom) / 2f
}
