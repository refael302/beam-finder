package io.github.refael302.beamfinder.detection.frame

import io.github.refael302.beamfinder.detection.geometry.CoordinateMapper
import io.github.refael302.beamfinder.detection.geometry.GuideBand
import java.nio.ByteBuffer

/**
 * Raw YUV_420_888 planes (mirrors android.media.Image planes without depending on Android).
 * Chroma is 2x2 subsampled; strides are in bytes.
 */
class YuvPlanes(
    val width: Int,
    val height: Int,
    val y: ByteBuffer,
    val yRowStride: Int,
    val yPixelStride: Int,
    val u: ByteBuffer,
    val v: ByteBuffer,
    val uvRowStride: Int,
    val uvPixelStride: Int,
)

/**
 * Extracts the guide band from a raw (possibly rotated) YUV buffer into a compact, upright
 * [LumaChromaFrame] with full-resolution chroma. Output buffers are reused between frames, so
 * the returned frame is only valid until the next call. Not thread-safe; use one per analyzer.
 */
class BandExtractor(
    /** Upper bound for the output width; wider inputs are point-sampled down. */
    private val maxOutputWidth: Int = 480,
) {
    private var lumaBuf = ByteArray(0)
    private var uBuf = ByteArray(0)
    private var vBuf = ByteArray(0)

    fun extract(planes: YuvPlanes, mapper: CoordinateMapper, band: GuideBand, timestampNanos: Long): LumaChromaFrame {
        val step = ((mapper.uprightWidth + maxOutputWidth - 1) / maxOutputWidth).coerceAtLeast(1)
        val r0 = mapper.uprightRow(band.top)
        val r1 = mapper.uprightRow(band.bottom).coerceAtLeast(r0)
        val outW = ((mapper.uprightWidth + step - 1) / step).coerceAtLeast(1)
        val outH = ((r1 - r0) / step + 1).coerceAtLeast(1)
        val n = outW * outH
        if (lumaBuf.size != n) {
            lumaBuf = ByteArray(n)
            uBuf = ByteArray(n)
            vBuf = ByteArray(n)
        }

        val maxBx = planes.width - 1
        val maxBy = planes.height - 1
        var i = 0
        for (oy in 0 until outH) {
            val yu = (r0 + oy * step).coerceAtMost(mapper.uprightHeight - 1)
            for (ox in 0 until outW) {
                val xu = (ox * step).coerceAtMost(mapper.uprightWidth - 1)
                val bx = mapper.bufferX(xu, yu).coerceIn(0, maxBx)
                val by = mapper.bufferY(xu, yu).coerceIn(0, maxBy)
                lumaBuf[i] = planes.y.get(by * planes.yRowStride + bx * planes.yPixelStride)
                val ci = (by shr 1) * planes.uvRowStride + (bx shr 1) * planes.uvPixelStride
                uBuf[i] = planes.u.get(ci)
                vBuf[i] = planes.v.get(ci)
                i++
            }
        }
        return LumaChromaFrame(outW, outH, lumaBuf, uBuf, vBuf, timestampNanos, chromaStep = 1)
    }
}
