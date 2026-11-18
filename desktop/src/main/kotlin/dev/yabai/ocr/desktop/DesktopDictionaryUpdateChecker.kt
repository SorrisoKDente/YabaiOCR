package dev.yabai.ocr.desktop

import com.google.gson.JsonParser
import dev.yabai.ocr.core.dict.CompactDictionary
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class DesktopDictCheckResult(
    val checkedAt: Long,
    val updatesAvailableCount: Int,
    val updateAvailableMap: Map<String, Boolean>,
    val remoteRevisionMap: Map<String, String>,
    /** Revision each installed file reports, so the notice can print «old → new». */
    val installedRevisionMap: Map<String, String> = emptyMap(),
)

object DesktopDictionaryUpdateChecker {

    fun shouldCheck(settings: DesktopSettings, now: Long = System.currentTimeMillis()): Boolean {
        val interval = settings.dictionaryUpdateInterval
        if (interval == DesktopUpdateInterval.OFF) return false

        val elapsed = now - settings.lastDictUpdateCheck
        val requiredIntervalMs = interval.days * 24 * 3600 * 1000L
        return elapsed >= requiredIntervalMs
    }

    fun checkUpdates(settings: DesktopSettings): DesktopDictCheckResult {
        val now = System.currentTimeMillis()
        val dictDir = settings.dictDirectory()
        // `.yabai` is a directory of blobs; only an imported ZIP is a file. Filtering on `isFile`
        // alone skipped every compiled dictionary, so the periodic check found nothing to compare.
        val installedFiles = if (dictDir.isDirectory) {
            dictDir.listFiles()?.filter { file ->
                file.isDirectory && file.extension.equals("yabai", ignoreCase = true) ||
                    file.isFile && file.extension.equals("zip", ignoreCase = true)
            }.orEmpty()
        } else {
            emptyList()
        }

        if (installedFiles.isEmpty()) {
            settings.lastDictUpdateCheck = now
            return DesktopDictCheckResult(now, 0, emptyMap(), emptyMap())
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val available = mutableMapOf<String, Boolean>()
        val remotes = mutableMapOf<String, String>()
        val installedRevisions = mutableMapOf<String, String>()
        // One release list per repository per check: GitHub allows 60 unauthenticated calls an hour.
        val releaseLists = mutableMapOf<String, String?>()
        val fetchText: (String) -> String? = { url ->
            if (releaseLists.containsKey(url)) {
                releaseLists[url]
            } else {
                runCatching {
                    client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        if (response.isSuccessful) response.body?.string() else null
                    }
                }.getOrNull().also { releaseLists[url] = it }
            }
        }

        installedFiles.forEach { file ->
            val dict = runCatching { CompactDictionary.open(file) }.getOrNull()
            val installedRev = dict?.info?.revision.orEmpty()
            dict?.close()
            if (installedRev.isNotBlank()) installedRevisions[file.name] = installedRev

            val catalogEntry = catalogEntryForDictionaryFile(file.name)

            if (catalogEntry != null) {
                val indexUrl = catalogEntry.indexUrl
                if (indexUrl != null) {
                    runCatching {
                        val req = Request.Builder().url(indexUrl).build()
                        client.newCall(req).execute().use { resp ->
                            val jsonText = resp.body?.string().orEmpty()
                            val obj = JsonParser.parseString(jsonText).asJsonObject
                            val remoteRev = obj.get("revision")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                            if (remoteRev.isNotBlank()) {
                                val order = compareRevisions(installedRev, remoteRev)
                                val hasUpdate = installedRev.isBlank() || (order != null && order < 0)
                                available[file.name] = hasUpdate
                                if (hasUpdate) {
                                    remotes[file.name] = remoteRev
                                }
                            } else {
                                available[file.name] = false
                            }
                        }
                    }
                } else if (catalogEntry.url != null) {
                    runCatching {
                        // GitHub releases: compare publications, not the HTTP metadata of `latest`.
                        // The newest publication is often an empty stub, and comparing against it
                        // offered an update that just reinstalled the same fallback version.
                        val remote = newestReleaseFor(catalogEntry.url, fetchText)
                        if (remote == null) {
                            available[file.name] = false
                        } else {
                            val hasUpdate = dictionaryUpdateAvailable(readDictionarySignature(file), remote)
                            available[file.name] = hasUpdate
                            if (hasUpdate) remotes[file.name] = remote.tag
                        }
                    }
                }
            }
        }

        settings.lastDictUpdateCheck = now
        val count = available.values.count { it }
        return DesktopDictCheckResult(now, count, available, remotes, installedRevisions)
    }

    internal fun compareRevisions(installed: String, remote: String): Int? {
        val left = revisionParts(installed) ?: return null
        val right = revisionParts(remote) ?: return null
        val len = maxOf(left.size, right.size)
        for (i in 0 until len) {
            val l = left.getOrElse(i) { 0 }
            val r = right.getOrElse(i) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    private fun revisionParts(text: String): List<Int>? {
        val parts = Regex("\\d+").findAll(text).map { it.value.toInt() }.toList()
        return parts.ifEmpty { null }
    }

}
