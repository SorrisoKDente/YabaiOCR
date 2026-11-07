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
        val installedFiles = if (dictDir.exists() && dictDir.isDirectory) {
            dictDir.listFiles()?.filter {
                it.isFile && (it.extension.equals("yabai", ignoreCase = true) || it.extension.equals("zip", ignoreCase = true))
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

        installedFiles.forEach { file ->
            val dict = runCatching { CompactDictionary.open(file) }.getOrNull()
            val installedRev = dict?.info?.revision.orEmpty()
            dict?.close()

            val catalogEntry = DesktopDictionaryCatalog.entries.find { entry ->
                file.nameWithoutExtension.lowercase().contains(entry.id.lowercase()) ||
                        file.nameWithoutExtension.lowercase().contains(entry.name.lowercase().take(6).lowercase())
            }

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
                        val req = Request.Builder().url(catalogEntry.url).build()
                        client.newCall(req).execute().use { resp ->
                            val finalUrl = resp.request.url.toString()
                            val remoteVersion = extractVersionFromUrl(finalUrl) ?: formatRemoteLabel(resp.header("Last-Modified"), resp.header("ETag"))
                            val remoteEtag = resp.header("ETag")
                            val remoteLastModified = resp.header("Last-Modified")
                            val remoteLength = resp.header("Content-Length")?.toLongOrNull() ?: 0L
                            val localSig = readSignature(file)
                            val hasUpdate = localSig == null || localSig.length != remoteLength ||
                                    (remoteEtag != null && localSig.etag != remoteEtag) ||
                                    (remoteLastModified != null && localSig.lastModified != remoteLastModified)
                            available[file.name] = hasUpdate
                            if (hasUpdate) {
                                remotes[file.name] = remoteVersion
                            }
                        }
                    }
                }
            }
        }

        settings.lastDictUpdateCheck = now
        val count = available.values.count { it }
        return DesktopDictCheckResult(now, count, available, remotes)
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

    private data class FileSignature(val etag: String?, val lastModified: String?, val length: Long)

    private fun readSignature(file: File): FileSignature? {
        val sigFile = File(file.parentFile, "${file.name}.sig")
        if (!sigFile.exists()) return null
        val text = sigFile.readText().trim()
        val parts = text.split("\n")
        val etag = parts.getOrNull(0)?.takeIf { it.isNotBlank() && it != "null" }
        val lm = parts.getOrNull(1)?.takeIf { it.isNotBlank() && it != "null" }
        val len = parts.getOrNull(2)?.toLongOrNull() ?: 0L
        return FileSignature(etag, lm, len)
    }

    private fun formatRemoteLabel(lastModified: String?, etag: String?): String {
        if (lastModified != null) return lastModified
        if (etag != null) return etag.replace("\"", "").take(10)
        return "Disponível"
    }

    private fun extractVersionFromUrl(url: String): String? {
        val regex = Regex("/releases/download/([^/]+)/")
        return regex.find(url)?.groupValues?.getOrNull(1)
    }
}
