package io.github.refael302.beamfinder.update

data class UpdateInfo(
    val versionName: String,
    val releaseNotes: String? = null,
    val pageUrl: String? = null,
    val downloadUrl: String? = null,
)

sealed interface UpdateResult {
    data object UpToDate : UpdateResult
    data class Available(val info: UpdateInfo) : UpdateResult

    /** This distribution does not self-update (e.g. Google Play). */
    data object NotSupported : UpdateResult
    data class Error(val message: String) : UpdateResult
}

interface UpdateChecker {
    suspend fun check(currentVersion: String): UpdateResult
}

sealed interface InstallResult {
    data object Started : InstallResult
    data object NotSupported : InstallResult
    data class Error(val message: String) : InstallResult
}

interface UpdateInstaller {
    suspend fun install(info: UpdateInfo): InstallResult
}
