package dev.yabai.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.ExifInterface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import dev.yabai.ocr.capture.CameraPhoto
import dev.yabai.ocr.overlay.PickerBlock
import dev.yabai.ocr.overlay.PickerReadingMode
import dev.yabai.ocr.overlay.ResultOverlay
import dev.yabai.ocr.overlay.TextPickerOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    /**
     * R47a: the photo is read like a whole-screen capture. Its paragraphs have to become the blue
     * boxes over the picture, and the picture itself is what the picker draws under them — a photo
     * has its own size and aspect ratio, so it cannot go through the capture's screen-scaled boxes.
     */
    @Test
    fun `the photo opens the picker over the picture`() {
        val service = source("app/src/main/java/dev/yabai/ocr/service/FloatingOcrService.kt")
        val photoRead = service.substringAfter("private suspend fun recogniseImage")
            .substringBefore("private data class PhotoPage")

        assertTrue(
            "the photo still goes straight to the panel, with no boxes over it",
            photoRead.contains("PickerDecision.offersPicker(true"),
        )
        assertTrue(
            "the photo's boxes are not placed on the photo-sized page",
            photoRead.contains("placePhotoBlocks(outcome, page)"),
        )
        assertTrue(
            "the picker is not shown over the photo",
            photoRead.contains("showTextPicker(picker, mode, page.bitmap, retake = true)"),
        )
        assertTrue(
            "going back to the boxes after the panel has no page to draw",
            service.contains("pendingPage = page.bitmap") && service.contains("pendingPage ?: lastCrop"),
        )
        assertTrue(
            "the panel of a photo has no way back to the camera",
            service.contains("canRetakePhoto = true") &&
                service.contains("canRetakePhoto = lastPhoto != null"),
        )
    }

    /** R47a: «Tirar outra foto» belongs to the camera, never to a screen capture. */
    @Test
    fun `the picker offers another photo only for a camera picture`() {
        val overlay = TextPickerOverlay(context)
        val blocks = listOf(PickerBlock(Rect(0, 0, 10, 10)))

        overlay.show(blocks, PickerReadingMode.OFF, page = null, retake = false)
        assertFalse(
            "a screen capture offers a camera that is not its source",
            overlay.texts().any {
                it.text.toString() == context.getString(R.string.camera_retake) &&
                    it.visibility == View.VISIBLE
            },
        )

        overlay.show(blocks, PickerReadingMode.OFF, page = null, retake = true)
        val retake = overlay.texts().firstOrNull {
            it.text.toString() == context.getString(R.string.camera_retake)
        }
        assertNotNull("the photo picker has no retake: ${overlay.texts().map { it.text }}", retake)
        assertEquals(View.VISIBLE, retake!!.visibility)
    }

    /** R47a: the result of a photo says how to take another one. */
    @Test
    fun `the result panel of a photo offers another shot`() {
        val label = context.getString(R.string.camera_retake)
        val withPhoto = ResultOverlay(context).apply {
            showResult(
                text = "日本語",
                meta = "câmera",
                notes = emptyList(),
                showLines = false,
                segmentLines = emptyList(),
                canRepick = false,
                canRetakePhoto = true,
            )
        }
        assertTrue(
            "the result of a photo cannot go back to the camera: ${withPhoto.texts().map { it.text }}",
            withPhoto.texts().any { it.text.toString() == label },
        )

        val withoutPhoto = ResultOverlay(context).apply {
            showResult(
                text = "日本語",
                meta = "captura",
                notes = emptyList(),
                showLines = false,
                segmentLines = emptyList(),
                canRepick = false,
            )
        }
        assertFalse(
            "a screen capture offers a camera that is not its source",
            withoutPhoto.texts().any { it.text.toString() == label },
        )
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun View.texts(): List<TextView> {
        val found = ArrayList<TextView>()
        if (this is TextView) found.add(this)
        if (this is ViewGroup) {
            for (index in 0 until childCount) found.addAll(getChildAt(index).texts())
        }
        return found
    }

    private fun source(path: String): String {
        val found = sequenceOf(File(path), File("../$path"))
            .firstOrNull { it.isFile }?.readText().orEmpty()
        assertTrue("$path not found", found.isNotEmpty())
        return found
    }
}
