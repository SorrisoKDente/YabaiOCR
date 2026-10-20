package dev.yabai.ocr.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import dev.yabai.ocr.core.jp.JapaneseText
import dev.yabai.ocr.core.lens.FuriganaMode
import dev.yabai.ocr.core.lens.LensClient
import dev.yabai.ocr.core.lens.LensOptions
import dev.yabai.ocr.core.lens.PixelBox

/**
 * Google Lens, the same engine family the Lens app uses. Highest accuracy on Japanese, including
 * vertical manga text, but it needs a network connection.
 *
 * [furigana] is the only thing here that does not travel to Google: Lens always reports the readings,
 * and this is what the app does with them once they are back.
 */
class LensOcrEngine(
    private val language: String,
    private val maxDimension: Int,
    private val upscaleSmall: Boolean,
    private val furigana: FuriganaMode,
) : OcrEngine {

    override val label: String = "Google Lens"
    override val requiresNetwork: Boolean = true

    private val client = LensClient()

    override suspend fun recognize(bitmap: Bitmap): OcrOutcome {
        val image = ImagePreparer.encode(bitmap, maxDimension, upscaleSmall)
        val result = client.recognizeSuspend(image, LensOptions(language = language), furigana)

        val lines = result.segments.map { it.text }.filter { it.isNotBlank() }

        // Lens reports boxes against the image it was sent, which is a rescaled copy of the crop.
        // The picker draws on the live screen, so the boxes have to come back to crop pixels first.
        val scale = bitmap.width.toFloat() / image.width.toFloat()
        val blocks = result.blocks.mapNotNull { block ->
            val content = JapaneseText.normalize(block.text).trim()
            if (content.isEmpty()) return@mapNotNull null
            OcrBlock(
                text = content,
                lines = block.lines.map { JapaneseText.normalize(it).trim() }.filter { it.isNotEmpty() },
                rect = block.box?.scaledTo(scale, bitmap.width, bitmap.height),
                pieces = block.pieces.mapNotNull { piece ->
                    val pieceText = JapaneseText.normalize(piece.text).trim()
                    if (pieceText.isEmpty()) return@mapNotNull null
                    OcrPiece(pieceText, piece.box?.scaledTo(scale, bitmap.width, bitmap.height))
                },
            )
        }

        // The blocks carry the joined sentences; the flat line list is only for the line-by-line
        // listing, and joining its raw columns for translation is what makes manga translate badly.
        val text = JapaneseText.normalize(
            if (blocks.isEmpty()) lines.joinToString("\n") else blocks.joinToString("\n") { it.text },
        )

        return OcrOutcome(
            text = text,
            lines = lines,
            engineLabel = label,
            language = result.language.ifBlank { null },
            elapsedMs = result.elapsedMs,
            blocks = blocks,
        )
    }

    override fun close() = client.close()
}

/** Scales a box out of the submitted image and into the crop, clipped to it. */
private fun PixelBox.scaledTo(scale: Float, maxWidth: Int, maxHeight: Int): Rect? {
    val left = Math.round(x * scale).coerceIn(0, maxWidth)
    val top = Math.round(y * scale).coerceIn(0, maxHeight)
    val right = Math.round(this.right * scale).coerceIn(left, maxWidth)
    val bottom = Math.round(this.bottom * scale).coerceIn(top, maxHeight)
    if (right <= left || bottom <= top) return null
    return Rect(left, top, right, bottom)
}
