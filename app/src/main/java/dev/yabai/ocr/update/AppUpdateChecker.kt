package dev.yabai.ocr.update

import dev.yabai.ocr.BuildConfig
import dev.yabai.ocr.core.update.compareVersions as compareCoreVersions
import dev.yabai.ocr.core.update.isNewerVersion
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** One GitHub release, as far as the app cares about it. */
data class AppUpdate(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val currentVersion: String,
    val releaseUrl: String,
    /** The release body, in Markdown — the same text the desktop app previews. */
    val releaseNotes: String,
    val apkUrl: String?,
)

/**
 * Checks the project's GitHub releases for a newer APK.
 *
 * The data comes from the release itself (`tag_name`, `body`, `assets`), so the preview shown to the
 * user is the text the release was published with, not a copy maintained here.
 */
object AppUpdateChecker {

    private const val RELEASES_URL =
        "https://api.github.com/repos/SorrisoKDente/YabaiOCR/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Version installed on this phone, including the debug suffix. */
    fun currentVersion(): String = BuildConfig.VERSION_NAME

    /** Null means the check could not be made (no network, no release, unexpected payload). */
    fun check(): AppUpdate? {
        return try {
            val request = Request.Builder()
                .url(RELEASES_URL)
                .header("User-Agent", "YabaiOCR-Android")
                .header("Accept", "application/vnd.github+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)

                val tagName = json.optString("tag_name").removePrefix("v").trim()
                if (tagName.isEmpty()) return null
                val htmlUrl = json.optString("html_url")
                val notes = json.optString("body")

                val apkUrl = runCatching {
                    val assets = json.optJSONArray("assets") ?: return@runCatching null
                    (0 until assets.length())
                        .map { assets.getJSONObject(it) }
                        .firstOrNull { it.optString("name").lowercase().endsWith(".apk") }
                        ?.optString("browser_download_url")
                }.getOrNull()

                AppUpdate(
                    hasUpdate = isNewer(currentVersion(), tagName),
                    latestVersion = tagName,
                    currentVersion = currentVersion(),
                    releaseUrl = htmlUrl,
                    releaseNotes = notes,
                    apkUrl = apkUrl,
                )
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * True when [latest] is a newer version than [current].
     *
     * A debug build reports `1.0.0-debug`: the suffix is dropped before comparing, because
     * `1.0.0-debug` is the same release as `1.0.0`, not a newer one.
     */
    fun isNewer(current: String, latest: String): Boolean = isNewerVersion(current, latest)

    fun compareVersions(first: String, second: String): Int = compareCoreVersions(first, second)
}
