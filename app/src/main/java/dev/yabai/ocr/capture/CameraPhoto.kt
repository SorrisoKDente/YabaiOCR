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
