package io.github.refael302.beamfinder

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import io.github.refael302.beamfinder.ui.AppNavHost
import io.github.refael302.beamfinder.ui.theme.BeamFinderTheme

/** Single activity. Extends AppCompatActivity so per-app locales work on all API levels. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BeamFinderTheme {
                AppNavHost()
            }
        }
    }
}
