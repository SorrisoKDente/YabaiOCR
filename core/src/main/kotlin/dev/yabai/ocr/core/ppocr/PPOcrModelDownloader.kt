package dev.yabai.ocr.core.ppocr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Downloads the three PP-OCRv6_manga files the engine reads.
 *
 * Files already on disk are kept, so retrying after a failed download does not start the 21 MB
 * recogniser again. Each file lands in a `.tmp` first: a half-written model is worse than none,
 * because `isAvailable()` accepts it and the ONNX session then fails on load.
 */
class PPOcrModelDownloader(
    private val store: PPOcrModelStore,
    private val client: OkHttpClient = defaultClient(),
) {

    /** [onProgress] gets the file label, bytes read and total bytes (0 when the server hides it). */
    suspend fun downloadModels(onProgress: suspend (String, Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        store.ensureDirectory()

        val files = listOf(
            Triple("Detector", PPOcrModelStore.DETECTOR_URL, store.detectorFile),
            Triple("Recognizer", PPOcrModelStore.RECOGNIZER_URL, store.recognizerFile),
            Triple("Dictionary", PPOcrModelStore.DICTIONARY_URL, store.dictionaryFile),
        )

        for ((label, url, target) in files) {
            if (target.exists() && target.length() > 0) {
                onProgress(label, target.length(), target.length())
                continue
            }

            val temp = File(target.parentFile, "${target.name}.tmp")
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (compatible; YabaiOCR)")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code} ($label)")
                    val body = response.body ?: throw IOException("Empty response ($label)")
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        temp.outputStream().use { output ->
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

                if (!temp.exists() || temp.length() == 0L) throw IOException("Empty file for $label")
                if (target.exists()) target.delete()
                if (!temp.renameTo(target)) {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                }
                if (!target.exists() || target.length() == 0L) throw IOException("Could not save $label")
            } finally {
                if (temp.exists()) temp.delete()
            }
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
