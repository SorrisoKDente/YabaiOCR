package dev.yabai.ocr.desktop

import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ScreencastCaptureTest {

    private fun tempToken(): List<java.io.File> {
        val dir = Files.createTempDirectory("yabai-screencast").toFile().apply { deleteOnExit() }
        return listOf(java.io.File(dir, "screencast-tokens.properties").apply {
            writeText("# token\n")
            deleteOnExit()
        })
    }

    private val image = BufferedImage(4, 3, BufferedImage.TYPE_INT_ARGB)

    @Test
    fun `an old jdk is reported as unsupported`() {
        val backend = ScreencastScreenCapture(
            tokenCandidates = emptyList(),
            jdkFeatureVersion = 17,
            captureAction = { image },
        )

        assertEquals(CaptureAvailability.UNSUPPORTED, backend.availability())
        try {
            backend.captureScreen()
            fail("capture must not be attempted with an unsupported JDK")
        } catch (e: ScreenCaptureException) {
            assertEquals(CaptureAvailability.UNSUPPORTED, e.availability)
        }
    }

    @Test
    fun `without a restore token the first capture needs permission`() {
        val backend = ScreencastScreenCapture(
            tokenCandidates = emptyList(),
            jdkFeatureVersion = 21,
            captureAction = { image },
        )

        assertEquals(CaptureAvailability.NEEDS_PERMISSION, backend.availability())
    }

    @Test
    fun `an existing restore token means the session is ready`() {
        val backend = ScreencastScreenCapture(
            tokenCandidates = tempToken(),
            jdkFeatureVersion = 21,
            captureAction = { image },
        )

        assertEquals(CaptureAvailability.READY, backend.availability())
    }

    @Test
    fun `a denied portal is remembered and explained`() {
        val backend = ScreencastScreenCapture(
            tokenCandidates = tempToken(),
            jdkFeatureVersion = 21,
            captureAction = { throw SecurityException("denied by the portal") },
        )

        try {
            backend.captureScreen()
            fail("a denied capture must not return an image")
        } catch (e: ScreenCaptureException) {
            assertEquals(CaptureAvailability.DENIED, e.availability)
            assertTrue(e.message!!.contains("negada"))
        }
        assertEquals(CaptureAvailability.DENIED, backend.availability())
    }

    @Test
    fun `a successful capture after a denial clears the failure`() {
        var action: () -> BufferedImage = { throw SecurityException("denied") }
        val backend = ScreencastScreenCapture(
            tokenCandidates = tempToken(),
            jdkFeatureVersion = 21,
            captureAction = { action() },
        )

        runCatching { backend.captureScreen() }
        assertEquals(CaptureAvailability.DENIED, backend.availability())

        action = { image }
        backend.captureScreen()

        assertEquals(CaptureAvailability.READY, backend.availability())
    }

    @Test
    fun `the region path uses the same screencast guard`() {
        var used: Rectangle? = null
        val backend = ScreencastScreenCapture(
            tokenCandidates = tempToken(),
            jdkFeatureVersion = 21,
            regionAction = { used = it; image },
        )

        backend.captureRegion(Rectangle(3, 4, 5, 6))

        assertEquals(Rectangle(3, 4, 5, 6), used)
    }
}
