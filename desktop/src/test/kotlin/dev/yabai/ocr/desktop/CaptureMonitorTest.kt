package dev.yabai.ocr.desktop

import java.awt.Point
import java.awt.Rectangle
import java.awt.image.BufferedImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Choosing the monitor for the full-screen capture and the hand-drawn crop.
 *
 * The three pieces below are what can be decided without a screen: the stored preference, the label
 * the settings window shows, and which backend call a monitor maps to.
 */
class CaptureMonitorTest {

    private val primary = CaptureDisplay(index = 0, label = "DP-1", bounds = Rectangle(0, 0, 1920, 1080))
    private val secondary = CaptureDisplay(index = 1, label = "HDMI-A-1", bounds = Rectangle(1920, 0, 1280, 720))
    private val displays = listOf(primary, secondary)

    // --- preference resolution ---

    @Test
    fun `the primary preference picks the first display`() {
        assertEquals(primary, resolveCaptureDisplay(CAPTURE_MONITOR_PRIMARY, displays))
    }

    @Test
    fun `the mouse preference follows the pointer to the second monitor`() {
        val chosen = resolveCaptureDisplay(CAPTURE_MONITOR_MOUSE, displays, Point(2500, 400))

        assertEquals(secondary, chosen)
    }

    @Test
    fun `the mouse preference uses the primary display when the pointer is unknown`() {
        assertEquals(primary, resolveCaptureDisplay(CAPTURE_MONITOR_MOUSE, displays, pointer = null))
    }

    @Test
    fun `a stored display index picks that monitor`() {
        val chosen = resolveCaptureDisplay(captureMonitorDisplayPreference(1), displays)

        assertEquals(secondary, chosen)
    }

    @Test
    fun `an index that no longer exists falls back to the primary display`() {
        assertEquals(primary, resolveCaptureDisplay(captureMonitorDisplayPreference(4), displays))
        assertEquals(primary, resolveCaptureDisplay("DISPLAY:not-a-number", displays))
    }

    @Test
    fun `no display at all resolves to nothing`() {
        assertNull(resolveCaptureDisplay(CAPTURE_MONITOR_PRIMARY, emptyList()))
    }

    // --- dropdown label ---

    @Test
    fun `the label names the chosen monitor and its size`() {
        val label = captureMonitorLabel(
            preference = captureMonitorDisplayPreference(1),
            displays = displays,
            primaryLabel = "Primary",
            mouseLabel = "Under pointer",
        )

        assertEquals("HDMI-A-1 (1280×720)", label)
    }

    @Test
    fun `the label shows the special choices and degrades to primary`() {
        assertEquals("Under pointer", captureMonitorLabel(CAPTURE_MONITOR_MOUSE, displays, "Primary", "Under pointer"))
        assertEquals("Primary", captureMonitorLabel(CAPTURE_MONITOR_PRIMARY, displays, "Primary", "Under pointer"))
        // A monitor that was unplugged must not leave the button empty.
        assertEquals("Primary", captureMonitorLabel("DISPLAY:9", displays, "Primary", "Under pointer"))
    }

    // --- backend call ---

    private class RecordingBackend(private val primaryBounds: Rectangle) : ScreenCaptureBackend {
        val calls = mutableListOf<String>()
        private val image = BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB)

        override fun availability(): CaptureAvailability = CaptureAvailability.READY
        override fun screenBounds(): Rectangle = primaryBounds
        override fun captureScreen(): BufferedImage {
            calls += "screen"
            return image
        }

        override fun captureRegion(rect: Rectangle): BufferedImage {
            calls += "region ${rect.x},${rect.y} ${rect.width}x${rect.height}"
            return image
        }
    }

    @Test
    fun `a monitor equal to the primary keeps the whole-screen capture`() {
        val backend = RecordingBackend(Rectangle(0, 0, 1920, 1080))

        backend.captureMonitor(Rectangle(0, 0, 1920, 1080))

        assertEquals(listOf("screen"), backend.calls)
    }

    @Test
    fun `another monitor is captured by its rectangle`() {
        val backend = RecordingBackend(Rectangle(0, 0, 1920, 1080))

        backend.captureMonitor(Rectangle(1920, 0, 1280, 720))

        assertEquals(listOf("region 1920,0 1280x720"), backend.calls)
    }

    // --- KWin transport: workspace crop for the primary, area for the rest ---

    private class FakeTransport(private val workspace: BufferedImage, private val area: BufferedImage) :
        KWinScreenshotTransport {
        val calls = mutableListOf<String>()

        override fun isAvailable(): Boolean = true

        override fun captureWorkspace(): BufferedImage {
            calls += "workspace"
            return workspace
        }

        override fun captureArea(rect: Rectangle): BufferedImage {
            calls += "area ${rect.x},${rect.y} ${rect.width}x${rect.height}"
            return area
        }

        override fun close() {}
    }

    @Test
    fun `kwin crops the workspace for the primary monitor and asks for the area of any other`() {
        val primaryBounds = Rectangle(0, 0, 1920, 1080)
        val transport = FakeTransport(
            workspace = BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB),
            area = BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB),
        )
        val capture = KWinScreenshotCapture(
            transportFactory = { transport },
            virtualBounds = { primaryBounds },
            primaryBounds = { primaryBounds },
        )

        assertEquals(1920, capture.captureMonitor(primaryBounds).width)
        assertEquals(listOf("workspace"), transport.calls)

        assertEquals(1280, capture.captureMonitor(Rectangle(1920, 0, 1280, 720)).width)
        assertEquals(listOf("workspace", "area 1920,0 1280x720"), transport.calls)
    }

    @Test
    fun `a rectangle no device owns matches nothing, so the primary capture is used`() {
        val bounds = mapOf(
            "DP-1" to Rectangle(0, 0, 1920, 1080),
            "HDMI-A-1" to Rectangle(1920, 0, 1280, 720),
        )

        assertEquals("HDMI-A-1", deviceWithBounds(bounds.keys.toList(), Rectangle(1920, 0, 1280, 720)) { bounds[it] })
        assertNull(deviceWithBounds(bounds.keys.toList(), Rectangle(9_999, 9_999, 10, 10)) { bounds[it] })
        assertTrue(bounds.isNotEmpty())
    }
}
