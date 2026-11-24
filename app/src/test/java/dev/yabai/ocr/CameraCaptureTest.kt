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
        assertTrue("the screen is not in the manifest", manifest.contains("android:name=\".CameraActivity\""))

        val activity = source("app/src/main/java/dev/yabai/ocr/CameraActivity.kt")
        assertTrue("the screen never asks for the camera", activity.contains("Manifest.permission.CAMERA"))
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
     * R48a: the shot is the decision.
     *
     * The system camera app asks «✕ / ✓» after the picture is taken, and answering ✓ closed this
     * screen and opened another one with the photo. Here the preview takes the shot itself and is
     * then covered by the picture while the engines read it — no confirmation, no second screen.
     */
    @Test
    fun `the picture is taken in place, without a confirmation step`() {
        val activity = source("app/src/main/java/dev/yabai/ocr/CameraActivity.kt")
        assertTrue(
            "the camera app is still the one taking the picture, with its «keep this photo?» step",
            !activity.contains("ActivityResultContracts.TakePicture"),
        )
        assertTrue(
            "the shot is not taken by the preview in this screen",
            activity.contains("ImageCapture.Builder()") &&
                activity.contains("capture.takePicture("),
        )
        assertTrue(
            "the photo is not drawn behind the spinner while it is read",
            activity.contains("CameraPhoto.preview(") &&
                activity.contains("frozenPhoto.setImageBitmap(") &&
                activity.contains("R.string.camera_reading"),
        )
        assertTrue(
            "the screen never waits for the reading, so it would close before the boxes are up",
            activity.contains("FloatingOcrService.ACTION_PHOTO_READY"),
        )

        val service = source("app/src/main/java/dev/yabai/ocr/service/FloatingOcrService.kt")
        assertTrue(
            "the service never announces the reading, so the camera screen would stay behind it",
            service.contains("const val ACTION_PHOTO_READY") &&
                service.contains("sendBroadcast(Intent(ACTION_PHOTO_READY)"),
        )
        assertTrue(
            "the camera screen's loading would be covered by the panel's spinner",
            service.contains("recogniseImage(photo, loadingPanel = false)"),
        )
    }

    /**
     * R48a: the frozen photo is decoded for the screen **before** the handoff.
     *
     * The service deletes the file as it reads it. A screen that displayed the file afterwards would
     * show an empty black page for exactly as long as the engines take.
     */
    @Test
    fun `the photo shown behind the spinner is read without taking the file from the reader`() {
        val directory = File(context.cacheDir, "photos").apply { mkdirs() }
        val file = File(directory, "photo-preview.jpg")
        val photo = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
        file.outputStream().use { photo.compress(Bitmap.CompressFormat.JPEG, 100, it) }

        val shown = CameraPhoto.preview(file, maxSide = 400)

        assertNotNull("the photo could not be decoded for the screen", shown)
        assertTrue("the picture to be recognised was deleted by the screen", file.isFile)
    }

    /**
     * R48c: the default is «not saved».
     *
     * A picture of a page is read once and thrown away: it lives in the app's private cache, never
     * where the gallery would index it, and the service deletes it as it decodes it.
     */
    @Test
    fun `the photo stays in the app's cache and never reaches the gallery`() {
        val activity = source("app/src/main/java/dev/yabai/ocr/CameraActivity.kt")
        assertTrue(
            "the picture is not written to the app's own cache",
            activity.contains("File(cacheDir, \"photos\")"),
        )
        for (shared in listOf("MediaStore", "getExternalFilesDir", "Environment.getExternalStorage", "DCIM")) {
            assertFalse("the picture is written where the gallery can see it: $shared", activity.contains(shared))
        }

        val photo = source("app/src/main/java/dev/yabai/ocr/capture/CameraPhoto.kt")
        assertFalse("the picture is written where the gallery can see it", photo.contains("MediaStore"))
        assertTrue("the file is not deleted once it has been read", photo.contains("file.delete()"))
    }

    /** R48c: a shot that never got read must not stay in the folder for the next one. */
    @Test
    fun `a picture left behind by an earlier shot is swept before the next one`() {
        val directory = File(context.cacheDir, "photos-sweep").apply { mkdirs() }
        val first = File(directory, "photo-1.jpg").apply { writeText("uma foto") }
        val second = File(directory, "photo-2.jpg").apply { writeText("outra foto") }

        CameraPhoto.clearLeftovers(directory)

        assertFalse("a picture from an earlier shot was left in the cache", first.exists())
        assertFalse("a picture from an earlier shot was left in the cache", second.exists())
        assertEquals(0, directory.listFiles()?.size ?: 0)
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

    /**
     * R48d: the picture is turned the way the phone is held, at the shutter and after a turn.
     *
     * `ImageCapture` is built with one rotation and keeps it: read from an unattached view it falls
     * back to `ROTATION_0`, which is right upright and wrong sideways, and turning the phone after
     * the screen opened left the old one behind. A photo turned 90° is read sideways by the engines,
     * which is what put the boxes over the wrong pieces and the text in the wrong order.
     */
    @Test
    fun `the photo turns with the phone, not with the view it was opened in`() {
        val camera = source("app/src/main/java/dev/yabai/ocr/CameraActivity.kt")

        assertFalse(
            "the picture's rotation is read from a view that may not be attached to a window",
            camera.contains("previewView.display?.rotation"),
        )
        assertTrue(
            "the shutter does not take the picture's rotation from the display",
            camera.contains(".setTargetRotation(displayRotation())"),
        )
        assertTrue(
            "turning the phone after the screen opened leaves the picture turned the old way",
            camera.contains("onDisplayChanged") &&
                camera.contains("imageCapture?.targetRotation = displayRotation()"),
        )
        assertTrue(
            "the rotation listener is never removed",
            camera.contains("unregisterDisplayListener"),
        )
    }

    /**
     * R48e: the preview already has the size of the photo.
     *
     * `FILL_CENTER` filled the screen by **cropping** the 4:3 frame, and the picker draws the whole
     * photo fitted to the screen's width: on a portrait phone the picture went from the full height
     * to about 60% of it at the shutter. The whole photo cannot be larger than that on a 9:20 screen
     * without cropping text out of it, so both views letterbox.
     *
     * Read from the source because the preview needs a camera, which this suite has none of; the
     * rectangles are only measurable on a phone.
     */
    @Test
    fun `the preview letterboxes the frame the photo will have`() {
        val camera = source("app/src/main/java/dev/yabai/ocr/CameraActivity.kt")

        assertTrue(
            "the preview crops the frame the photo keeps, so the photo appears smaller than it",
            camera.contains("scaleType = PreviewView.ScaleType.FIT_CENTER"),
        )
        assertFalse(
            "the preview fills the screen by cropping the frame",
            camera.contains("PreviewView.ScaleType.FILL_CENTER"),
        )
        assertTrue(
            "the picture frozen over the preview is fitted another way",
            camera.contains("scaleType = ImageView.ScaleType.FIT_CENTER"),
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
