package dev.yabai.ocr.ocr

import android.graphics.Bitmap
import android.util.Log
import dev.yabai.ocr.AppSettings
import dev.yabai.ocr.EngineChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Chooses a recognition backend and implements the "Lens first, offline as a safety net" behaviour
 * of the AUTO mode.
 *
 * The Lens engine is cached because it owns an OkHttp client and a cookie jar; it is only rebuilt
 * when one of the settings that change the request (language, max dimension, upscaling) move.
 */
class OcrDispatcher(private val settings: AppSettings) {

    private data class LensKey(
        val language: String,
        val maxDimension: Int,
        val upscaleSmall: Boolean,
    )

    private val offlineLazy = lazy { MlKitOcrEngine() }
    private val offlineEngine: MlKitOcrEngine by offlineLazy

    private var lensEngine: LensOcrEngine? = null
    private var lensKey: LensKey? = null

    /** True when the result panel should offer the "tentar offline" action. */
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
                EngineChoice.LENS -> lens().recognize(bitmap)
                EngineChoice.AUTO -> recognizeAuto(bitmap)
            }
        }
    }

    fun close() {
        lensEngine?.close()
        lensEngine = null
        lensKey = null
        if (offlineLazy.isInitialized()) offlineEngine.close()
    }

    // --- internals -----------------------------------------------------------------------------

    private suspend fun recognizeAuto(bitmap: Bitmap): OcrOutcome {
        val outcome = try {
            lens().recognize(bitmap)
        } catch (t: Throwable) {
            Log.w(TAG, "Lens failed, falling back to ML Kit", t)
            return offlineFallback(bitmap)
        }

        return if (outcome.text.isNotBlank()) outcome else offlineFallback(bitmap)
    }

    private suspend fun offlineFallback(bitmap: Bitmap): OcrOutcome =
        offlineEngine.recognize(bitmap).copy(fallback = true)

    private fun lens(): LensOcrEngine {
        val key = LensKey(settings.language, settings.maxDimension, settings.upscaleSmall)
        val current = lensEngine
        if (current != null && lensKey == key) return current

        current?.close()
        val fresh = LensOcrEngine(key.language, key.maxDimension, key.upscaleSmall)
        lensEngine = fresh
        lensKey = key
        return fresh
    }

    companion object {
        private const val TAG = "OcrDispatcher"
    }
}
