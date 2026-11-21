package dev.yabai.ocr

import android.graphics.Bitmap
import dev.yabai.ocr.service.FloatingOcrService
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The photo of a camera read is held only while its panel is open (R46d).
 *
 * The whole screen is dropped when the panel closes; the photo is the same size and has the same
 * reason to be held — a retry of a result that came from the camera. Closing the panel without it
 * left a full photo in memory until the service died.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResultMemoryTest {

    private val service: FloatingOcrService by lazy {
        Robolectric.buildService(FloatingOcrService::class.java).create().get()
    }

    @Test
    fun `closing the panel drops the photo with the capture`() {
        val photo = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
        service.hold("lastCrop", photo)
        service.hold("lastPhoto", photo)

        service.onResultDismissed()

        assertNull("the panel is closed and the capture is still held", service.held("lastCrop"))
        assertNull("the panel is closed and the photo is still held", service.held("lastPhoto"))
    }

    // The fields are private and there is no other door into the service's memory: the test sets
    // the state the panel leaves behind and asks what survives its close.

    private fun FloatingOcrService.hold(name: String, value: Any) {
        javaClass.getDeclaredField(name).apply { isAccessible = true }.set(this, value)
    }

    private fun FloatingOcrService.held(name: String): Any? =
        javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this)
}
