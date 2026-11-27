package dev.yabai.ocr.desktop

import dev.yabai.ocr.core.lens.LensBlock
import dev.yabai.ocr.core.lens.LensResult
import dev.yabai.ocr.core.lens.LensSegment
import dev.yabai.ocr.core.lens.PixelBox
import dev.yabai.ocr.core.ppocr.PPOcrBlock
import dev.yabai.ocr.core.ppocr.PPOcrEngine
import dev.yabai.ocr.core.ppocr.PPOcrModelStore
import dev.yabai.ocr.core.ppocr.PPOcrOutcome
import dev.yabai.ocr.desktop.i18n.DesktopStrings
import java.awt.Point
import java.awt.image.BufferedImage

/**
 * The offline engine on the desktop: PP-OCRv6_manga over ONNX Runtime, in the `:core`
 * implementation the Android app can share.
 *
 * The sessions are kept after the first call because opening them costs reading ~23 MB from disk,
 * and a hover that runs twice a second cannot pay that every time. The answer is translated into
 * [LensResult] — the shape every desktop caller already speaks — so the result window, the
 * full-screen picker and the hover do not need to know which engine read the page.
 */
object DesktopPPOcr {

    private var cached: PPOcrEngine? = null

    /**
     * The `IntArray` the image is copied into. A hover read of a full monitor is ~8 MB at 1080p and
     * the continuous hover runs one every few hundred milliseconds: reusing the buffer takes that
     * garbage out of the young generation (D99). Safe because [recognize] is `@Synchronized`.
     */
    private val pixelBuffer = PixelBuffer()

    fun store(settings: DesktopSettings = DesktopSettings()): PPOcrModelStore =
        PPOcrModelStore(settings.ppOcrDir)

    fun isInstalled(settings: DesktopSettings = DesktopSettings()): Boolean = store(settings).isAvailable()

    /** Drops the sessions; a settings window that just installed the files calls this. */
    @Synchronized
    fun close() {
        cached?.let { runCatching { it.close() } }
        cached = null
    }

    /**
     * [focus] is the image point the answer is about — the hover's pointer. With it the recogniser
     * only reads the lines around that point; without it the whole image is read, as the result
     * window and the picker need.
     */
    @Synchronized
    fun recognize(
        image: BufferedImage,
        settings: DesktopSettings = DesktopSettings(),
        focus: Point? = null,
    ): LensResult {
        val models = store(settings)
        if (!models.isAvailable()) throw IllegalStateException(DesktopStrings.ppOcrMissingModels)

        val engine = cached ?: PPOcrEngine(
            models.detectorFile,
            models.recognizerFile,
            models.dictionaryFile,
        ).also { cached = it }

        val width = image.width
        val height = image.height
        if (width <= 0 || height <= 0) {
            return LensResult(language = "ja", segments = emptyList(), blocks = emptyList(), elapsedMs = 0)
        }

        val pixels = pixelBuffer.of(width * height)
        image.getRGB(0, 0, width, height, pixels, 0, width)
        val focusBox = focus?.let { PixelBox(it.x, it.y, 1, 1) }
        return toLensResult(engine.recognizePixels(pixels, width, height, focus = focusBox))
    }

    /**
     * One recognition, in the shape the rest of the desktop reads.
     *
     * Segments are the detected **lines** — the columns of a vertical balloon, the rows of a
     * horizontal one — because that is what hover hit-tests against and what the panel lists; the
     * block keeps the joined sentence. Without lines (nothing detected) the block itself is the
     * segment, so nothing is lost.
     */
    fun toLensResult(outcome: PPOcrOutcome): LensResult {
        val blocks = outcome.blocks.map { block -> block.toLensBlock() }
        val segments = blocks.flatMap { block ->
            block.pieces.ifEmpty { listOf(LensSegment(block.text, block.box)) }
        }
        return LensResult(
            language = "ja",
            segments = segments,
            blocks = blocks,
            elapsedMs = outcome.elapsedMs,
        )
    }

    private fun PPOcrBlock.toLensBlock(): LensBlock = LensBlock(
        text = text,
        lines = lines,
        box = PixelBox(left, top, right - left, bottom - top),
        pieces = pieces.map {
            LensSegment(
                it.text,
                PixelBox(it.left, it.top, it.width, it.height),
                printed = it.printed,
            )
        },
    )
}
