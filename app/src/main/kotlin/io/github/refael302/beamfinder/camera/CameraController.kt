package io.github.refael302.beamfinder.camera

import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import io.github.refael302.beamfinder.settings.CameraFacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Camera parameters controlled from the Detect screen. */
data class CameraTuning(
    /** 0..1 digital zoom (session only). */
    val linearZoom: Float = 0f,
    /** Exposure compensation toward the minimum (from settings). */
    val lowExposure: Boolean = true,
    /** Lock auto-exposure and auto-white-balance. */
    val aeAwbLock: Boolean = false,
    /** Lock focus after a single AF scan. */
    val focusLock: Boolean = false,
)

/**
 * CameraX wrapper: Preview + ImageAnalysis (YUV_420_888, ~640x480, keep-only-latest) bound with
 * the PreviewView's ViewPort so the analysis crop rect equals the visible preview area.
 * Main-thread only.
 */
class CameraController(private val context: Context) {
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "beam-analysis")
    }
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var tuning = CameraTuning()
    private var appliedFocusLock: Boolean? = null

    /** The facing actually bound (may differ from the request if that camera is missing). */
    var boundFacing: CameraFacing? = null
        private set

    /**
     * Binds preview + analysis to [owner]. Suspends until the PreviewView is laid out.
     * @throws NoCameraException if the device has no usable camera.
     */
    suspend fun bind(
        owner: LifecycleOwner,
        previewView: PreviewView,
        facing: CameraFacing,
        analyzer: ImageAnalysis.Analyzer,
    ) {
        val provider = provider ?: awaitProvider().also { provider = it }
        val viewPort = awaitViewPort(previewView)

        val selector = pickSelector(provider, facing)
        val resolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(ANALYSIS_SIZE, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
            )
            .build()
        val preview = Preview.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build(),
            )
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolution)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also { it.setAnalyzer(analysisExecutor, analyzer) }

        val group = UseCaseGroup.Builder()
            .setViewPort(viewPort)
            .addUseCase(preview)
            .addUseCase(analysis)
            .build()

        provider.unbindAll()
        appliedFocusLock = null
        camera = provider.bindToLifecycle(owner, selector, group)
        boundFacing = if (selector == CameraSelector.DEFAULT_FRONT_CAMERA) CameraFacing.Front else CameraFacing.Back
        applyAll()
    }

    fun unbind() {
        provider?.unbindAll()
        camera = null
        boundFacing = null
        appliedFocusLock = null
    }

    fun release() {
        unbind()
        analysisExecutor.shutdown()
    }

    /** Stores [newTuning] and applies whatever changed to the bound camera (if any). */
    fun setTuning(newTuning: CameraTuning) {
        val old = tuning
        tuning = newTuning
        val cam = camera ?: return
        if (old.linearZoom != newTuning.linearZoom) cam.cameraControl.setLinearZoom(newTuning.linearZoom)
        if (old.lowExposure != newTuning.lowExposure || old.aeAwbLock != newTuning.aeAwbLock) applyExposure(cam)
        if (appliedFocusLock != newTuning.focusLock) applyFocus(cam)
    }

    private fun applyAll() {
        val cam = camera ?: return
        cam.cameraControl.setLinearZoom(tuning.linearZoom)
        applyExposure(cam)
        applyFocus(cam)
    }

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun applyExposure(cam: Camera) {
        val exposure = cam.cameraInfo.exposureState
        if (exposure.isExposureCompensationSupported) {
            val range = exposure.exposureCompensationRange
            val index = if (tuning.lowExposure) {
                (range.lower * LOW_EXPOSURE_FRACTION).toInt().coerceIn(range.lower, range.upper)
            } else {
                0.coerceIn(range.lower, range.upper)
            }
            cam.cameraControl.setExposureCompensationIndex(index)
        }
        // AE/AWB locks via Camera2 interop (CameraX has no first-class API for them).
        Camera2CameraControl.from(cam.cameraControl).setCaptureRequestOptions(
            CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, tuning.aeAwbLock)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, tuning.aeAwbLock)
                .build(),
        )
    }

    private fun applyFocus(cam: Camera) {
        appliedFocusLock = tuning.focusLock
        if (tuning.focusLock) {
            // Single AF scan at the centre, then hold (no auto-cancel) = focus lock.
            val point = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f)
            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                .disableAutoCancel()
                .build()
            if (cam.cameraInfo.isFocusMeteringSupported(action)) {
                cam.cameraControl.startFocusAndMetering(action)
            } else {
                Log.i(TAG, "Focus metering not supported on this camera (fixed focus?)")
            }
        } else {
            cam.cameraControl.cancelFocusAndMetering()
        }
    }

    private fun pickSelector(provider: ProcessCameraProvider, facing: CameraFacing): CameraSelector {
        val preferred = if (facing == CameraFacing.Front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        val fallback = if (facing == CameraFacing.Front) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
        return when {
            hasCamera(provider, preferred) -> preferred
            hasCamera(provider, fallback) -> fallback
            else -> throw NoCameraException()
        }
    }

    private fun hasCamera(provider: ProcessCameraProvider, selector: CameraSelector): Boolean =
        try {
            provider.hasCamera(selector)
        } catch (e: Exception) {
            false
        }

    private suspend fun awaitProvider(): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                try {
                    cont.resume(future.get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    /** PreviewView.viewPort is null until the view is attached and laid out. */
    private suspend fun awaitViewPort(previewView: PreviewView): ViewPort {
        while (true) {
            previewView.viewPort?.let { return it }
            delay(16)
        }
    }

    class NoCameraException : Exception("No camera available")

    private companion object {
        const val TAG = "CameraController"
        val ANALYSIS_SIZE = Size(640, 480)
        /** 1.0 = full way to the minimum exposure compensation. */
        const val LOW_EXPOSURE_FRACTION = 1.0f
    }
}
