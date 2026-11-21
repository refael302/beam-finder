package io.github.refael302.beamfinder.update

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/** Downloads the release APK, verifies its package name, and installs via PackageInstaller. */
class GitHubUpdateInstaller(
    private val context: Context,
    private val client: OkHttpClient,
) : UpdateInstaller {

    override suspend fun install(info: UpdateInfo): InstallResult = withContext(Dispatchers.IO) {
        val url = info.downloadUrl ?: return@withContext InstallResult.Error("No APK download URL")
        val pm = context.packageManager
        if (!pm.canRequestPackageInstalls()) return@withContext InstallResult.NeedsPermission

        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apk = File(dir, "update.apk")
        try {
            download(url, apk)
            val archive = pm.getPackageArchiveInfo(apk.path, 0)
                ?: return@withContext InstallResult.Error("Invalid APK")
            if (archive.packageName != context.packageName) {
                return@withContext InstallResult.Error("Package mismatch: ${archive.packageName}")
            }
            commit(apk)
            InstallResult.Started
        } catch (e: IOException) {
            InstallResult.Error(e.message ?: "I/O error")
        } catch (e: SecurityException) {
            InstallResult.Error(e.message ?: "Not permitted")
        } finally {
            // The session has its own copy once written; safe to remove on every path.
            runCatching { apk.delete() }
        }
    }

    private fun download(url: String, target: File) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty response")
            body.byteStream().use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
        }
    }

    private fun commit(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val sessionId = installer.createSession(params)
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
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
    }
}
