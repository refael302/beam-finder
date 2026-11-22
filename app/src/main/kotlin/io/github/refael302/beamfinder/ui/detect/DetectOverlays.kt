package io.github.refael302.beamfinder.ui.detect

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.refael302.beamfinder.R
import io.github.refael302.beamfinder.detection.sensitivity.SensitivityModel
import kotlin.math.roundToInt

/** Keeps the screen on while composed (Detect screen active). */
@Composable
fun KeepScreenOn() {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

/** Two-finger pinch -> [onZoom] with the incremental scale factor. Single-finger events pass through. */
fun Modifier.pinchToZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } >= 2) {
                val zoom = event.calculateZoom()
                if (zoom != 1f) {
                    onZoom(zoom)
                    event.changes.forEach { it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

/**
 * Full-width guide line marking the lens/height reference. VISUAL ONLY: detection scans the
 * whole frame. Drag the line vertically to move it (unless locked); the lock button sits at the
 * line's end. Draw this in an LTR subtree.
 */
@Composable
fun GuideOverlay(
    guideY: Float,
    locked: Boolean,
    lineColor: Color,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onToggleLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val density = LocalDensity.current
        val touchPx = with(density) { TOUCH_HEIGHT.toPx() }
        val buttonPx = with(density) { LOCK_BUTTON.toPx() }

        Canvas(Modifier.fillMaxSize()) {
            val y = guideY * size.height
            drawLine(Color.Black.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), 4.dp.toPx())
            drawLine(
                lineColor,
                Offset(0f, y),
                Offset(size.width, y),
                (if (locked) 3.dp else 2.dp).toPx(),
                pathEffect = if (locked) null else PathEffect.dashPathEffect(floatArrayOf(24f, 12f)),
            )
        }

        val latestGuide by rememberUpdatedState(guideY)
        var dragY by remember { mutableFloatStateOf(guideY) }
        val dragState = rememberDraggableState { delta ->
            dragY = (dragY + delta / heightPx).coerceIn(0f, 1f)
            onDrag(dragY)
        }
        Box(
            Modifier
                .offset { IntOffset(0, (guideY * heightPx - touchPx / 2f).roundToInt()) }
                .fillMaxWidth()
                .height(TOUCH_HEIGHT)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    enabled = !locked,
                    onDragStarted = { dragY = latestGuide },
                    onDragStopped = { onDragEnd() },
                ),
        )

        val lockDescription = stringResource(if (locked) R.string.detect_guide_unlock else R.string.detect_guide_lock)
        FilledIconToggleButton(
            checked = locked,
            onCheckedChange = { onToggleLock() },
            colors = IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = Color.Black.copy(alpha = 0.45f),
                contentColor = Color.White,
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset {
                    val y = (guideY * heightPx - buttonPx / 2f).coerceIn(0f, (heightPx - buttonPx).coerceAtLeast(0f))
                    IntOffset(0, y.roundToInt())
                }
                .padding(end = 8.dp)
                .size(LOCK_BUTTON)
                .semantics { contentDescription = lockDescription },
        ) {
            Icon(if (locked) Icons.Filled.Lock else Icons.Outlined.Lock, contentDescription = null)
        }
    }
}

/**
 * Compact quick controls for beam colour and camera, drawn as two small translucent pills.
 * Keep them in a corner of the (LTR) camera overlay so they barely cover the preview.
 */
@Composable
fun QuickControls(
    colorLabel: String,
    colorDot: Color,
    cameraLabel: String,
    onToggleColor: () -> Unit,
    onToggleCamera: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorDesc = stringResource(R.string.detect_quick_color_desc, colorLabel)
    val cameraDesc = stringResource(R.string.detect_quick_camera_desc, cameraLabel)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.End) {
        QuickPill(onClick = onToggleColor, description = colorDesc) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(colorDot),
            )
            Text(colorLabel, color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
        QuickPill(onClick = onToggleCamera, description = cameraDesc) {
            Icon(
                Icons.Filled.Cameraswitch,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
            Text(cameraLabel, color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun QuickPill(
    onClick: () -> Unit,
    description: String,
    content: @Composable RowScope.() -> Unit,
) {
    // Visual pill stays small; the touch target is padded to the 48dp minimum invisibly.
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick, role = Role.Button)
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * Vertical signal meter with start/full threshold marks. Thresholds come from
 * [SensitivityModel] via the ViewModel; nothing is hard-coded here.
 */
@Composable
fun LevelMeter(
    intensity: Float,
    thresholds: SensitivityModel.Thresholds,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.detect_level)
    Canvas(
        modifier
            .width(16.dp)
            .fillMaxHeight(0.6f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .semantics { contentDescription = description },
    ) {
        val level = intensity.coerceIn(0f, 1f)
        val color = when {
            level >= thresholds.fullThreshold -> Color(0xFF4CAF50)
            level >= thresholds.startThreshold -> Color(0xFFFFC107)
            else -> Color(0xFF9E9E9E)
        }
        val h = size.height * level
        drawRect(color, topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
        for (t in listOf(thresholds.startThreshold, thresholds.fullThreshold)) {
            val y = size.height * (1f - t)
            drawLine(Color.White, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
        }
    }
}

/**
 * Dim-screen mode: opaque black overlay, minimum screen brightness, hidden system bars, back
 * blocked. Long-press ([UNLOCK_HOLD_MS]) to unlock. The camera keeps running underneath.
 */
@Composable
fun PocketOverlay(onUnlock: () -> Unit) {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        val window = activity?.window
        val previousBrightness = window?.attributes?.screenBrightness
        window?.let { w ->
            w.attributes = w.attributes.apply { screenBrightness = POCKET_BRIGHTNESS }
            WindowCompat.getInsetsController(w, w.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            window?.let { w ->
                w.attributes = w.attributes.apply {
                    screenBrightness = previousBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
                WindowCompat.getInsetsController(w, w.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    BackHandler { /* swallow back while in the pocket */ }

    val unlock by rememberUpdatedState(onUnlock)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    val released = withTimeoutOrNull(UNLOCK_HOLD_MS) {
                        waitForUpOrCancellation()
                        true
                    }
                    if (released == null) unlock()
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            stringResource(R.string.detect_pocket_unlock_hint),
            color = Color.White.copy(alpha = 0.25f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 48.dp),
        )
    }
}

/** Shows [content] once CAMERA is granted; otherwise an explanation with a request button. */
@Composable
fun CameraPermissionGate(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    fun isGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(isGranted()) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        askedOnce = true
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = isGranted() }

    if (granted) {
        content()
        return
    }

    val permanentlyDenied = askedOnce && activity != null &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)

    Column(
        modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.detect_permission_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(if (permanentlyDenied) R.string.detect_permission_denied else R.string.detect_permission_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (permanentlyDenied) {
            Button(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }) { Text(stringResource(R.string.detect_permission_settings)) }
        } else {
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                Text(stringResource(R.string.detect_permission_grant))
            }
        }
    }
}

private val TOUCH_HEIGHT = 48.dp
private val LOCK_BUTTON = 40.dp
private const val UNLOCK_HOLD_MS = 1_200L
/** Near-minimum; exactly 0 turns the backlight fully off on some devices. */
private const val POCKET_BRIGHTNESS = 0.01f
