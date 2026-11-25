package io.github.refael302.beamfinder.ui.detect

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.refael302.beamfinder.audio.BeepEngine
import io.github.refael302.beamfinder.camera.CameraTuning
import io.github.refael302.beamfinder.camera.LaserAnalyzer
import io.github.refael302.beamfinder.detection.pipeline.DetectionResult
import io.github.refael302.beamfinder.detection.pipeline.IntensityPipeline
import io.github.refael302.beamfinder.detection.scoring.BeamHue
import io.github.refael302.beamfinder.detection.sensitivity.AutoSensitivityTuner
import io.github.refael302.beamfinder.detection.sensitivity.SensitivityModel
import io.github.refael302.beamfinder.settings.AppSettings
import io.github.refael302.beamfinder.settings.BeamColor
import io.github.refael302.beamfinder.settings.CameraFacing
import io.github.refael302.beamfinder.settings.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

/** Which bottom control panel is expanded on Detect (one at a time). */
enum class DetectControlPanel {
    Sensitivity,
    Locks,
    Actions,
    Advanced,
}

/** Session-only Detect UI state (not persisted). */
data class DetectSessionState(
    val linearZoom: Float = 0f,
    val aeAwbLock: Boolean = false,
    val focusLock: Boolean = false,
    val pocketActive: Boolean = false,
    val showDebug: Boolean = false,
    /** Expanded bottom control topic; null when all panels are collapsed. */
    val openPanel: DetectControlPanel? = null,
    /** When true, sensitivity tracks the strongest dazzle in the current auto session. */
    val sensitivityAuto: Boolean = false,
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

    private val autoSensitivity = AutoSensitivityTuner()

    /** Thresholds for the level meter; read from the detection module (SSOT). */
    val thresholds: StateFlow<SensitivityModel.Thresholds> = settings
        .map { SensitivityModel.thresholds(it.sensitivity) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SensitivityModel.thresholds(AppSettings.Default.sensitivity))

    private var active = false
    /** Cancels the pending auto-lock after the last guide interaction. */
    private var guideAutoLockJob: Job? = null

    init {
        viewModelScope.launch {
            settings.collect { s ->
                // Guide line is visual only; detection scans the full frame.
                analyzer.hue = s.beamColor.toHue()
                pipeline.thresholds = SensitivityModel.thresholds(s.sensitivity)
                beepEngine.configure(s)
                if (s.guideLocked) {
                    guideAutoLockJob?.cancel()
                    guideAutoLockJob = null
                }
            }
        }
        // A different colour or camera invalidates the ambient baseline.
        viewModelScope.launch {
            settings.map { it.beamColor to it.cameraFacing }
                .distinctUntilChanged()
                .collect { pipeline.recalibrate() }
        }
        // Feed auto-sensitivity when that mode is on.
        viewModelScope.launch {
            detection.collect { r ->
                if (_session.value.sensitivityAuto && !r.calibrating) {
                    val next = autoSensitivity.onSample(r.intensity, SystemClock.elapsedRealtime())
                    if (next != null && next != settings.value.sensitivity) {
                        repository.update { it.copy(sensitivity = next) }
                    }
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
        pipeline.recalibrate()
        beepEngine.start(viewModelScope)
        if (!settings.value.guideLocked) scheduleGuideAutoLock()
    }

    fun onInactive() {
        if (!active) return
        active = false
        guideAutoLockJob?.cancel()
        guideAutoLockJob = null
        autoSensitivity.reset()
        beepEngine.stop()
        analyzer.clear()
    }

    fun recalibrate() = pipeline.recalibrate()

    fun setLinearZoom(value: Float) = _session.update { it.copy(linearZoom = value.coerceIn(0f, 1f)) }

    /** Pinch: multiplicative [scale] -> additive change on the 0..1 linear zoom. */
    fun zoomBy(scale: Float) {
        if (scale <= 0f) return
        // Clamp per-event scale so a bogus resume/pointer spike can't slam the slider to 0 or 1.
        val clamped = scale.coerceIn(PINCH_SCALE_MIN, PINCH_SCALE_MAX)
        _session.update { it.copy(linearZoom = (it.linearZoom + ln(clamped) * PINCH_SENSITIVITY).coerceIn(0f, 1f)) }
    }

    fun toggleAeAwbLock() = _session.update { it.copy(aeAwbLock = !it.aeAwbLock) }

    fun toggleFocusLock() = _session.update { it.copy(focusLock = !it.focusLock) }

    fun toggleDebug() = _session.update { it.copy(showDebug = !it.showDebug) }

    /** Opens [panel], or closes it if it is already open (only one panel at a time). */
    fun toggleControlPanel(panel: DetectControlPanel) = _session.update {
        it.copy(openPanel = if (it.openPanel == panel) null else panel)
    }

    fun toggleSensitivityAuto() {
        _session.update { current ->
            val enabling = !current.sensitivityAuto
            if (enabling) autoSensitivity.reset()
            current.copy(sensitivityAuto = enabling)
        }
    }

    /** Manual slider: persist sensitivity and leave auto mode. */
    fun setSensitivityManual(value: Int) {
        _session.update { it.copy(sensitivityAuto = false) }
        viewModelScope.launch {
            repository.update { it.copy(sensitivity = value.coerceIn(AppSettings.SENSITIVITY_RANGE)) }
        }
    }

    fun setPocket(active: Boolean) = _session.update { it.copy(pocketActive = active) }

    fun onGuideDrag(y: Float) {
        if (settings.value.guideLocked) return
        guideAutoLockJob?.cancel()
        guideAutoLockJob = null
        val v = y.coerceIn(0f, 1f)
        _session.update { it.copy(draggingGuideY = v) }
    }

    fun onGuideDragEnd() {
        val y = _session.value.draggingGuideY ?: return
        viewModelScope.launch {
            repository.update { it.copy(guideLineY = y) }
            _session.update { it.copy(draggingGuideY = null) }
            scheduleGuideAutoLock()
        }
    }

    fun toggleGuideLock() {
        viewModelScope.launch {
            val locking = !settings.value.guideLocked
            if (locking) {
                guideAutoLockJob?.cancel()
                guideAutoLockJob = null
                // Persist any in-progress drag so locking mid-gesture does not drop the position.
                val y = _session.value.draggingGuideY
                if (y != null) {
                    repository.update { it.copy(guideLineY = y, guideLocked = true) }
                    _session.update { it.copy(draggingGuideY = null) }
                } else {
                    repository.update { it.copy(guideLocked = true) }
                }
            } else {
                repository.update { it.copy(guideLocked = false) }
                scheduleGuideAutoLock()
            }
        }
    }

    /** After [GUIDE_AUTO_LOCK_MS] without dragging, freeze the line so it can't shift by accident. */
    private fun scheduleGuideAutoLock() {
        if (!active) return
        guideAutoLockJob?.cancel()
        guideAutoLockJob = viewModelScope.launch {
            delay(GUIDE_AUTO_LOCK_MS)
            if (active && !settings.value.guideLocked && _session.value.draggingGuideY == null) {
                repository.update { it.copy(guideLocked = true) }
            }
        }
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
        const val PINCH_SCALE_MIN = 0.5f
        const val PINCH_SCALE_MAX = 2f
        /** Idle time after the last guide drag / unlock before the line locks itself. */
        const val GUIDE_AUTO_LOCK_MS = 10_000L
    }
}

fun BeamColor.toHue(): BeamHue = when (this) {
    BeamColor.Red -> BeamHue.Red
    BeamColor.Green -> BeamHue.Green
}
