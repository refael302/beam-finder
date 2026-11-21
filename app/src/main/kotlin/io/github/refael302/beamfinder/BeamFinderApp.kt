package io.github.refael302.beamfinder

import android.app.Application

class BeamFinderApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
