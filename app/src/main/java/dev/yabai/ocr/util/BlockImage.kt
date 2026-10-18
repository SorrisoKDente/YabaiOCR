package dev.yabai.ocr.util

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * The piece of the captured frame that a piece of text was recognised from.
 *
 * A whole-screen capture is picked from in boxes, and showing the whole screen next to one line of
 * text would be showing a page at thumbnail size — useless for the one thing the image is there for,
 * which is checking whether the recogniser got a kanji right. So the picture that goes beside the
 * text is the piece the text came from; for a region capture that piece is the whole crop, and for a
 * picked box it is that box.
 */
object BlockImage {

    /**
     * Cuts [rect] out of [crop], in the crop's own pixels.
     *
     * A box that is missing, empty or completely off the frame — a rounding accident, a frame of
     * another size — is the whole frame: an image the user did not ask for beats no image at all
     * beside the text. A box that only hangs off the edge is clipped to what is really there.
     */
    fun from(crop: Bitmap, rect: Rect?): Bitmap {
        if (rect == null || rect.isEmpty) return crop
        val box = Rect(rect)
        if (!box.intersect(0, 0, crop.width, crop.height)) return crop
        return runCatching { Bitmap.createBitmap(crop, box.left, box.top, box.width(), box.height()) }
            .getOrDefault(crop)
    }
}
