package io.github.refael302.beamfinder.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Checks https://api.github.com/repos/<repo>/releases/latest and compares the tag with the
 * running version. Basic implementation; extend later (APK asset selection, caching, rate limits).
 */
class GitHubReleaseChecker(
    private val client: OkHttpClient,
    private val repo: String = "refael302/beam-finder",
) : UpdateChecker {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun check(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        try {
            fetchAndCompare(currentVersion)
        } catch (e: IOException) {
            UpdateResult.Error(e.message ?: "network error")
        } catch (e: SerializationException) {
            UpdateResult.Error(e.message ?: "invalid response")
        }
    }

    private fun fetchAndCompare(currentVersion: String): UpdateResult {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()

        client.newCall(request).execute().use { response ->
            // No releases published yet — not the same as "already on latest".
            if (response.code == 404) {
                return UpdateResult.Error("no releases yet")
            }
            if (!response.isSuccessful) return UpdateResult.Error("HTTP ${response.code}")
            val body = response.body?.string() ?: return UpdateResult.Error("empty response")

            val release = json.decodeFromString<GitHubRelease>(body)
            val latest = SemVer.parse(release.tagName)
                ?: return UpdateResult.Error("bad version tag: ${release.tagName}")
            val current = SemVer.parse(currentVersion)
                ?: return UpdateResult.Error("bad current version: $currentVersion")

            if (latest <= current) return UpdateResult.UpToDate

            val apks = release.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
            val apk = apks.firstOrNull { PREFERRED_ASSET.matches(it.name) } ?: apks.firstOrNull()
            return UpdateResult.Available(
                UpdateInfo(
                    versionName = latest.toString(),
                    releaseNotes = release.body,
                    pageUrl = release.htmlUrl,
                    downloadUrl = apk?.downloadUrl,
                ),
            )
        }
    }

    private companion object {
        /** beam-finder-github-*.apk */
        val PREFERRED_ASSET = Regex("^beam-finder-github-.*\\.apk$", RegexOption.IGNORE_CASE)
    }

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String,
        @SerialName("html_url") val htmlUrl: String? = null,
        val body: String? = null,
        val assets: List<GitHubAsset> = emptyList(),
    )

    @Serializable
    private data class GitHubAsset(
        val name: String,
        @SerialName("browser_download_url") val downloadUrl: String,
    )
}
