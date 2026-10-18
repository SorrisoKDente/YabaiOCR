package dev.yabai.ocr.dict

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Fetches a dictionary archive.
 *
 * Wiktionary-to-Yomitan only publishes small descriptor files on GitHub and keeps the archives behind
 * a storage URL written inside them, so those entries are resolved in two requests. Everything else
 * is a plain download from a "latest release" link.
 */
class DictionaryDownloader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS) // a 37 MB download on mobile data takes a while
        .build()

    /** Resolves and downloads [entry] into [target]. [onProgress] gets (bytesRead, totalOrZero). */
    suspend fun download(
        entry: DictionaryCatalog.Entry,
        target: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        val url = entry.url ?: entry.indexUrl?.let { resolveDownloadUrl(it) }
            ?: throw IOException("O catálogo não informou de onde baixar «${entry.name}».")

        target.parentFile?.mkdirs()
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("O servidor respondeu ${response.code} ao baixar ${entry.name}.")
            }
            val body = response.body ?: throw IOException("Resposta vazia do servidor.")
            val total = body.contentLength()
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    copyWithProgress(input, output, total, onProgress)
                }
            }
        }
        if (target.length() == 0L) throw IOException("O arquivo baixado veio vazio.")
        target
    }

    /** Reads `downloadUrl` out of a Wiktionary-to-Yomitan descriptor. */
    private fun resolveDownloadUrl(indexUrl: String): String {
        val request = Request.Builder().url(indexUrl).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Não foi possível consultar o catálogo do Wikcionário (HTTP ${response.code}).")
            }
            val text = response.body?.string().orEmpty()
            val obj = JsonParser.parseString(text).asJsonObject
            val downloadUrl = obj.get("downloadUrl")?.asString
            if (downloadUrl.isNullOrBlank()) {
                throw IOException("O catálogo do Wikcionário não informou um link de download.")
            }
            return downloadUrl
        }
    }

    private suspend fun copyWithProgress(
        input: InputStream,
        output: java.io.OutputStream,
        total: Long,
        onProgress: (Long, Long) -> Unit,
    ) {
        val buffer = ByteArray(1 shl 16)
        var read = 0L
        var lastReport = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            read += count
            // Reporting on every chunk would queue thousands of UI updates.
            if (read - lastReport >= 128 * 1024) {
                lastReport = read
                onProgress(read, total)
            }
        }
        output.flush()
        onProgress(read, total)
    }

    companion object {
        private const val USER_AGENT = "YabaiOCR/1.0 (Android)"
    }
}
