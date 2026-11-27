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
 * Whether the automatic check still has to run.
 *
 * It belongs to «opening the app»: the checkbox says so, and running it again on every resume would
 * hit GitHub and pop the notice repeatedly within one launch.
 */
internal fun shouldCheckAppUpdateOnStart(checkOnStart: Boolean, alreadyStarted: Boolean): Boolean =
    checkOnStart && !alreadyStarted

/**
 * The release that earns a startup notice, or null.
 *
 * Only a newer version opens a window; an up-to-date answer or a failed check stays silent, because
 * the user asked to be told about updates, not about the absence of them.
 */
internal fun startupUpdateNotice(info: AppUpdateInfo?): AppUpdateInfo? = info?.takeIf { it.hasUpdate }

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
    private const val FALLBACK_VERSION = "1.2.0"
    private const val GITHUB_RELEASE_URL = "https://api.github.com/repos/SorrisoKDente/YabaiOCR/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Version the running build reports.
     *
     * The packaged resource wins because it is stamped by the build (`-PyabaiVersion`); the
     * `YABAI_VERSION` environment variable is the second source (`gradle run` and the AppImage
     * script both set it), and the constant is only the last resort for a source checkout.
     */
    private val packagedVersion: String by lazy {
        val resource = runCatching {
            DesktopAppUpdateChecker::class.java
                .getResourceAsStream("/yabai-version.properties")
                ?.bufferedReader()
                ?.use { it.readText() }
        }.getOrNull()
        resolveVersion(resource, System.getenv("YABAI_VERSION"))
    }

    fun getCurrentVersion(): String = packagedVersion

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
                val htmlUrl = json.get("html_url")?.asString ?: "https://github.com/SorrisoKDente/YabaiOCR/releases/latest"
                val bodyText = json.get("body")?.asString.orEmpty()

                val hasUpdate = compareVersions(packagedVersion, tagName) < 0

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
                    currentVersion = packagedVersion,
                    releaseUrl = htmlUrl,
                    releaseNotes = bodyText,
                    downloadUrl = preferredReleaseAsset(family, assets)?.downloadUrl
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Version from the packaged properties text, then the environment, then the constant.
     *
     * Kept pure so the precedence is testable without a build or an environment.
     */
    internal fun resolveVersion(propertiesText: String?, environmentVersion: String?): String {
        val fromResource = propertiesText
            ?.lineSequence()
            ?.firstOrNull { it.trim().startsWith("version=") }
            ?.substringAfter('=')
            ?.trim()
            .orEmpty()
        if (fromResource.isNotEmpty()) return fromResource

        val fromEnvironment = environmentVersion.orEmpty().removePrefix("v").trim()
        if (fromEnvironment.isNotEmpty()) return fromEnvironment

        return FALLBACK_VERSION
    }

    /** Shared with the Android build, so both apps agree on what «newer» means. */
    fun compareVersions(v1: String, v2: String): Int =
        dev.yabai.ocr.core.update.compareVersions(v1, v2)
}
