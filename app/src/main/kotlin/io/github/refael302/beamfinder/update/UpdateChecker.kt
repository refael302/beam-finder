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

sealed interface InstallProgress {
    /** [totalBytes] is null when the server did not send Content-Length. */
    data class Downloading(val bytesRead: Long, val totalBytes: Long?) : InstallProgress
    data object Preparing : InstallProgress
    data object AwaitingConfirmation : InstallProgress
}

sealed interface InstallResult {
    data object Success : InstallResult
    data object NotSupported : InstallResult

    /** User must allow "install unknown apps" for this app first. */
    data object NeedsPermission : InstallResult
    data class Error(val message: String) : InstallResult
}

/** Stable InstallResult.Error.message values mapped to UI strings. */
object InstallErrorCodes {
    const val CANCELLED = "install_cancelled"
    const val SIGNATURE = "signature_mismatch"
}

interface UpdateInstaller {
    suspend fun install(
        info: UpdateInfo,
        onProgress: (InstallProgress) -> Unit = {},
    ): InstallResult
}
