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
 * Extracts the full frame ([extractFull], used by detection) or a horizontal band from a raw
 * (possibly rotated) YUV buffer into a compact, upright
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

    /** The whole upright frame (detection scans the full field of view, not just the guide band). */
    fun extractFull(planes: YuvPlanes, mapper: CoordinateMapper, timestampNanos: Long): LumaChromaFrame =
        extract(planes, mapper, FULL_FRAME, timestampNanos)

    /**
     * Dense crop around [centerX]/[centerY] (normalized upright 0..1) for secondary refine.
     * Samples the source region at high density into [maxOutputWidth] so a tiny laser occupies
     * more analysis pixels — like digital zoom, without changing the preview FOV.
     */
    fun extractCrop(
        planes: YuvPlanes,
        mapper: CoordinateMapper,
        centerX: Float,
        centerY: Float,
        timestampNanos: Long,
        fraction: Float = 0.25f,
        maxOutputWidth: Int = 256,
    ): LumaChromaFrame {
        val side = fraction.coerceIn(0.08f, 0.6f)
        val cx = centerX.coerceIn(0f, 1f)
        val cy = centerY.coerceIn(0f, 1f)
        val half = side / 2f
        val left = (cx - half).coerceIn(0f, 1f - side)
        val top = (cy - half).coerceIn(0f, 1f - side)
        val right = left + side
        val bottom = top + side
        return extractRegion(planes, mapper, left, top, right, bottom, timestampNanos, maxOutputWidth)
    }

    fun extract(planes: YuvPlanes, mapper: CoordinateMapper, band: GuideBand, timestampNanos: Long): LumaChromaFrame =
        extractRegion(planes, mapper, 0f, band.top, 1f, band.bottom, timestampNanos, maxOutputWidth)

    private fun extractRegion(
        planes: YuvPlanes,
        mapper: CoordinateMapper,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        timestampNanos: Long,
        outMaxWidth: Int,
    ): LumaChromaFrame {
        val x0 = (left.coerceIn(0f, 1f) * (mapper.uprightWidth - 1)).toInt()
        val x1 = (right.coerceIn(0f, 1f) * (mapper.uprightWidth - 1)).toInt().coerceAtLeast(x0)
        val y0 = mapper.uprightRow(top.coerceIn(0f, 1f))
        val y1 = mapper.uprightRow(bottom.coerceIn(0f, 1f)).coerceAtLeast(y0)
        val srcW = (x1 - x0 + 1).coerceAtLeast(1)
        val srcH = (y1 - y0 + 1).coerceAtLeast(1)
        val step = (srcW + outMaxWidth - 1) / outMaxWidth.coerceAtLeast(1)
        val outW = ((srcW + step - 1) / step).coerceAtLeast(1)
        val outH = ((srcH + step - 1) / step).coerceAtLeast(1)
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
            val yu = (y0 + oy * step).coerceAtMost(mapper.uprightHeight - 1)
            for (ox in 0 until outW) {
                val xu = (x0 + ox * step).coerceAtMost(mapper.uprightWidth - 1)
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

    private companion object {
        val FULL_FRAME = GuideBand(top = 0f, bottom = 1f)
    }
}
