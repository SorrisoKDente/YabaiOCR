package dev.yabai.ocr.desktop

import java.awt.Rectangle
import java.awt.image.BufferedImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which capture transport served the frame, and what it cost.
 *
 * KDE Wayland has two: KWin's own `ScreenShot2` (tens of milliseconds, no window) and the
 * `Screenshot` portal behind it (a permission window per read, seconds — the «hover lento» of an
 * AppImage). Nothing on screen said which one was in use, so the user could only guess. These
 * tests hold the names that answer it.
 */
class CaptureDiagnosticsTest {

    private fun image(): BufferedImage = BufferedImage(8, 6, BufferedImage.TYPE_INT_RGB)

    private fun backend(name: String, refuses: Boolean = false): ScreenCaptureBackend =
        object : ScreenCaptureBackend {
            override fun availability(): CaptureAvailability = CaptureAvailability.READY
            override fun screenBounds(): Rectangle = Rectangle(0, 0, 8, 6)
            override fun captureScreen(): BufferedImage = captureRegion(Rectangle(0, 0, 8, 6))
            override fun captureRegion(rect: Rectangle): BufferedImage =
                if (refuses) {
                    throw ScreenCaptureException(CaptureAvailability.UNSUPPORTED, "recusada")
                } else {
                    image()
                }

            override fun lastCaptureRoute(): String = name
            override fun toString(): String = name
        }

    @Test
    fun `a direct capture says ScreenShot2 served it`() {
        val capture = KdeWaylandCapture(
            direct = backend(ROUTE_KWIN),
            portal = backend(ROUTE_PORTAL),
            report = {},
        )

        capture.captureRegion(Rectangle(0, 0, 8, 6))

        assertEquals(ROUTE_KWIN, capture.lastCaptureRoute())
    }

    @Test
    fun `a refused direct capture says the portal served it`() {
        // The slow path: the AppImage cannot be authorized for ScreenShot2, so every read goes
        // through the portal and the status must say so instead of blaming the OCR.
        val capture = KdeWaylandCapture(
            direct = backend(ROUTE_KWIN, refuses = true),
            portal = backend(ROUTE_PORTAL),
            report = {},
        )

        capture.captureRegion(Rectangle(0, 0, 8, 6))

        assertEquals(ROUTE_PORTAL, capture.lastCaptureRoute())
    }

    @Test
    fun `nothing was served before the first capture`() {
        val capture = KdeWaylandCapture(direct = backend(ROUTE_KWIN), portal = backend(ROUTE_PORTAL), report = {})

        assertNull(capture.lastCaptureRoute())
    }

    @Test
    fun `the session names the capture path it expects`() {
        assertEquals(
            "$ROUTE_KWIN (KDE Wayland)",
            captureBackendLabel(OsFamily.LINUX, SessionType.WAYLAND, DesktopFamily.KDE),
        )
        assertEquals(
            "$ROUTE_PORTAL (GNOME Wayland)",
            captureBackendLabel(OsFamily.LINUX, SessionType.WAYLAND, DesktopFamily.GNOME),
        )
        assertEquals(
            "$ROUTE_SCREENCAST (Wayland)",
            captureBackendLabel(OsFamily.LINUX, SessionType.WAYLAND, DesktopFamily.OTHER),
        )
        assertEquals("$ROUTE_ROBOT (Windows)", captureBackendLabel(OsFamily.WINDOWS, SessionType.UNKNOWN, DesktopFamily.UNKNOWN))
        assertEquals("$ROUTE_ROBOT (X11)", captureBackendLabel(OsFamily.LINUX, SessionType.X11, DesktopFamily.KDE))
    }

    @Test
    fun `the summary carries the transport, the size and the time`() {
        CaptureDiagnostics.record(ROUTE_KWIN, BufferedImage(1280, 800, BufferedImage.TYPE_INT_RGB), 47)

        val summary = CaptureDiagnostics.lastSummary().orEmpty()
        assertTrue(summary, summary.contains(ROUTE_KWIN))
        assertTrue(summary, summary.contains("1280×800"))
        assertTrue(summary, summary.contains("47 ms"))
    }

    @Test
    fun `a backend without a name still gets a summary`() {
        CaptureDiagnostics.record(null, BufferedImage(8, 6, BufferedImage.TYPE_INT_RGB), 3)

        val summary = CaptureDiagnostics.lastSummary().orEmpty()
        assertTrue(summary, summary.contains("8×6"))
        assertTrue(summary, summary.contains("3 ms"))
    }
}
