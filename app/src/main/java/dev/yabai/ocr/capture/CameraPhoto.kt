package dev.yabai.ocr.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File

/**
 * A photo taken with the camera, as a bitmap the engines can read (R46d).
 *
 * The camera app writes the picture the way its sensor saw it and records the turn in the EXIF tag: a
 * page photographed upright arrives 90° on its side, where no engine reads it. The temporary file is
 * deleted on the way out — the photo is read once and never stored.
 */
internal object CameraPhoto {

    /** Reads [file] upright, then deletes it. Null when the picture cannot be decoded. */
    fun loadUpright(file: File): Bitmap? = try {
        val decoded = BitmapFactory.decodeFile(file.path)
        val degrees = if (decoded == null) 0 else turnDegrees(exifOrientation(file))
        if (decoded == null || degrees == 0) decoded else turn(decoded, degrees)
    } finally {
        file.delete()
    }

    /**
     * Deletes every picture an earlier shot left in [directory].
     *
     * The file of a photo is the service's to delete as it reads it, and it does — but a shot whose
     * service never came, or a screen closed before the handoff, leaves a JPEG behind in the app's
     * cache. A new shot sweeps the folder first, so the camera keeps no picture of its own between
     * uses (R48c).
     */
    fun clearLeftovers(directory: File) {
        directory.listFiles()?.forEach { it.delete() }
    }

    /**
     * [file] upright and no side larger than about twice [maxSide], for showing on screen while the
     * engines read the picture (R48a).
     *
     * Unlike [loadUpright] the file is **kept**: the service still has to read it, and deleting it
     * here would leave the photo on screen with nothing to recognise. The sampling is what keeps a
     * twelve-megapixel JPEG from becoming a fifty-megabyte bitmap just to be drawn behind a spinner.
     */
    fun preview(file: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val limit = maxSide.coerceAtLeast(1)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= limit || bounds.outHeight / (sample * 2) >= limit) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            file.path,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        val degrees = turnDegrees(exifOrientation(file))
        return if (degrees == 0) decoded else turn(decoded, degrees)
    }

    /**
     * The clockwise turn the EXIF [orientation] asks for.
     *
     * The mirroring tags are not turned: a camera writes them only for the front lens, and a mirrored
     * page is still read left to right by the engines.
     */
    internal fun turnDegrees(orientation: Int): Int = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    private fun exifOrientation(file: File): Int = try {
        ExifInterface(file.path)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (_: Throwable) {
        // A file without an EXIF header (or one the platform refuses) is read as it is.
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun turn(source: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val turned = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (turned !== source) source.recycle()
        return turned
    }
}
