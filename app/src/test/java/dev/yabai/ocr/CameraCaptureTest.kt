package dev.yabai.ocr

import android.content.Context
import android.graphics.Bitmap
import android.media.ExifInterface
import dev.yabai.ocr.capture.CameraPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * The camera on the floating bubble (R46d): a photo taken with the camera app is read like a screen
 * capture, without the screen, and without a file left on the device.
 *
 * The half that cannot be measured here is the camera app itself. What is checked is what this app
 * decides: the menu offers the action and asks for it, the EXIF turn is honoured (a page photographed
 * upright arrives 90° on its side, where no engine reads it), and the temporary file is deleted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "pt-rBR")
class CameraCaptureTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `the EXIF turn is honoured and the mirroring ones are not`() {
        assertEquals(90, CameraPhoto.turnDegrees(ExifInterface.ORIENTATION_ROTATE_90))
        assertEquals(180, CameraPhoto.turnDegrees(ExifInterface.ORIENTATION_ROTATE_180))
        assertEquals(270, CameraPhoto.turnDegrees(ExifInterface.ORIENTATION_ROTATE_270))
        assertEquals(0, CameraPhoto.turnDegrees(ExifInterface.ORIENTATION_NORMAL))
        assertEquals(0, CameraPhoto.turnDegrees(ExifInterface.ORIENTATION_FLIP_HORIZONTAL))
    }

    /** The photo is read once and never stored: the gallery must not gain a picture (R46d). */
    @Test
    fun `the photo file is deleted once it has been read`() {
        val directory = File(context.cacheDir, "photos").apply { mkdirs() }
        val file = File(directory, "photo-test.jpg")
        val photo = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
        file.outputStream().use { photo.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        assertTrue("the test did not write the photo", file.isFile)

        CameraPhoto.loadUpright(file)

        assertFalse("the photo is still in the app's cache", file.exists())
    }

    /**
     * The permission, the activity and the handoff are the parts a revert would silently drop: the
     * menu would still show a camera that opens nothing (or a screen-capture prompt).
     */
    @Test
    fun `the camera asks for its permission and hands the file to the running service`() {
        val manifest = source("app/src/main/AndroidManifest.xml")
        assertTrue("the app never declares the camera", manifest.contains("android.permission.CAMERA"))
        assertTrue("the doorway is not in the manifest", manifest.contains("android:name=\".CameraActivity\""))
        assertTrue(
            "the camera app is handed a file:// Uri, which the platform refuses",
            manifest.contains("androidx.core.content.FileProvider"),
        )

        val activity = source("app/src/main/java/dev/yabai/ocr/CameraActivity.kt")
        assertTrue("the doorway never asks for the camera", activity.contains("Manifest.permission.CAMERA"))
        assertTrue(
            "the photo is not handed to the service",
            activity.contains("FloatingOcrService.ACTION_PHOTO") &&
                activity.contains("FloatingOcrService.EXTRA_PHOTO_PATH"),
        )

        val service = source("app/src/main/java/dev/yabai/ocr/service/FloatingOcrService.kt")
        assertTrue("the service ignores a photo", service.contains("ACTION_PHOTO ->"))
        assertTrue(
            "the photo is not read through the same pipeline as a capture",
            service.contains("CameraPhoto.loadUpright(file)") &&
                service.contains("dispatcher.recognize(photo"),
        )
        assertTrue(
            "the camera should not need the screen-capture session",
            !service.substringAfter("override fun onCameraRequested()")
                .substringBefore("override fun onRegionConfirmed")
                .contains("requestCapture"),
        )
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun source(path: String): String {
        val found = sequenceOf(File(path), File("../$path"))
            .firstOrNull { it.isFile }?.readText().orEmpty()
        assertTrue("$path not found", found.isNotEmpty())
        return found
    }
}
