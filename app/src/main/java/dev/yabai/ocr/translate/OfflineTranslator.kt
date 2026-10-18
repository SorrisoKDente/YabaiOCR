package dev.yabai.ocr.translate

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Sentence translation that keeps working with the phone in flight mode.
 *
 * This is ML Kit's on-device model, not a network call: the Japanese↔Portuguese pair is fetched once
 * (around 30 MB) and then runs locally. It is deliberately separate from the dictionary path — the
 * dictionary tells you what each word means and never needs a network, this tells you what the whole
 * sentence says.
 *
 * ML Kit pulls models through Google Play services, so the first translation needs Play services to
 * be present and functional. Everything after that is offline.
 */
class OfflineTranslator(private val context: Context) {

    private val translators = HashMap<String, Translator>()

    /** True when the model for [targetLanguage] is already on the device, so no download is needed. */
    suspend fun isModelReady(targetLanguage: String): Boolean = try {
        translatorFor(targetLanguage).downloadModelIfNeeded(conditions()).awaitTask()
        true
    } catch (t: Throwable) {
        false
    }

    /**
     * Translates [text] from Japanese into [targetLanguage], downloading the model on first use.
     *
     * @throws TranslationUnavailableException when Play services is missing, the download fails, or
     *   the language pair is not supported, so the caller can show something useful.
     */
    suspend fun translate(text: String, targetLanguage: String): String {
        if (text.isBlank()) return ""
        return try {
            withContext(Dispatchers.Default) {
                val translator = translatorFor(targetLanguage)
                translator.downloadModelIfNeeded(conditions()).awaitTask()
                translator.translate(text).awaitTask()
            }
        } catch (t: Throwable) {
            throw TranslationUnavailableException(
                "Tradução offline indisponível: ${t.message ?: t.javaClass.simpleName}",
                t,
            )
        }
    }

    fun close() {
        for (translator in translators.values) runCatching { translator.close() }
        translators.clear()
    }

    private fun translatorFor(targetLanguage: String): Translator {
        val tag = targetLanguage.lowercase().take(2)
        return translators.getOrPut(tag) {
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.JAPANESE)
                .setTargetLanguage(tag)
                .build()
            Translation.getClient(options)
        }
    }

    private fun conditions(): DownloadConditions = DownloadConditions.Builder().build()

    class TranslationUnavailableException(message: String, cause: Throwable?) : Exception(message, cause)

    companion object {
        /** Languages this app offers as a translation target. */
        val TARGETS: List<Pair<String, String>> = listOf(
            "pt" to "Português",
            "en" to "Inglês",
        )

        fun displayName(tag: String): String =
            TARGETS.firstOrNull { it.first == tag.lowercase() }?.second ?: tag
    }
}

/** Bridges a Play services [Task] into a suspending call. */
internal suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(result) }
    addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
    addOnCanceledListener { if (continuation.isActive) continuation.cancel() }
}
