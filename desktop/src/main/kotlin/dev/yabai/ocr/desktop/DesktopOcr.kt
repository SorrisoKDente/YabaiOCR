package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.lens.EncodedImage
import dev.yabai.ocr.core.lens.LensClient
import dev.yabai.ocr.core.lens.LensOptions
import dev.yabai.ocr.core.lens.LensResult
import dev.yabai.ocr.desktop.i18n.DesktopStrings
import java.awt.Point
import java.awt.image.BufferedImage

/** The answer plus the engine that produced it, so the window can say which reader read the page. */
data class DesktopOcrAnswer(
    val result: LensResult,
    val engine: DesktopEngineChoice,
)

/**
 * Picks the engine that reads an image, the way the Android `OcrDispatcher` does.
 *
 * Every desktop path — the result window (hand-drawn crop, region capture), the full-screen picker
 * and the hover — goes through [recognize], so the choice in the settings applies to all of them.
 * Before this, the windows called Lens directly and the setting did nothing.
 *
 * [engineOrder] is the rule, kept apart from the OCR calls so it can be tested without a network or
 * a 23 MB model: a fixed choice runs alone, and the automatic mode walks the user's priority list,
 * stopping at the first engine that actually returned text.
 */
object DesktopOcr {

    fun engineOrder(
        choice: DesktopEngineChoice,
        priority: List<DesktopEngineChoice>,
    ): List<DesktopEngineChoice> = when (choice) {
        DesktopEngineChoice.LENS -> listOf(DesktopEngineChoice.LENS)
        DesktopEngineChoice.PP_OCR -> listOf(DesktopEngineChoice.PP_OCR)
        DesktopEngineChoice.AUTO -> {
            val ordered = priority.filter { it != DesktopEngineChoice.AUTO }.distinct()
            ordered.ifEmpty { listOf(DesktopEngineChoice.LENS, DesktopEngineChoice.PP_OCR) }
        }
    }

    /** What the result window prints next to the elapsed time. */
    fun label(engine: DesktopEngineChoice): String = when (engine) {
        DesktopEngineChoice.LENS -> "Google Lens"
        DesktopEngineChoice.PP_OCR -> "PP-OCRv6 (offline)"
        DesktopEngineChoice.AUTO -> "Auto"
    }

    /**
     * Recognises [image] with the configured engine, falling back along the priority list.
     *
     * A reader without text is not an answer: Lens refusing (no network, quota) or PP-OCR without its
     * models installed is recorded and the next engine is tried, so «Automático» still works offline
     * once the models are there. When every engine fails the exception carries all the reasons —
     * «Lens indisponível» alone would hide the one that matters.
     */
    fun recognize(
        image: BufferedImage,
        settings: DesktopSettings = DesktopSettings(),
        /** The image point a hover is asking about; null reads the whole image. */
        focus: Point? = null,
    ): DesktopOcrAnswer {
        val failures = mutableListOf<String>()

        for (engine in engineOrder(settings.engine, settings.enginePriorityList)) {
            val attempt = runCatching {
                when (engine) {
                    DesktopEngineChoice.LENS -> recognizeWithLens(image)
                    DesktopEngineChoice.PP_OCR -> DesktopPPOcr.recognize(image, settings, focus)
                    DesktopEngineChoice.AUTO -> error("AUTO is not an engine")
                }
            }

            attempt.fold(
                onSuccess = { answered ->
                    if (answered.segments.any { it.text.isNotBlank() }) {
                        return DesktopOcrAnswer(answered, engine)
                    }
                    failures += "${label(engine)}: ${DesktopStrings.ocrNoText}"
                },
                onFailure = { error ->
                    failures += "${label(engine)}: ${error.message ?: error.javaClass.simpleName}"
                },
            )
        }

        throw IllegalStateException(DesktopStrings.ocrNoEngine + failures.joinToString(" · "))
    }

    /**
     * One client for the whole app: it owns the cookie jar Lens hands out, and a hover that built a
     * new one twice a second would start every recognition as a stranger.
     */
    private var lensClient: LensClient? = null

    @Synchronized
    private fun lens(): LensClient = lensClient ?: LensClient().also { lensClient = it }

    /**
     * Sends the image to Lens in the smallest form that changes nothing: PNG when it is small, JPEG
     * when PNG made it heavy enough that the upload, not the recognition, is the wait (D83).
     */
    private fun recognizeWithLens(image: BufferedImage): LensResult {
        val encoded = LensPayload.encode(image)
        return lens().recognize(
            EncodedImage(encoded, image.width, image.height),
            LensOptions(language = "ja"),
        )
    }
}
