package io.github.refael302.beamfinder.update

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** Downloads the release APK, verifies its package name, and installs via PackageInstaller. */
class GitHubUpdateInstaller(
    private val context: Context,
    client: OkHttpClient,
) : UpdateInstaller {

    // Large APKs need a longer read idle window than OkHttp's 10s default.
    private val client: OkHttpClient = client.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    override suspend fun install(
        info: UpdateInfo,
        onProgress: (InstallProgress) -> Unit,
    ): InstallResult = withContext(Dispatchers.IO) {
        val url = info.downloadUrl ?: return@withContext InstallResult.Error("No APK download URL")
        val pm = context.packageManager
        if (!pm.canRequestPackageInstalls()) return@withContext InstallResult.NeedsPermission

        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apk = File(dir, "update.apk")
        try {
            download(url, apk, onProgress)
            ensureActive()
            onProgress(InstallProgress.Preparing)
            val archive = pm.getPackageArchiveInfo(apk.path, 0)
                ?: return@withContext InstallResult.Error("Invalid APK")
            if (archive.packageName != context.packageName) {
                return@withContext InstallResult.Error("Package mismatch: ${archive.packageName}")
            }
            commitAndAwait(apk, onProgress)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            InstallResult.Error(e.message ?: "I/O error")
        } catch (e: SecurityException) {
            InstallResult.Error(e.message ?: "Not permitted")
        } finally {
            // The session has its own copy once written; safe to remove on every path.
            runCatching { apk.delete() }
        }
    }

    private suspend fun download(
        url: String,
        target: File,
        onProgress: (InstallProgress) -> Unit,
    ) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "BeamFinder-Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty response")
            val total = body.contentLength().takeIf { it > 0 }
            var read = 0L
            var lastReported = -PROGRESS_REPORT_BYTES
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (read - lastReported >= PROGRESS_REPORT_BYTES || total != null && read == total) {
                            lastReported = read
                            onProgress(InstallProgress.Downloading(read, total))
                        }
                    }
                    output.flush()
                }
            }
            if (read == 0L) throw IOException("Empty APK")
            onProgress(InstallProgress.Downloading(read, total ?: read))
        }
    }

    private suspend fun commitAndAwait(
        apk: File,
        onProgress: (InstallProgress) -> Unit,
    ): InstallResult {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val sessionId = installer.createSession(params)
        val statusDeferred = InstallStatusHub.register(sessionId)
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("update.apk", 0, apk.length()).use { out ->
                        input.copyTo(out)
                        session.fsync(out)
                    }
                }
                val intent = Intent(context, InstallStatusReceiver::class.java)
                    .setComponent(ComponentName(context, InstallStatusReceiver::class.java))
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                onProgress(InstallProgress.AwaitingConfirmation)
                session.commit(pending.intentSender)
            }

            // Wait for the final status (PENDING_USER_ACTION is handled in the receiver
            // without completing; SUCCESS / FAILURE complete the deferred).
            val status = statusDeferred.await()
            return when (status.code) {
                PackageInstaller.STATUS_SUCCESS -> InstallResult.Success
                PackageInstaller.STATUS_FAILURE_ABORTED ->
                    InstallResult.Error(InstallErrorCodes.CANCELLED)
                PackageInstaller.STATUS_FAILURE_CONFLICT,
                PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
                -> InstallResult.Error(InstallErrorCodes.SIGNATURE)
                else -> InstallResult.Error(
                    status.message?.takeIf { it.isNotBlank() }
                        ?: "Install failed (code ${status.code})",
                )
            }
        } catch (e: CancellationException) {
            InstallStatusHub.abandon(sessionId)
            runCatching { installer.abandonSession(sessionId) }
            throw e
        } catch (e: Exception) {
            InstallStatusHub.abandon(sessionId)
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
    }

    private companion object {
        const val PROGRESS_REPORT_BYTES = 256 * 1024L
    }
}
