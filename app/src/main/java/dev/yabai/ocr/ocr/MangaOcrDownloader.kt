package dev.yabai.ocr.ocr

import dev.yabai.ocr.core.manga.MangaOcrModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class MangaOcrDownloader(
    private val store: MangaOcrModelStore,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun downloadModels(onProgress: suspend (String, Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        store.ensureDirectory()

        val filesToDownload = listOf(
            Triple("Detector", MangaOcrModelStore.DETECTOR_URL, store.detectorFile),
            Triple("Recognizer", MangaOcrModelStore.RECOGNIZER_URL, store.recognizerFile),
            Triple("Vocab", MangaOcrModelStore.VOCAB_URL, store.vocabFile),
        )

        for ((label, url, target) in filesToDownload) {
            if (target.exists() && target.length() > 0) {
                onProgress(label, target.length(), target.length())
                continue
            }
            val tempFile = File(target.parentFile, "${target.name}.tmp")
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .build()
                val call = client.newCall(request)
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code} ($label)")
                    }
                    val body = response.body ?: throw IOException("Empty response ($label)")
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        tempFile.outputStream().use { output ->
                            val buffer = ByteArray(1 shl 16)
                            var read = 0L
                            var lastReport = 0L
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                read += count
                                if (read - lastReport >= 128 * 1024 || (total > 0 && read == total)) {
                                    lastReport = read
                                    onProgress(label, read, if (total > 0) total else 0L)
                                }
                            }
                            output.flush()
                        }
                    }
                }
                if (tempFile.exists() && tempFile.length() > 0) {
                    if (target.exists()) target.delete()
                    if (!tempFile.renameTo(target)) {
                        tempFile.copyTo(target, overwrite = true)
                        tempFile.delete()
                    }
                    if (!target.exists() || target.length() == 0L) {
                        throw IOException("Failed to save file for $label.")
                    }
                } else {
                    throw IOException("Temporary file for $label was empty.")
                }
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }
    }
}
