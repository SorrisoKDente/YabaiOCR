package dev.yabai.ocr.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import dev.yabai.ocr.core.jp.JapaneseText
import dev.yabai.ocr.core.layout.BlockColumns
import dev.yabai.ocr.core.layout.ReadingOrder
import dev.yabai.ocr.core.lens.PixelBox
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

        // Two things are wrong with the blocks as reported: the order, and the granularity.
        //
        // The order: ML Kit hands the blocks over in page order — top to bottom, then left to right —
        // which is not how a manga balloon reads: it puts the leftmost column first, when that is the
        // last one. The boxes know the real order, so the blocks are sorted the same way the Lens ones
        // are (R14).
        //
        // The granularity: vertical text is reported one column per block, so a single balloon
        // printed as 「戦う術が」 / 「無い」 arrives as two paragraphs and the picker offers two boxes
        // for one sentence. BlockColumns puts side-by-side columns of the same paragraph back
        // together (R43), and only then is the reading order applied to what is left.
        val found = text.textBlocks
        val grouped = BlockColumns.groups(found.map { boxOf(it.boundingBox) })

        val blocks = grouped.mapNotNull { indices ->
            val orderedLines = indices.flatMap { index -> linesOf(found[index]) }
            val content = JapaneseText.normalize(orderedLines.joinToString(" ") { it.first }).trim()
            if (content.isEmpty()) return@mapNotNull null
            OcrBlock(
                text = content,
                lines = orderedLines.map { it.first },
                rect = unionOf(indices.mapNotNull { found[it].boundingBox }),
                pieces = orderedLines.mapNotNull { (text, box) ->
                    val pieceText = JapaneseText.normalize(text).trim()
                    if (pieceText.isEmpty() || box == null) null else OcrPiece(pieceText, box)
                },
            )
        }
        val orderedLines = blocks.flatMap { it.lines }

        val joined = JapaneseText.normalize(
            if (blocks.isEmpty()) {
                if (lines.isEmpty()) text.text.trim() else lines.joinToString("\n")
            } else {
                blocks.joinToString("\n") { it.text }
            },
        )

        return OcrOutcome(
            text = joined,
            lines = orderedLines.ifEmpty { lines },
            engineLabel = label,
            language = text.textBlocks.firstNotNullOfOrNull { it.recognizedLanguage },
            elapsedMs = SystemClock.elapsedRealtime() - started,
            blocks = blocks,
        )
    }

    /**
     * The lines of one reported block, in reading order, with the box each one occupies.
     *
     * A block with no lines is a paragraph the engine joined itself; its whole text is the only thing
     * there is to offer, which is what the panel shows.
     */
    private fun linesOf(block: Text.TextBlock): List<Pair<String, Rect?>> {
        if (block.lines.isEmpty()) {
            val whole = block.text.trim()
            return if (whole.isEmpty()) emptyList() else listOf(whole to block.boundingBox)
        }
        val order = ReadingOrder.order(block.lines.map { boxOf(it.boundingBox) })
        return order
            .map { block.lines[it] }
            .map { it.text.trim() to it.boundingBox }
            .filter { it.first.isNotEmpty() }
    }

    /** The box a ML Kit element reports, in the shape the rest of the app speaks. */
    private fun boxOf(rect: Rect?): PixelBox? =
        rect?.let { PixelBox(it.left, it.top, it.width(), it.height()) }

    /** The smallest box that contains every box of a paragraph. */
    private fun unionOf(rects: List<Rect>): Rect? {
        val first = rects.firstOrNull() ?: return null
        val result = Rect(first)
        for (rect in rects.drop(1)) result.union(rect)
        return result
    }

    override fun close() {
        recognizer.close()
    }
}
