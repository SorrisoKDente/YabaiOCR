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

        // ML Kit reports boxes in the bitmap's own pixels, which is already the space the picker
        // needs a crop-relative box in. A block is already a paragraph, so, unlike Lens, nothing has
        // to be merged here; the lines inside one are just wrapped text and are joined back together
        // the same way Lens's columns are.
        val blocks = text.textBlocks.mapNotNull { block ->
            val blockLines = block.lines.map { it.text.trim() }.filter { it.isNotEmpty() }
            val content = JapaneseText.normalize(
                if (blockLines.isEmpty()) block.text.trim() else blockLines.joinToString(" "),
            ).trim()
            if (content.isEmpty()) return@mapNotNull null
            OcrBlock(
                text = content,
                lines = blockLines,
                rect = block.boundingBox,
                pieces = block.lines.mapNotNull { line ->
                    val pieceText = JapaneseText.normalize(line.text.trim()).trim()
                    val box = line.boundingBox
                    if (pieceText.isEmpty() || box == null) null else OcrPiece(pieceText, box)
                },
            )
        }

        val joined = JapaneseText.normalize(
            if (blocks.isEmpty()) {
                if (lines.isEmpty()) text.text.trim() else lines.joinToString("\n")
            } else {
                blocks.joinToString("\n") { it.text }
            },
        )

        return OcrOutcome(
            text = joined,
            lines = lines,
            engineLabel = label,
            language = text.textBlocks.firstNotNullOfOrNull { it.recognizedLanguage },
            elapsedMs = SystemClock.elapsedRealtime() - started,
            blocks = blocks,
        )
    }

    override fun close() {
        recognizer.close()
    }
}
