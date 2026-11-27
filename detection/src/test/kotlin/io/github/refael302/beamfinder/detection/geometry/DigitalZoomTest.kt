package io.github.refael302.beamfinder.detection.geometry

import kotlin.test.Test
import kotlin.test.assertEquals

class DigitalZoomTest {

    @Test
    fun scaleFromLinear_endsAtOneAndMax() {
        assertEquals(1f, DigitalZoom.scaleFromLinear(0f), absoluteTolerance = 1e-4f)
        assertEquals(DigitalZoom.MAX_SCALE, DigitalZoom.scaleFromLinear(1f), absoluteTolerance = 1e-4f)
        assertEquals(
            1f + 0.5f * (DigitalZoom.MAX_SCALE - 1f),
            DigitalZoom.scaleFromLinear(0.5f),
            absoluteTolerance = 1e-4f,
        )
    }

    @Test
    fun insetCrop_identityAtScaleOne() {
        val crop = PixelRect(10, 20, 400, 300)
        assertEquals(crop, DigitalZoom.insetCrop(crop, 1f))
    }

    @Test
    fun insetCrop_halvesAtScaleTwo() {
        val crop = PixelRect(0, 0, 400, 200)
        val inset = DigitalZoom.insetCrop(crop, 2f)
        assertEquals(PixelRect(100, 50, 200, 100), inset)
    }

    @Test
    fun insetCrop_keepsOddCentering() {
        val crop = PixelRect(0, 0, 101, 51)
        val inset = DigitalZoom.insetCrop(crop, 2f)
        assertEquals(50, inset.width) // 101/2
        assertEquals(25, inset.height) // 51/2
        assertEquals(25, inset.left) // (101-50)/2
        assertEquals(13, inset.top) // (51-25)/2
    }
}
