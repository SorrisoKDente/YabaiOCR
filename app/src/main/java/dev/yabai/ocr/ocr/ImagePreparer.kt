package dev.yabai.ocr.ocr

import android.graphics.Bitmap
import dev.yabai.ocr.core.lens.EncodedImage
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Prepares a screenshot (or a crop of one) for OCR.
 *
 * Lens works best around 1200 px on the longest side, but phone full-screen grabs are 1080x2400,
 * so we downscale those. Crops, on the other hand, are often only a couple of hundred pixels tall
 * — those get a modest upscale, which measurably helps on small furigana.
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
