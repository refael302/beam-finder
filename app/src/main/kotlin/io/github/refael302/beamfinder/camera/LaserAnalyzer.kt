package io.github.refael302.beamfinder.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import io.github.refael302.beamfinder.detection.frame.BandExtractor
import io.github.refael302.beamfinder.detection.frame.YuvPlanes
import io.github.refael302.beamfinder.detection.geometry.CoordinateMapper
import io.github.refael302.beamfinder.detection.geometry.PixelRect
import io.github.refael302.beamfinder.detection.pipeline.DetectionResult
import io.github.refael302.beamfinder.detection.pipeline.IntensityPipeline
import io.github.refael302.beamfinder.detection.scoring.BeamHue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * CameraX analyzer: YUV_420_888 frame -> upright FULL frame (downsampled) -> [IntensityPipeline]
 * -> [results]. The guide line is visual only and does not restrict detection.
 * Parameters are read per frame and may be changed from any thread.
 */
class LaserAnalyzer(
    private val pipeline: IntensityPipeline,
    /** Invoked on the analysis thread for every result (low-latency path for audio). */
    private val onResult: (DetectionResult) -> Unit = {},
) : ImageAnalysis.Analyzer {

    @Volatile var hue: BeamHue = BeamHue.Red
    /** Front camera preview is mirrored; only affects horizontal positions. */
    @Volatile var mirrored: Boolean = false

    private val extractor = BandExtractor(maxOutputWidth = MAX_ANALYSIS_WIDTH)
    private val _results = MutableStateFlow(DetectionResult.Empty)
    val results: StateFlow<DetectionResult> = _results.asStateFlow()

    override fun analyze(image: ImageProxy) {
        image.use { img ->
            val crop = img.cropRect
            if (crop.width() <= 0 || crop.height() <= 0) return
            val mapper = CoordinateMapper(
                crop = PixelRect(crop.left, crop.top, crop.width(), crop.height()),
                rotationDegrees = img.imageInfo.rotationDegrees,
                mirrored = mirrored,
            )
            val p = img.planes
            val planes = YuvPlanes(
                width = img.width,
                height = img.height,
                y = p[0].buffer,
                yRowStride = p[0].rowStride,
                yPixelStride = p[0].pixelStride,
                u = p[1].buffer,
                v = p[2].buffer,
                uvRowStride = p[1].rowStride,
                uvPixelStride = p[1].pixelStride,
            )
            val frame = extractor.extractFull(planes, mapper, img.imageInfo.timestamp)
            val result = pipeline.process(frame, hue)
            _results.value = result
            onResult(result)
        }
    }

    /** Clears the published result (e.g. when the camera stops). */
    fun clear() {
        _results.value = DetectionResult.Empty
    }

    private companion object {
        /**
         * Upright analysis width cap. The 640x480 analysis stream is 480 wide when upright, so
         * this keeps full resolution (small far spots survive); larger streams are point-sampled.
         */
        const val MAX_ANALYSIS_WIDTH = 480
    }
}
