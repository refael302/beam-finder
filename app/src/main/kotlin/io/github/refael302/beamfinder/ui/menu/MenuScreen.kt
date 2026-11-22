package io.github.refael302.beamfinder.ui.menu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.refael302.beamfinder.R

@Composable
fun MenuScreen(
    onStart: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    val beamColor = MaterialTheme.colorScheme.primary

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 32.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BeamMark(color = beamColor, modifier = Modifier.size(88.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displaySmall,
                    color = beamColor,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.menu_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(40.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.menu_start))
                }
                TextButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.menu_settings))
                }
                TextButton(onClick = onAbout, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.menu_about))
                }
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun BeamMark(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val beamHalf = size.height * 0.38f
            val stroke = size.minDimension * 0.045f
            val hotspot = size.minDimension * 0.1f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.28f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = size.minDimension * 0.55f,
                ),
            )
            drawLine(
                color = color,
                start = Offset(cx, cy - beamHalf),
                end = Offset(cx, cy + beamHalf),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawCircle(color = Color.White.copy(alpha = 0.95f), radius = hotspot, center = Offset(cx, cy))
            drawCircle(color = color.copy(alpha = 0.55f), radius = hotspot * 1.7f, center = Offset(cx, cy))
        }
    }
}
