package io.github.refael302.beamfinder.ui.detect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.refael302.beamfinder.audio.BeepEngine
import io.github.refael302.beamfinder.camera.CameraTuning
import io.github.refael302.beamfinder.camera.LaserAnalyzer
import io.github.refael302.beamfinder.detection.pipeline.DetectionResult
import io.github.refael302.beamfinder.detection.pipeline.IntensityPipeline
import io.github.refael302.beamfinder.detection.scoring.BeamHue
import io.github.refael302.beamfinder.detection.sensitivity.SensitivityModel
import io.github.refael302.beamfinder.settings.AppSettings
import io.github.refael302.beamfinder.settings.BeamColor
import io.github.refael302.beamfinder.settings.CameraFacing
import io.github.refael302.beamfinder.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.ln

/** Session-only Detect UI state (not persisted). */
data class DetectSessionState(
    val linearZoom: Float = 0f,
    val aeAwbLock: Boolean = false,
    val focusLock: Boolean = false,
    val pocketActive: Boolean = false,
    val showDebug: Boolean = false,
    /** Guide Y while the user is dragging (persisted on release); null when idle. */
    val draggingGuideY: Float? = null,
)

class DetectViewModel(
    private val repository: SettingsRepository,
    private val beepEngine: BeepEngine,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings.Default)

    private val _session = MutableStateFlow(DetectSessionState())
    val session: StateFlow<DetectSessionState> = _session.asStateFlow()

    private val pipeline = IntensityPipeline()
    val analyzer = LaserAnalyzer(pipeline) { result -> beepEngine.submit(result.intensity) }
    val detection: StateFlow<DetectionResult> = analyzer.results

    /** Thresholds for the level meter; read from the detection module (SSOT). */
    val thresholds: StateFlow<SensitivityModel.Thresholds> = settings
        .map { SensitivityModel.thresholds(it.sensitivity) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SensitivityModel.thresholds(AppSettings.Default.sensitivity))

    private var active = false
    private var autoPocketPending = false

    init {
        viewModelScope.launch {
            settings.collect { s ->
                // Guide line / roiWidth are visual only; detection scans the full frame.
                analyzer.hue = s.beamColor.toHue()
                pipeline.thresholds = SensitivityModel.thresholds(s.sensitivity)
                beepEngine.configure(s)
            }
        }
        // A different colour or camera invalidates the ambient baseline.
        viewModelScope.launch {
            settings.map { it.beamColor to it.cameraFacing }
                .distinctUntilChanged()
                .collect { pipeline.recalibrate() }
        }
        // Auto-enter pocket mode once the first calibration finishes, if enabled in settings.
        viewModelScope.launch {
            detection.collect { r ->
                if (autoPocketPending && !r.calibrating && r.fps > 0f) {
                    autoPocketPending = false
                    if (settings.value.pocketMode) _session.update { it.copy(pocketActive = true) }
                }
            }
        }
    }

    /** Camera tuning derived from settings + session state. */
    fun cameraTuning(s: AppSettings, session: DetectSessionState) = CameraTuning(
        linearZoom = session.linearZoom,
        lowExposure = s.lowExposure,
        aeAwbLock = session.aeAwbLock,
        focusLock = session.focusLock,
    )

    /** Detect screen visible with camera permission: start calibration and audio. */
    fun onActive() {
        if (active) return
        active = true
        autoPocketPending = true
        pipeline.recalibrate()
        beepEngine.start(viewModelScope)
    }

    fun onInactive() {
        if (!active) return
        active = false
        autoPocketPending = false
        beepEngine.stop()
        analyzer.clear()
    }

    fun recalibrate() = pipeline.recalibrate()

    fun setLinearZoom(value: Float) = _session.update { it.copy(linearZoom = value.coerceIn(0f, 1f)) }

    /** Pinch: multiplicative [scale] -> additive change on the 0..1 linear zoom. */
    fun zoomBy(scale: Float) {
        if (scale <= 0f) return
        _session.update { it.copy(linearZoom = (it.linearZoom + ln(scale) * PINCH_SENSITIVITY).coerceIn(0f, 1f)) }
    }

    fun toggleAeAwbLock() = _session.update { it.copy(aeAwbLock = !it.aeAwbLock) }

    fun toggleFocusLock() = _session.update { it.copy(focusLock = !it.focusLock) }

    fun toggleDebug() = _session.update { it.copy(showDebug = !it.showDebug) }

    fun setPocket(active: Boolean) = _session.update { it.copy(pocketActive = active) }

    fun onGuideDrag(y: Float) {
        if (settings.value.guideLocked) return
        val v = y.coerceIn(0f, 1f)
        _session.update { it.copy(draggingGuideY = v) }
    }

    fun onGuideDragEnd() {
        val y = _session.value.draggingGuideY ?: return
        viewModelScope.launch {
            repository.update { it.copy(guideLineY = y) }
            _session.update { it.copy(draggingGuideY = null) }
        }
    }

    fun toggleGuideLock() {
        viewModelScope.launch { repository.update { it.copy(guideLocked = !it.guideLocked) } }
    }

    /** Quick control: Red <-> Green (persisted; the colour change triggers recalibration). */
    fun toggleBeamColor() {
        viewModelScope.launch {
            repository.update {
                it.copy(beamColor = if (it.beamColor == BeamColor.Red) BeamColor.Green else BeamColor.Red)
            }
        }
    }

    /** Quick control: Back <-> Front camera (persisted; DetectScreen rebinds on change). */
    fun toggleCameraFacing() {
        viewModelScope.launch {
            repository.update {
                it.copy(cameraFacing = if (it.cameraFacing == CameraFacing.Back) CameraFacing.Front else CameraFacing.Back)
            }
        }
    }

    override fun onCleared() {
        beepEngine.stop()
    }

    private companion object {
        /** Linear-zoom change per unit of ln(pinch scale). */
        const val PINCH_SENSITIVITY = 0.8f
    }
}

fun BeamColor.toHue(): BeamHue = when (this) {
    BeamColor.Red -> BeamHue.Red
    BeamColor.Green -> BeamHue.Green
}
