package dev.yabai.ocr.ocr

import android.graphics.Bitmap
import dev.yabai.ocr.core.lens.EncodedImage
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Prepares a screenshot (or a crop of one) for OCR.
 *
 * The longest side is capped at `maxDimension`, which by default is the long side of an ordinary
 * phone screen (2400), so a full-screen grab goes through untouched. Reducing a whole page is not
 * free: measured against the real endpoint, the kana printed beside a kanji stop being recognised
 * once the page has been brought down to 720x1600, and those readings are half of what this app is
 * for (see `AppSettings.DEFAULT_MAX_DIMENSION` for the numbers).
 *
 * Crops are the other way round: they are often only a couple of hundred pixels tall — those get a
 * modest upscale, which measurably helps on small furigana.
 */
object ImagePreparer {

    private const val UPSCALE_THRESHOLD = 420
    private const val UPSCALE_MAX_FACTOR = 2.0f

    fun encode(source: Bitmap, maxDimension: Int, upscaleSmall: Boolean): EncodedImage {
        val longest = max(source.width, source.height)
        val scale = when {
            longest <= 0 -> 1f
            longest > maxDimension -> maxDimension.toFloat() / longest
            upscaleSmall && longest < UPSCALE_THRESHOLD ->
                minOf(UPSCALE_MAX_FACTOR, max(1f, maxDimension.toFloat() / longest))
            else -> 1f
        }

        val work = if (scale == 1f) {
            source
        } else {
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).roundToInt().coerceAtLeast(1),
                (source.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        }

        val bytes = ByteArrayOutputStream(estimateSize(work)).use { out ->
            work.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }

        if (work !== source) work.recycle()

        return EncodedImage(bytes, work.width, work.height, "image/png")
    }

    private fun estimateSize(bitmap: Bitmap): Int =
        (bitmap.width.toLong() * bitmap.height / 2).coerceIn(64 * 1024, 8 * 1024 * 1024).toInt()
}
