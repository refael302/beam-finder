package io.github.refael302.beamfinder.update

/** Stub. Later: Play In-App Updates API (com.google.android.play:app-update-ktx). */
class PlayUpdateChecker : UpdateChecker {
    override suspend fun check(currentVersion: String): UpdateResult = UpdateResult.NotSupported
}

class PlayUpdateInstaller : UpdateInstaller {
    override suspend fun install(
        info: UpdateInfo,
        onProgress: (InstallProgress) -> Unit,
    ): InstallResult = InstallResult.NotSupported
}
