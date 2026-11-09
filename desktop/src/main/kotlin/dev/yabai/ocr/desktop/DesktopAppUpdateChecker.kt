package dev.yabai.ocr.desktop

import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val currentVersion: String,
    val releaseUrl: String,
    val releaseNotes: String,
    /** Direct download for this OS when the release publishes a matching asset. */
    val downloadUrl: String? = null,
)

internal data class ReleaseAsset(val name: String, val downloadUrl: String)

/**
 * Picks the installer that matches the running OS.
 *
 * A release can publish assets for several platforms; sending a Windows user to the `.AppImage`
 * (or the opposite) makes the update button useless. The order inside a family is the preference:
 * an `.exe` installer is friendlier than a `.msi`, and Linux only ships AppImages.
 */
internal fun preferredReleaseAsset(family: OsFamily, assets: List<ReleaseAsset>): ReleaseAsset? {
    val suffixes = when (family) {
        OsFamily.WINDOWS -> listOf(".exe", ".msi")
        OsFamily.LINUX -> listOf(".appimage")
        OsFamily.MACOS -> listOf(".dmg", ".pkg")
        OsFamily.OTHER -> emptyList()
    }
    for (suffix in suffixes) {
        assets.firstOrNull { it.name.lowercase().endsWith(suffix) }?.let { return it }
    }
    return null
}

object DesktopAppUpdateChecker {
    private const val CURRENT_VERSION = "1.0.0"
    private const val GITHUB_RELEASE_URL = "https://api.github.com/repos/SorrisoKDente/yabai_ocr/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun getCurrentVersion(): String = CURRENT_VERSION

    internal fun checkForAppUpdate(family: OsFamily = DesktopPlatform.family): AppUpdateInfo? {
        return try {
            val request = Request.Builder()
                .url(GITHUB_RELEASE_URL)
                .header("User-Agent", "YabaiOCR-Desktop")
                .header("Accept", "application/vnd.github+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bodyStr = response.body?.string() ?: return null
                val json = JsonParser.parseString(bodyStr).asJsonObject

                val tagName = json.get("tag_name")?.asString?.removePrefix("v")?.trim() ?: return null
                val htmlUrl = json.get("html_url")?.asString ?: "https://github.com/SorrisoKDente/yabai_ocr/releases/latest"
                val bodyText = json.get("body")?.asString.orEmpty()

                val hasUpdate = compareVersions(CURRENT_VERSION, tagName) < 0

                val assets = json.getAsJsonArray("assets")
                    ?.mapNotNull { element ->
                        val asset = element.asJsonObject
                        val name = asset.get("name")?.asString ?: return@mapNotNull null
                        val url = asset.get("browser_download_url")?.asString ?: return@mapNotNull null
                        ReleaseAsset(name, url)
                    }
                    .orEmpty()

                AppUpdateInfo(
                    hasUpdate = hasUpdate,
                    latestVersion = tagName,
                    currentVersion = CURRENT_VERSION,
                    releaseUrl = htmlUrl,
                    releaseNotes = bodyText,
                    downloadUrl = preferredReleaseAsset(family, assets)?.downloadUrl
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    fun compareVersions(v1: String, v2: String): Int {
        val p1 = v1.split(".").mapNotNull { it.toIntOrNull() }
        val p2 = v2.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(p1.size, p2.size)
        for (i in 0 until maxLen) {
            val num1 = p1.getOrElse(i) { 0 }
            val num2 = p2.getOrElse(i) { 0 }
            if (num1 != num2) return num1.compareTo(num2)
        }
        return 0
    }
}
