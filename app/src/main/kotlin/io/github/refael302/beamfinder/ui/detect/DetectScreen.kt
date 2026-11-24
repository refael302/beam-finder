package io.github.refael302.beamfinder.ui.detect

import androidx.activity.compose.LocalActivity
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
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
                    // Fixed lock control — kept with the right-side tools, not on the guide line / gear.
                    GuideLockButton(
                        locked = s.guideLocked,
                        onToggle = viewModel::toggleGuideLock,
                    )
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
    val locksActive = session.aeAwbLock || session.focusLock

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (session.openPanel) {
            DetectControlPanel.Sensitivity -> SensitivityControls(
                sensitivity = settings.sensitivity,
                auto = session.sensitivityAuto,
                onSensitivityCommit = viewModel::setSensitivityManual,
                onToggleAuto = viewModel::toggleSensitivityAuto,
            )
            DetectControlPanel.Locks -> LocksControls(
                aeAwbLock = session.aeAwbLock,
                focusLock = session.focusLock,
                onToggleAeAwb = viewModel::toggleAeAwbLock,
                onToggleFocus = viewModel::toggleFocusLock,
            )
            DetectControlPanel.Actions -> ActionsControls(
                onCalibrate = viewModel::recalibrate,
                onPocket = { viewModel.setPocket(true) },
            )
            DetectControlPanel.Advanced -> AdvancedControls(
                showDebug = session.showDebug,
                onToggleDebug = viewModel::toggleDebug,
            )
            null -> Unit
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TopicChip(
                selected = session.openPanel == DetectControlPanel.Sensitivity,
                onClick = { viewModel.toggleControlPanel(DetectControlPanel.Sensitivity) },
                icon = Icons.Filled.Tune,
                label = stringResource(R.string.detect_panel_sensitivity),
                modifier = Modifier.weight(1f),
            )
            TopicChip(
                selected = session.openPanel == DetectControlPanel.Locks,
                onClick = { viewModel.toggleControlPanel(DetectControlPanel.Locks) },
                icon = if (locksActive) Icons.Filled.Lock else Icons.Outlined.Lock,
                label = stringResource(R.string.detect_panel_locks),
                modifier = Modifier.weight(1f),
            )
            TopicChip(
                selected = session.openPanel == DetectControlPanel.Actions,
                onClick = { viewModel.toggleControlPanel(DetectControlPanel.Actions) },
                icon = Icons.Filled.Build,
                label = stringResource(R.string.detect_panel_actions),
                modifier = Modifier.weight(1f),
            )
            TopicChip(
                selected = session.openPanel == DetectControlPanel.Advanced,
                onClick = { viewModel.toggleControlPanel(DetectControlPanel.Advanced) },
                icon = Icons.Filled.BugReport,
                label = stringResource(R.string.detect_panel_advanced),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Shared Detect control chrome: transparent fill + 1dp outline. Selected only strengthens
 * border/tint — never a filled chip background — so every button matches.
 */
@Composable
private fun OutlinedControl(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    contentDescription: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .background(Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                content()
            }
        },
    )
}

/** Icon-only topic toggle; [label] is a11y only so four topics never truncate. */
@Composable
private fun TopicChip(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedControl(
        onClick = onClick,
        selected = selected,
        contentDescription = label,
        modifier = modifier,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
    }
}

/** Sensitivity slider + Auto toggle; local drag state commits when the finger lifts. */
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
        OutlinedControl(onClick = onToggleAuto, selected = auto) {
            Text(stringResource(R.string.detect_sensitivity_auto), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun LocksControls(
    aeAwbLock: Boolean,
    focusLock: Boolean,
    onToggleAeAwb: () -> Unit,
    onToggleFocus: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedControl(
            onClick = onToggleAeAwb,
            selected = aeAwbLock,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Filled.WbSunny, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.detect_lock_exposure),
                modifier = Modifier.padding(start = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        OutlinedControl(
            onClick = onToggleFocus,
            selected = focusLock,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Filled.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.detect_lock_focus),
                modifier = Modifier.padding(start = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun ActionsControls(
    onCalibrate: () -> Unit,
    onPocket: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedControl(onClick = onCalibrate, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.detect_calibrate),
                modifier = Modifier.padding(start = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        OutlinedControl(onClick = onPocket, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.DarkMode, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.detect_pocket),
                modifier = Modifier.padding(start = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun AdvancedControls(
    showDebug: Boolean,
    onToggleDebug: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedControl(onClick = onToggleDebug, selected = showDebug) {
            Icon(Icons.Filled.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.detect_debug),
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private fun BeamColor.uiColor(): Color = when (this) {
    BeamColor.Red -> Color(0xFFFF5252)
    BeamColor.Green -> Color(0xFF69F0AE)
}
