package io.github.refael302.beamfinder.update

import android.content.Context
import okhttp3.OkHttpClient

/** Flavor factory (github): real release checker + installer stub. */
fun createUpdateChecker(context: Context, client: OkHttpClient): UpdateChecker =
    GitHubReleaseChecker(client)

fun createUpdateInstaller(context: Context): UpdateInstaller =
    GitHubUpdateInstaller(context.applicationContext)
