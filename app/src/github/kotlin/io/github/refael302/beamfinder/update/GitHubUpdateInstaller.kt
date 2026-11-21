package io.github.refael302.beamfinder.update

import android.content.Context

/**
 * Stub. To complete: download [UpdateInfo.downloadUrl], then install via PackageInstaller
 * (uses REQUEST_INSTALL_PACKAGES declared in the github manifest).
 */
class GitHubUpdateInstaller(@Suppress("unused") private val context: Context) : UpdateInstaller {
    override suspend fun install(info: UpdateInfo): InstallResult =
        InstallResult.Error("Installer not implemented yet")
}
