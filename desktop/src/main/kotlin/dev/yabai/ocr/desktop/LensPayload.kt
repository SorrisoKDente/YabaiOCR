package dev.yabai.ocr.desktop

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * Turns a screenshot into the bytes Lens receives.
 *
 * Lens is not what makes a manga page slow on this Deck: the **upload** is. Measured here (ROADMAP
 * D83) on 1280×800 images — same endpoint, same client, only the encoder changed:
 *
 * | image | PNG | Lens |
 * | --- | --- | --- |
 * | desktop screen, light text | 149 KB | 688–793 ms |
 * | a 700 px strip of a manga page | 852 KB | 2 022–2 273 ms |
 * | the whole manga page | 1 540 KB | 3 220–4 498 ms |
 *
 * The desktop screen had *more* segments than the manga (46 against 65) and was still five times
 * faster, so this is bytes on the wire, not text on the page. Photographs and artwork are exactly
 * what PNG cannot compress.
 *
 * So the PNG stays for the common case — a UI screen is small, lossless, and answers in under a
 * second — and only an image PNG bloathed goes as JPEG. Measured on that manga page: 299 KB instead
 * of 1 540 KB, 916–1 183 ms instead of 3 370–3 541 ms, and **no segment lost** against four PNG runs
 * of the same page (it found one extra standalone 「ツ」 in the artwork). Quality 0.75 was rejected:
 * it turned 「プレイして」 into 「ブレイして」 and 「NEW」 into 「HEWE」 — exactly the damage the precision
 * rule forbids.
 */
internal object LensPayload {

    /** Above this the PNG is worth re-encoding; it is where the upload starts costing seconds here. */
    const val PNG_LIMIT_BYTES = 400 * 1024

    /** High enough to keep glyph edges intact at 0.75–1× the screen resolution (see the doc above). */
    const val JPEG_QUALITY = 0.85f

    /** Bytes to send for [image]: PNG when it is small, JPEG when PNG made it heavy. */
    fun encode(image: BufferedImage): ByteArray {
        val png = encodePng(image)
        if (png.size <= PNG_LIMIT_BYTES) return png
        val jpeg = runCatching { encodeJpeg(image, JPEG_QUALITY) }.getOrNull() ?: return png
        // A page of flat colour can be smaller as PNG even when it is big; never make it worse.
        return if (jpeg.size < png.size) jpeg else png
    }

    fun encodePng(image: BufferedImage): ByteArray = ByteArrayOutputStream().use { out ->
        check(ImageIO.write(image, "png", out)) { "PNG encoder unavailable" }
        out.toByteArray()
    }

    fun encodeJpeg(image: BufferedImage, quality: Float = JPEG_QUALITY): ByteArray {
        val out = ByteArrayOutputStream()
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        try {
            val params = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = quality
            }
            ImageIO.createImageOutputStream(out).use { stream ->
                writer.output = stream
                // JPEG has no alpha channel: handing the writer an ARGB image throws.
                val rgb = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
                val graphics = rgb.createGraphics()
                try {
                    graphics.drawImage(image, 0, 0, null)
                } finally {
                    graphics.dispose()
                }
                writer.write(null, IIOImage(rgb, null, null), params)
            }
        } finally {
            writer.dispose()
        }
        return out.toByteArray()
    }
}
