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
)

object DesktopAppUpdateChecker {
    private const val CURRENT_VERSION = "1.0.0"
    private const val GITHUB_RELEASE_URL = "https://api.github.com/repos/SorrisoKDente/yabai_ocr/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun getCurrentVersion(): String = CURRENT_VERSION

    fun checkForAppUpdate(): AppUpdateInfo? {
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

                AppUpdateInfo(
                    hasUpdate = hasUpdate,
                    latestVersion = tagName,
                    currentVersion = CURRENT_VERSION,
                    releaseUrl = htmlUrl,
                    releaseNotes = bodyText
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
