package dev.yabai.ocr.desktop

import java.awt.Color
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalScreenshotCaptureTest {

    private class FakeTransport(
        private val image: BufferedImage,
        private val available: Boolean = true,
        private val failure: RuntimeException? = null,
    ) : ScreenshotPortalTransport {
        var captures = 0
        var closed = false

        override fun isAvailable(): Boolean = available

        override fun captureWorkspace(): BufferedImage {
            captures++
            failure?.let { throw it }
            return image
        }

        override fun close() {
            closed = true
        }
    }

    private fun image(width: Int, height: Int): BufferedImage =
        BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

    @Test
    fun `screenshot request is non interactive so KDE stores the permission once`() {
        val options = screenshotPortalOptions("yabai_screenshot_7")

        assertEquals("yabai_screenshot_7", options.getValue("handle_token").value)
        assertEquals(false, options.getValue("interactive").value)
        assertEquals(false, options.getValue("modal").value)
    }

    @Test
    fun `screen capture crops the primary screen from the workspace image`() {
        val workspace = image(1920, 1080)
        workspace.setRGB(100, 100, Color.RED.rgb)
        val transport = FakeTransport(workspace)
        val capture = PortalScreenshotCapture(
            transportFactory = { transport },
            virtualBounds = { Rectangle(0, 0, 1920, 1080) },
            primaryBounds = { Rectangle(0, 0, 1280, 800) },
        )

        val screen = capture.captureScreen()

        assertEquals(1280, screen.width)
        assertEquals(800, screen.height)
        assertEquals(Color.RED.rgb, screen.getRGB(100, 100))
        assertEquals(1, transport.captures)
        assertEquals(CaptureAvailability.READY, capture.availability())
    }

    @Test
    fun `region capture scales native workspace pixels back to the requested rectangle`() {
        val workspace = image(2560, 1600)
        workspace.setRGB(200, 100, Color.GREEN.rgb)
        val transport = FakeTransport(workspace)
        val capture = PortalScreenshotCapture(
            transportFactory = { transport },
            virtualBounds = { Rectangle(0, 0, 1280, 800) },
            primaryBounds = { Rectangle(0, 0, 1280, 800) },
        )

        val region = capture.captureRegion(Rectangle(100, 50, 200, 100))

        assertEquals(400, region.width)
        assertEquals(200, region.height)
        assertEquals(Color.GREEN.rgb, region.getRGB(0, 0))
    }

    @Test
    fun `virtual screen offset is removed before cropping`() {
        val workspace = image(3840, 1080)
        workspace.setRGB(1280, 0, Color.BLUE.rgb)
        val transport = FakeTransport(workspace)
        val capture = PortalScreenshotCapture(
            transportFactory = { transport },
            virtualBounds = { Rectangle(-1280, 0, 3840, 1080) },
            primaryBounds = { Rectangle(0, 0, 1280, 800) },
        )

        val screen = capture.captureScreen()

        assertEquals(1280, screen.width)
        assertEquals(Color.BLUE.rgb, screen.getRGB(0, 0))
    }

    @Test
    fun `denied screenshot marks the backend and keeps the error`() {
        val transport = FakeTransport(
            image(10, 10),
            failure = ScreenCaptureException(
                CaptureAvailability.DENIED,
                captureAvailabilityMessage(CaptureAvailability.DENIED),
            ),
        )
        val capture = PortalScreenshotCapture(
            transportFactory = { transport },
            virtualBounds = { Rectangle(0, 0, 10, 10) },
            primaryBounds = { Rectangle(0, 0, 10, 10) },
        )

        val error = assertThrows(ScreenCaptureException::class.java) { capture.captureScreen() }

        assertEquals(CaptureAvailability.DENIED, error.availability)
        assertEquals(CaptureAvailability.DENIED, capture.availability())
    }

    @Test
    fun `security exception from the portal is reported as denied`() {
        val transport = FakeTransport(image(10, 10), failure = SecurityException("denied"))
        val capture = PortalScreenshotCapture(
            transportFactory = { transport },
            virtualBounds = { Rectangle(0, 0, 10, 10) },
            primaryBounds = { Rectangle(0, 0, 10, 10) },
        )

        val error = assertThrows(ScreenCaptureException::class.java) { capture.captureScreen() }

        assertEquals(CaptureAvailability.DENIED, error.availability)
    }

    @Test
    fun `an unavailable portal is reported as unsupported and never captured`() {
        val transport = FakeTransport(image(10, 10), available = false)
        val capture = PortalScreenshotCapture(
            transportFactory = { transport },
            virtualBounds = { Rectangle(0, 0, 10, 10) },
            primaryBounds = { Rectangle(0, 0, 10, 10) },
        )

        assertEquals(CaptureAvailability.UNSUPPORTED, capture.availability())
        val error = assertThrows(ScreenCaptureException::class.java) { capture.captureScreen() }

        assertEquals(CaptureAvailability.UNSUPPORTED, error.availability)
        assertEquals(0, transport.captures)
        assertTrue(transport.closed)
    }

    @Test
    fun `workspace crop outside the virtual screen is rejected`() {
        val workspace = image(10, 10)

        assertThrows(IllegalArgumentException::class.java) {
            cropWorkspace(workspace, Rectangle(0, 0, 10, 10), Rectangle(20, 20, 5, 5))
        }
    }

    @Test
    fun `portal screenshot is read and the returned file is deleted`() {
        val file = File.createTempFile("yabai-portal-screenshot", ".png")
        ImageIO.write(image(4, 3), "png", file)

        val read = readPortalScreenshot(file.toURI().toString())

        assertEquals(4, read.width)
        assertEquals(3, read.height)
        assertFalse(file.exists())
    }
}
