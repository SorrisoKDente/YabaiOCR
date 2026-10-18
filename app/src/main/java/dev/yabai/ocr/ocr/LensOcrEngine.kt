package dev.yabai.ocr.ocr

import android.graphics.Bitmap
import dev.yabai.ocr.core.jp.JapaneseText
import dev.yabai.ocr.core.lens.LensClient
import dev.yabai.ocr.core.lens.LensOptions

/**
 * Google Lens, the same engine family the Lens app uses. Highest accuracy on Japanese, including
 * vertical manga text, but it needs a network connection.
 */
class LensOcrEngine(
    private val language: String,
    private val maxDimension: Int,
    private val upscaleSmall: Boolean,
) : OcrEngine {

    override val label: String = "Google Lens"
    override val requiresNetwork: Boolean = true

    private val client = LensClient()

    override suspend fun recognize(bitmap: Bitmap): OcrOutcome {
        val image = ImagePreparer.encode(bitmap, maxDimension, upscaleSmall)
        val result = client.recognizeSuspend(image, LensOptions(language = language))

        val lines = result.segments.map { it.text }.filter { it.isNotBlank() }
        val text = JapaneseText.normalize(lines.joinToString("\n"))

        return OcrOutcome(
            text = text,
            lines = lines,
            engineLabel = label,
            language = result.language.ifBlank { null },
            elapsedMs = result.elapsedMs,
        )
    }

    override fun close() = client.close()
}
