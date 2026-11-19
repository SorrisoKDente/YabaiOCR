package dev.yabai.ocr.desktop

/**
 * One pixels buffer, reused by consecutive reads of the same image size.
 *
 * A hover crop is the whole monitor height, so one read asked for ~8 MB of `IntArray` at 1080p and
 * the continuous hover asked for two of those at a time — tens of megabytes a second of garbage for
 * a buffer whose contents are overwritten before anyone looks at them. The array is kept and handed
 * back whenever the size matches, exactly like a scratch buffer: the caller fills it and reads it
 * before asking again, and nothing else holds it.
 *
 * Not thread safe on purpose: the only user, [DesktopPPOcr.recognize], is `@Synchronized`, so the
 * buffer is never shared by two reads.
 */
internal class PixelBuffer {

    private var pixels = IntArray(0)

    /** The buffer for an image of [size] pixels, grown when the image size changed. */
    fun of(size: Int): IntArray {
        if (size < 0) throw IllegalArgumentException("negative pixel count: $size")
        if (pixels.size != size) pixels = IntArray(size)
        return pixels
    }

    /** How many pixels the reused buffer holds; 0 before the first read. */
    val size: Int get() = pixels.size
}
