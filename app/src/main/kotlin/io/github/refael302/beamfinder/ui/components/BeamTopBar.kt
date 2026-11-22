package io.github.refael302.beamfinder.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import io.github.refael302.beamfinder.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeamTopBar(
    title: String,
    onBack: () -> Unit,
    /** When true, the bar is a translucent overlay so content (e.g. camera) shows through to the top edge. */
    overlay: Boolean = false,
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        },
        colors = if (overlay) {
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black.copy(alpha = 0.35f),
                scrolledContainerColor = Color.Black.copy(alpha = 0.35f),
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White,
                actionIconContentColor = Color.White,
            )
        } else {
            TopAppBarDefaults.topAppBarColors()
        },
    )
}
