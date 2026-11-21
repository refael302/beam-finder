package io.github.refael302.beamfinder.detection.geometry

import io.github.refael302.beamfinder.detection.frame.BandExtractor
import io.github.refael302.beamfinder.detection.frame.YuvPlanes
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoordinateMapperTest {
    private val full = PixelRect(0, 0, 640, 480)

    @Test
    fun rotation0IsIdentity() {
        val m = CoordinateMapper(full, 0)
        assertEquals(640, m.uprightWidth)
        assertEquals(480, m.uprightHeight)
        assertEquals(10, m.bufferX(10, 20))
        assertEquals(20, m.bufferY(10, 20))
    }

    @Test
    fun rotation90TurnsGuideRowIntoBufferColumn() {
        // Back camera in portrait: buffer is landscape, upright is portrait.
        val m = CoordinateMapper(full, 90)
        assertEquals(480, m.uprightWidth)
        assertEquals(640, m.uprightHeight)
        val row = m.uprightRow(0.25f) // 160
        // Every point along the upright row maps to the same buffer column.
        val cols = (0 until m.uprightWidth step 50).map { m.bufferX(it, row) }.toSet()
        assertEquals(setOf(160), cols)
        // Upright left edge -> buffer bottom, right edge -> buffer top.
        assertEquals(479, m.bufferY(0, row))
        assertEquals(0, m.bufferY(479, row))
    }

    @Test
    fun rotation270MirrorsTheColumn() {
        val m = CoordinateMapper(full, 270)
        val row = m.uprightRow(0.25f)
        assertEquals(640 - 1 - 160, m.bufferX(0, row))
        assertEquals(0, m.bufferY(0, row))
    }

    @Test
    fun rotation180FlipsBoth() {
        val m = CoordinateMapper(full, 180)
        assertEquals(639, m.bufferX(0, 0))
        assertEquals(479, m.bufferY(0, 0))
    }

    @Test
    fun cropRectOffsetsAreApplied() {
        // Viewport crop (e.g. 16:9 preview cropping a 4:3 buffer).
        val m = CoordinateMapper(PixelRect(40, 0, 560, 480), 90)
        assertEquals(480, m.uprightWidth)
        assertEquals(560, m.uprightHeight)
        assertEquals(40, m.bufferX(0, 0))
        assertEquals(40 + 559, m.bufferX(0, 559))
    }

    @Test
    fun normalizedGuideMapsToBufferForRotation90() {
        val m = CoordinateMapper(full, 90)
        val (bx, _) = m.normalizedToBuffer(0.5f, 0.5f, 640, 480)
        assertTrue(bx in 0.49f..0.51f, "bx=$bx")
    }

    @Test
    fun bandToBufferRectIsVerticalStripForRotation90() {
        val m = CoordinateMapper(full, 90)
        val rect = m.bandToBufferRect(GuideBand(0.4f, 0.6f))
        assertEquals(0, rect.top)
        assertEquals(480, rect.height)
        assertEquals(256, rect.left)
        assertTrue(rect.width in 128..130, "width=${rect.width}")
    }

    @Test
    fun mirroringDoesNotMoveTheGuideRow() {
        val a = CoordinateMapper(full, 270, mirrored = false)
        val b = CoordinateMapper(full, 270, mirrored = true)
        val row = a.uprightRow(0.3f)
        assertEquals(a.bufferX(0, row), b.bufferX(0, row))
    }

    @Test
    fun extractorProducesUprightBandFromRotatedBuffer() {
        val w = 64
        val h = 48
        // Buffer: a bright vertical column at x = 16 (=> upright row 16 of 64 for rotation 90).
        val y = ByteArray(w * h) { i -> if (i % w == 16) 200.toByte() else 20.toByte() }
        // Interleaved chroma (pixelStride 2), neutral except the column which is strongly red.
        val uvRowStride = w
        val u = ByteArray(uvRowStride * h / 2) { 128.toByte() }
        val v = ByteArray(uvRowStride * h / 2) { i -> if ((i % uvRowStride) / 2 == 8) 230.toByte() else 128.toByte() }
        val planes = YuvPlanes(
            width = w, height = h,
            y = ByteBuffer.wrap(y), yRowStride = w, yPixelStride = 1,
            u = ByteBuffer.wrap(u), v = ByteBuffer.wrap(v), uvRowStride = uvRowStride, uvPixelStride = 2,
        )
        val mapper = CoordinateMapper(PixelRect(0, 0, w, h), 90)
        val band = GuideBand(top = 10f / 64f, bottom = 22f / 64f)
        val frame = BandExtractor().extract(planes, mapper, band, timestampNanos = 5L)

        assertEquals(48, frame.width) // upright width = buffer height
        assertEquals(5L, frame.timestampNanos)
        val brightRow = (0 until frame.height).single { frame.lumaAt(frame.width / 2, it) == 200 }
        assertEquals(16 - mapper.uprightRow(band.top), brightRow)
        // Entire upright row is bright: the buffer column became a horizontal line.
        assertTrue((0 until frame.width).all { frame.lumaAt(it, brightRow) == 200 })
        assertEquals(230, frame.vAt(0, brightRow))
    }
}
