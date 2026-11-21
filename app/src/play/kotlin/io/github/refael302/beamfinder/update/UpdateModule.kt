package io.github.refael302.beamfinder.update

import android.content.Context
import okhttp3.OkHttpClient

/** Flavor factory (play): updates are delegated to Google Play. */
@Suppress("UNUSED_PARAMETER")
fun createUpdateChecker(context: Context, client: OkHttpClient): UpdateChecker = PlayUpdateChecker()

@Suppress("UNUSED_PARAMETER")
fun createUpdateInstaller(context: Context, client: OkHttpClient): UpdateInstaller = PlayUpdateInstaller()
