package dev.yabai.ocr.ocr

import android.graphics.Bitmap
import android.util.Log
import dev.yabai.ocr.AppSettings
import dev.yabai.ocr.EngineChoice
import dev.yabai.ocr.UsageTracker
import dev.yabai.ocr.core.lens.FuriganaMode
import dev.yabai.ocr.core.lens.LensException
import dev.yabai.ocr.core.ppocr.PPOcrEngine
import dev.yabai.ocr.core.ppocr.PPOcrModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Chooses a recognition backend and implements the "Lens first, offline as a safety net" behaviour
 * of the AUTO mode.
 *
 * The Lens engine is cached because it owns an OkHttp client and a cookie jar; it is only rebuilt
 * when one of the settings it was built from (language, max dimension, upscaling, and what to do
 * with furigana) moves. Only the first three change the request; the last one changes how the
 * response is read, and it is in the key all the same so that a toggle can never be honoured late.
 *
 * Every Lens attempt is reported to [usage], which is the only place that can know how much we have
 * been calling Google — the API itself exposes no quota.
 */
class OcrDispatcher(
    private val settings: AppSettings,
    private val usage: UsageTracker,
) {

    private data class LensKey(
        val language: String,
        val maxDimension: Int,
        val upscaleSmall: Boolean,
        val furigana: FuriganaMode,
    )

    private val offlineLazy = lazy { MlKitOcrEngine() }
    private val offlineEngine: MlKitOcrEngine by offlineLazy

    private var lensEngine: LensOcrEngine? = null
    private var lensKey: LensKey? = null

    /** True when the error card should offer the "tentar offline" way back. */
    fun offlineAvailable(forceOffline: Boolean): Boolean =
        !forceOffline && settings.engine != EngineChoice.MLKIT

    /**
     * Recognises [bitmap] with the engine the user chose (or, when [forceOffline] is set, the
     * offline one regardless of the setting).
     *
     * Runs on a background dispatcher: both engines block.
     */
    suspend fun recognize(bitmap: Bitmap, forceOffline: Boolean = false): OcrOutcome {
        val choice = if (forceOffline) EngineChoice.MLKIT else settings.engine
        return withContext(Dispatchers.Default) {
            when (choice) {
                EngineChoice.MLKIT -> offlineEngine.recognize(bitmap)
                EngineChoice.LENS -> recognizeWithLens(bitmap)
                EngineChoice.PP_OCR -> recognizeWithPpOcr(bitmap)
                EngineChoice.AUTO -> recognizeAuto(bitmap)
            }
        }
    }

    fun close() {
        lensEngine?.close()
        lensEngine = null
        lensKey = null
        runCatching { ppOcrEngineInstance?.close() }
        ppOcrEngineInstance = null
        if (offlineLazy.isInitialized()) offlineEngine.close()
    }

    // --- internals -----------------------------------------------------------------------------

    private suspend fun recognizeAuto(bitmap: Bitmap): OcrOutcome {
        val priorityList = settings.enginePriorityList
        var lastWarning: String? = null

        for (engineChoice in priorityList) {
            try {
                val outcome = when (engineChoice) {
                    EngineChoice.LENS -> recognizeWithLens(bitmap)
                    EngineChoice.PP_OCR -> {
                        val store = PPOcrModelStore(settings.ppOcrDir)
                        if (store.isAvailable()) recognizeWithPpOcr(bitmap) else null
                    }
                    EngineChoice.MLKIT -> offlineEngine.recognize(bitmap)
                    else -> null
                }
                if (outcome != null && outcome.text.isNotBlank()) {
                    return outcome
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Engine $engineChoice failed in AUTO mode", t)
                if (engineChoice == EngineChoice.LENS) {
                    lastWarning = rateLimitWarning(t)
                }
            }
        }

        return offlineEngine.recognize(bitmap).copy(fallback = true, warning = lastWarning)
    }

    private suspend fun recognizeWithPpOcr(bitmap: Bitmap): OcrOutcome {
        val store = PPOcrModelStore(settings.ppOcrDir)
        if (!store.isAvailable()) {
            return OcrOutcome(
                text = "",
                lines = emptyList(),
                engineLabel = PP_OCR_LABEL,
                language = "ja",
                elapsedMs = 0,
                warning = "Modelos do PP-OCRv6 não instalados.",
            )
        }

        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) {
            return OcrOutcome(
                text = "",
                lines = emptyList(),
                engineLabel = PP_OCR_LABEL,
                language = "ja",
                elapsedMs = 0,
            )
        }

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val result = runCatching {
            val engine = ppOcrEngine() ?: throw IllegalStateException("Não foi possível carregar as sessões ONNX do PP-OCRv6.")
            engine.recognizePixels(pixels, width, height)
        }

        val outcome = result.getOrElse { t ->
            Log.e(TAG, "Error in PP-OCR recognition", t)
            return OcrOutcome(
                text = "",
                lines = emptyList(),
                engineLabel = PP_OCR_LABEL,
                language = "ja",
                elapsedMs = 0,
                warning = "Falha no PP-OCRv6: ${t.message ?: t.javaClass.simpleName}",
            )
        }

        val blocks = outcome.blocks.map { b ->
            OcrBlock(
                text = b.text,
                lines = b.lines,
                rect = android.graphics.Rect(b.left, b.top, b.right, b.bottom),
                pieces = b.pieces.map { p ->
                    OcrPiece(
                        text = p.text,
                        rect = android.graphics.Rect(p.left, p.top, p.right, p.bottom),
                    )
                }
            )
        }

        return OcrOutcome(
            text = outcome.text,
            lines = outcome.lines,
            engineLabel = PP_OCR_LABEL,
            language = "ja",
            elapsedMs = outcome.elapsedMs,
            blocks = blocks,
        )
    }

    @Volatile
    private var ppOcrEngineInstance: PPOcrEngine? = null

    @Synchronized
    private fun ppOcrEngine(): PPOcrEngine? {
        ppOcrEngineInstance?.let { return it }
        val store = PPOcrModelStore(settings.ppOcrDir)
        if (!store.isAvailable()) return null
        return runCatching {
            PPOcrEngine(store.detectorFile, store.recognizerFile, store.dictionaryFile).also {
                ppOcrEngineInstance = it
            }
        }.getOrNull()
    }

    private suspend fun offlineFallback(bitmap: Bitmap, warning: String?): OcrOutcome =
        offlineEngine.recognize(bitmap).copy(fallback = true, warning = warning)

    /**
     * Turns a refusal from Google into something the result panel can explain, because
     * "Lens indisponível" alone would hide the one signal that actually tells the user why.
     */
    private fun rateLimitWarning(error: Throwable): String? {
        val status = (error as? LensException)?.httpStatus ?: return null
        return when (status) {
            HTTP_TOO_MANY_REQUESTS -> "Limite de uso do Google Lens atingido (HTTP 429)."
            HTTP_FORBIDDEN -> "O Google recusou a requisição (HTTP 403)."
            else -> null
        }
    }

    private suspend fun recognizeWithLens(bitmap: Bitmap): OcrOutcome = try {
        val outcome = lens().recognize(bitmap)
        usage.recordLensCall(ok = true, httpStatus = 200)
        outcome
    } catch (t: Throwable) {
        val lensError = t as? LensException
        usage.recordLensCall(
            ok = false,
            httpStatus = lensError?.httpStatus ?: -1,
            retryAfterSeconds = lensError?.retryAfterSeconds,
            message = t.message,
        )
        throw t
    }

    private fun lens(): LensOcrEngine {
        val key = LensKey(
            language = settings.language,
            maxDimension = settings.maxDimension,
            upscaleSmall = settings.upscaleSmall,
            furigana = if (settings.furiganaInParentheses) {
                FuriganaMode.PARENTHESES
            } else {
                FuriganaMode.REMOVE
            },
        )
        val current = lensEngine
        if (current != null && lensKey == key) return current

        current?.close()
        val fresh = LensOcrEngine(key.language, key.maxDimension, key.upscaleSmall, key.furigana)
        lensEngine = fresh
        lensKey = key
        return fresh
    }

    companion object {
        private const val TAG = "OcrDispatcher"
        private const val PP_OCR_LABEL = "PP-OCRv6 (Offline)"
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_FORBIDDEN = 403
    }
}
