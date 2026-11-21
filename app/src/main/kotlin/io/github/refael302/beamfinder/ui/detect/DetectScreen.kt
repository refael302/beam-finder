package io.github.refael302.beamfinder.ui.detect

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.refael302.beamfinder.R
import io.github.refael302.beamfinder.camera.CameraController
import io.github.refael302.beamfinder.detection.pipeline.DetectionResult
import io.github.refael302.beamfinder.detection.sensitivity.SensitivityModel
import io.github.refael302.beamfinder.settings.BeamColor
import io.github.refael302.beamfinder.settings.CameraFacing
import io.github.refael302.beamfinder.ui.AppViewModelProvider
import io.github.refael302.beamfinder.ui.components.BeamTopBar
import kotlinx.coroutines.CancellationException
import java.util.Locale

@Composable
fun DetectScreen(
    onBack: () -> Unit,
    viewModel: DetectViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    KeepScreenOn()

    Box(Modifier.fillMaxSize()) {
        Scaffold(topBar = { BeamTopBar(stringResource(R.string.detect_title), onBack) }) { padding ->
            CameraPermissionGate(Modifier.padding(padding)) {
                DetectContent(viewModel, Modifier.padding(padding))
            }
        }
        // Drawn above the Scaffold (covers the top bar); the camera keeps running underneath.
        if (session.pocketActive) {
            PocketOverlay(onUnlock = { viewModel.setPocket(false) })
        }
    }
}

private sealed interface CameraStatus {
    data object Starting : CameraStatus
    data object Running : CameraStatus
    data object NoCamera : CameraStatus
    data class Failed(val message: String) : CameraStatus
}

@Composable
private fun DetectContent(viewModel: DetectViewModel, modifier: Modifier = Modifier) {
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
    LifecycleStartEffect(viewModel) {
        viewModel.onActive()
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

                StatusBanner(status, detection, Modifier.align(Alignment.TopCenter).padding(top = 12.dp))

                QuickControls(
                    colorLabel = stringResource(if (s.beamColor == BeamColor.Red) R.string.color_red else R.string.color_green),
                    colorDot = s.beamColor.uiColor(),
                    cameraLabel = stringResource(if (s.cameraFacing == CameraFacing.Back) R.string.camera_back else R.string.camera_front),
                    onToggleColor = viewModel::toggleBeamColor,
                    onToggleCamera = viewModel::toggleCameraFacing,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 4.dp, bottom = 4.dp),
                )

                if (session.showDebug) {
                    DebugInfo(detection, thresholds, Modifier.align(Alignment.BottomStart).padding(8.dp))
                }
            }
        }

        Controls(viewModel, session)
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
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.detect_zoom), style = MaterialTheme.typography.labelLarge)
            Slider(
                value = session.linearZoom,
                onValueChange = viewModel::setLinearZoom,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            )
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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

private fun BeamColor.uiColor(): Color = when (this) {
    BeamColor.Red -> Color(0xFFFF5252)
    BeamColor.Green -> Color(0xFF69F0AE)
}
