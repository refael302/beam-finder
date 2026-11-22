package io.github.refael302.beamfinder.ui.detect

import androidx.activity.compose.LocalActivity
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.refael302.beamfinder.R
import io.github.refael302.beamfinder.camera.CameraController
import io.github.refael302.beamfinder.detection.pipeline.DetectionResult
import io.github.refael302.beamfinder.detection.sensitivity.SensitivityModel
import io.github.refael302.beamfinder.settings.AppSettings
import io.github.refael302.beamfinder.settings.BeamColor
import io.github.refael302.beamfinder.settings.CameraFacing
import io.github.refael302.beamfinder.ui.AppViewModelProvider
import io.github.refael302.beamfinder.ui.components.BeamTopBar
import kotlinx.coroutines.CancellationException
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DetectScreen(
    onBack: () -> Unit,
    onSettings: () -> Unit,
    viewModel: DetectViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    KeepScreenOn()
    LightStatusBarIcons()

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                BeamTopBar(
                    title = stringResource(R.string.detect_title),
                    onBack = onBack,
                    overlay = true,
                    actions = {
                        IconButton(onClick = onSettings) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.menu_settings),
                            )
                        }
                    },
                )
            },
            containerColor = Color.Black,
        ) { padding ->
            // Permission UI stays inset under the top bar; camera draws edge-to-edge under it.
            CameraPermissionGate(Modifier.padding(padding)) {
                DetectContent(
                    viewModel,
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = padding.calculateBottomPadding()),
                    overlayTopInset = padding.calculateTopPadding(),
                )
            }
        }
        // Drawn above the Scaffold (covers the top bar); the camera keeps running underneath.
        if (session.pocketActive) {
            PocketOverlay(onUnlock = { viewModel.setPocket(false) })
        }
    }
}

/** Camera fills under the status bar; force light icons so they stay readable. */
@Composable
private fun LightStatusBarIcons() {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        val window = activity?.window
        if (window == null) return@DisposableEffect onDispose { }
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        val wasLight = insets.isAppearanceLightStatusBars
        insets.isAppearanceLightStatusBars = false
        onDispose { insets.isAppearanceLightStatusBars = wasLight }
    }
}

private sealed interface CameraStatus {
    data object Starting : CameraStatus
    data object Running : CameraStatus
    data object NoCamera : CameraStatus
    data class Failed(val message: String) : CameraStatus
}

@Composable
private fun DetectContent(
    viewModel: DetectViewModel,
    modifier: Modifier = Modifier,
    /** Space reserved by the translucent top bar + status bar; keep overlays readable below it. */
    overlayTopInset: Dp = 0.dp,
) {
    val s by viewModel.settings.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val detection by viewModel.detection.collectAsStateWithLifecycle()
    val thresholds by viewModel.thresholds.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { CameraController(context) }
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var status by remember { mutableStateOf<CameraStatus>(CameraStatus.Starting) }

    DisposableEffect(controller) { onDispose { controller.release() } }

    // Detection + audio run while the screen is started (also in pocket mode, which keeps it on).
    // Re-apply zoom/tuning on every START: CameraX often resets hardware zoom on stop/resume
    // while session.linearZoom is unchanged, which left the slider ahead of the real zoom.
    LifecycleStartEffect(viewModel, controller) {
        viewModel.onActive()
        controller.reapplyTuning()
        onStopOrDispose { viewModel.onInactive() }
    }

    LaunchedEffect(s.cameraFacing, lifecycleOwner) {
        status = CameraStatus.Starting
        status = try {
            controller.bind(lifecycleOwner, previewView, s.cameraFacing, viewModel.analyzer)
            viewModel.analyzer.mirrored = controller.boundFacing == CameraFacing.Front
            CameraStatus.Running
        } catch (e: CancellationException) {
            throw e
        } catch (e: CameraController.NoCameraException) {
            CameraStatus.NoCamera
        } catch (e: Exception) {
            CameraStatus.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    val tuning = viewModel.cameraTuning(s, session)
    LaunchedEffect(tuning) { controller.setTuning(tuning) }
    // After the first bind completes, push tuning once more (START may have run while camera was null).
    LaunchedEffect(status) {
        if (status == CameraStatus.Running) controller.reapplyTuning()
    }

    Column(modifier.fillMaxSize()) {
        // Camera overlay subtree is forced LTR so positions/gestures don't flip in Hebrew.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .background(Color.Black)
                    .pinchToZoom(viewModel::zoomBy),
            ) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

                GuideOverlay(
                    guideY = session.draggingGuideY ?: s.guideLineY,
                    locked = s.guideLocked,
                    lineColor = s.beamColor.uiColor(),
                    onDrag = viewModel::onGuideDrag,
                    onDragEnd = viewModel::onGuideDragEnd,
                    onToggleLock = viewModel::toggleGuideLock,
                )

                LevelMeter(
                    intensity = detection.intensity,
                    thresholds = thresholds,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 12.dp),
                )

                StatusBanner(
                    status,
                    detection,
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = overlayTopInset + 12.dp),
                )

                Column(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 4.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VerticalZoomSlider(
                        value = session.linearZoom,
                        onValueChange = viewModel::setLinearZoom,
                    )
                    QuickControls(
                        colorLabel = stringResource(if (s.beamColor == BeamColor.Red) R.string.color_red else R.string.color_green),
                        colorDot = s.beamColor.uiColor(),
                        cameraLabel = stringResource(if (s.cameraFacing == CameraFacing.Back) R.string.camera_back else R.string.camera_front),
                        onToggleColor = viewModel::toggleBeamColor,
                        onToggleCamera = viewModel::toggleCameraFacing,
                    )
                }

                if (session.showDebug) {
                    DebugInfo(detection, thresholds, Modifier.align(Alignment.BottomStart).padding(8.dp))
                }
            }
        }

        Controls(viewModel, session)
    }
}

/**
 * Vertical zoom control: after −90° rotation, slider min (0) is at the bottom (zoom out)
 * and max (1) is at the top (zoom in).
 */
@Composable
private fun VerticalZoomSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.detect_zoom)
    Box(
        modifier
            .width(40.dp)
            .height(168.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .requiredSize(width = 168.dp, height = 40.dp)
                .rotate(-90f),
        )
    }
}

@Composable
private fun StatusBanner(status: CameraStatus, detection: DetectionResult, modifier: Modifier = Modifier) {
    val text: String? = when (status) {
        CameraStatus.Starting -> null
        CameraStatus.NoCamera -> stringResource(R.string.detect_no_camera)
        is CameraStatus.Failed -> stringResource(R.string.detect_camera_error, status.message)
        CameraStatus.Running -> when {
            detection.calibrating -> stringResource(R.string.detect_calibrating)
            detection.detected -> stringResource(R.string.detect_beam_detected)
            else -> null
        }
    }
    if (text == null) return
    Column(
        modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        if (status == CameraStatus.Running && detection.calibrating) {
            LinearProgressIndicator(
                progress = { detection.calibrationProgress },
                modifier = Modifier
                    .padding(top = 4.dp)
                    .width(120.dp),
            )
        }
    }
}

@Composable
private fun DebugInfo(d: DetectionResult, thresholds: SensitivityModel.Thresholds, modifier: Modifier = Modifier) {
    val b = d.breakdown
    val text = String.format(
        Locale.US,
        "raw %.2f  int %.2f  fps %.1f\nflash %.2f  peak %.2f  cov %.2f  Y %.0f",
        d.rawScore, d.intensity, d.fps, b.flash, b.peak, b.coverage, b.meanLuma,
    ) + "\n" + stringResource(
        R.string.detect_thresholds,
        String.format(Locale.US, "%.2f", thresholds.startThreshold),
        String.format(Locale.US, "%.2f", thresholds.fullThreshold),
    )
    Text(
        text,
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .padding(6.dp),
    )
}

@Composable
private fun Controls(viewModel: DetectViewModel, session: DetectSessionState) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (session.showSensitivity) {
            SensitivityControls(
                sensitivity = settings.sensitivity,
                auto = session.sensitivityAuto,
                onSensitivityCommit = viewModel::setSensitivityManual,
                onToggleAuto = viewModel::toggleSensitivityAuto,
            )
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = session.showSensitivity,
                onClick = viewModel::toggleSensitivityPanel,
                label = { Text(stringResource(R.string.setting_sensitivity)) },
            )
            FilterChip(
                selected = session.aeAwbLock,
                onClick = viewModel::toggleAeAwbLock,
                label = { Text(stringResource(R.string.detect_lock_exposure)) },
            )
            FilterChip(
                selected = session.focusLock,
                onClick = viewModel::toggleFocusLock,
                label = { Text(stringResource(R.string.detect_lock_focus)) },
            )
            OutlinedButton(onClick = viewModel::recalibrate) { Text(stringResource(R.string.detect_calibrate)) }
            OutlinedButton(onClick = { viewModel.setPocket(true) }) { Text(stringResource(R.string.detect_pocket)) }
            FilterChip(
                selected = session.showDebug,
                onClick = viewModel::toggleDebug,
                label = { Text(stringResource(R.string.detect_debug)) },
            )
        }
    }
}

/** Sensitivity slider + Auto chip; local drag state commits when the finger lifts. */
@Composable
private fun SensitivityControls(
    sensitivity: Int,
    auto: Boolean,
    onSensitivityCommit: (Int) -> Unit,
    onToggleAuto: () -> Unit,
) {
    var local by remember(sensitivity) { mutableFloatStateOf(sensitivity.toFloat()) }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            local.roundToInt().toString(),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(28.dp),
        )
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onSensitivityCommit(local.roundToInt()) },
            valueRange = AppSettings.SENSITIVITY_RANGE.first.toFloat()..AppSettings.SENSITIVITY_RANGE.last.toFloat(),
            steps = AppSettings.SENSITIVITY_RANGE.last - AppSettings.SENSITIVITY_RANGE.first - 1,
            modifier = Modifier.weight(1f),
        )
        FilterChip(
            selected = auto,
            onClick = onToggleAuto,
            label = { Text(stringResource(R.string.detect_sensitivity_auto)) },
        )
    }
}

private fun BeamColor.uiColor(): Color = when (this) {
    BeamColor.Red -> Color(0xFFFF5252)
    BeamColor.Green -> Color(0xFF69F0AE)
}
