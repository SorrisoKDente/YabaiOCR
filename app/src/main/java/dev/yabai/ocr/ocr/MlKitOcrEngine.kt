package dev.yabai.ocr.ocr

import android.graphics.Bitmap
import android.os.SystemClock
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import dev.yabai.ocr.core.jp.JapaneseText
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device Japanese recogniser. Ships a bundled model, so it works offline and without Play
 * Services — at the cost of being noticeably weaker than Lens on stylised or vertical text.
 */
class MlKitOcrEngine : OcrEngine {

    override val label: String = "ML Kit (offline)"
    override val requiresNetwork: Boolean = false

    private val recognizer: TextRecognizer =
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())

    override suspend fun recognize(bitmap: Bitmap): OcrOutcome {
        val started = SystemClock.elapsedRealtime()
        val text = suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (continuation.isActive) continuation.resume(result)
                }
                .addOnFailureListener { error ->
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
        }

        val lines = text.textBlocks
            .flatMap { block -> block.lines }
            .map { line -> line.text.trim() }
            .filter { it.isNotEmpty() }

        val joined = JapaneseText.normalize(
            if (lines.isEmpty()) text.text.trim() else lines.joinToString("\n"),
        )

        return OcrOutcome(
            text = joined,
            lines = lines,
            engineLabel = label,
            language = text.textBlocks.firstNotNullOfOrNull { it.recognizedLanguage },
            elapsedMs = SystemClock.elapsedRealtime() - started,
        )
    }

    override fun close() {
        recognizer.close()
    }
}
