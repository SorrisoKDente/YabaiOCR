package dev.yabai.ocr.desktop

import java.awt.Rectangle
import java.awt.image.BufferedImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenCaptureBackendTest {

    private fun fake(name: String): ScreenCaptureBackend = object : ScreenCaptureBackend {
        override fun availability(): CaptureAvailability = CaptureAvailability.READY
        override fun screenBounds(): Rectangle = Rectangle(0, 0, 1, 1)
        override fun captureScreen(): BufferedImage = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        override fun captureRegion(rect: Rectangle): BufferedImage = captureScreen()
        override fun toString(): String = name
    }

    @Test
    fun `kde wayland uses the direct screen shot 2 backend instead of the JDK screencast`() {
        val robot = fake("robot")
        val screencast = fake("screencast")
        val kdeCapture = fake("kde-capture")

        assertEquals(
            kdeCapture,
            captureBackendFor(
                family = OsFamily.LINUX,
                session = SessionType.WAYLAND,
                robot = robot,
                screencast = screencast,
                desktop = DesktopFamily.KDE,
                kdeCapture = kdeCapture,
            ),
        )
    }

    @Test
    fun `gnome wayland uses the one-shot screenshot portal instead of the JDK screencast`() {
        // The regression: every hover opened a new ScreenCast session, so the Shell showed its
        // sharing indicator, asked for the screen again and no text ever appeared.
        val robot = fake("robot")
        val screencast = fake("screencast")
        val kdeCapture = fake("kde-capture")
        val gnomeCapture = fake("gnome-capture")

        assertEquals(
            gnomeCapture,
            captureBackendFor(
                family = OsFamily.LINUX,
                session = SessionType.WAYLAND,
                robot = robot,
                screencast = screencast,
                desktop = DesktopFamily.GNOME,
                kdeCapture = kdeCapture,
                gnomeCapture = gnomeCapture,
            ),
        )
    }

    @Test
    fun `other wayland desktops keep the screencast backend`() {
        val robot = fake("robot")
        val screencast = fake("screencast")
        val kdeCapture = fake("kde-capture")
        val gnomeCapture = fake("gnome-capture")

        assertEquals(
            screencast,
            captureBackendFor(
                family = OsFamily.LINUX,
                session = SessionType.WAYLAND,
                robot = robot,
                screencast = screencast,
                desktop = DesktopFamily.OTHER,
                kdeCapture = kdeCapture,
                gnomeCapture = gnomeCapture,
            ),
        )
    }

    @Test
    fun `linux x11 and windows keep the robot backend`() {
        val robot = fake("robot")
        val screencast = fake("screencast")
        val kdeCapture = fake("kde-capture")

        assertEquals(
            robot,
            captureBackendFor(
                OsFamily.LINUX,
                SessionType.X11,
                robot,
                screencast,
                DesktopFamily.KDE,
                kdeCapture,
            ),
        )
        assertEquals(robot, captureBackendFor(OsFamily.WINDOWS, SessionType.UNKNOWN, robot, screencast))
        assertEquals(robot, captureBackendFor(OsFamily.MACOS, SessionType.UNKNOWN, robot, screencast))
    }

    @Test
    fun `every availability state has a distinct non-empty explanation`() {
        val messages = CaptureAvailability.entries.map(::captureAvailabilityMessage)

        assertTrue(messages.all { it.isNotBlank() })
        assertEquals(messages.size, messages.toSet().size)
        // The screencast backport landed in the update releases of the older LTS lines, so the
        // message must name both the 17.0.13 floor and the 21 line it shipped in.
        val unsupported = captureAvailabilityMessage(CaptureAvailability.UNSUPPORTED)
        assertTrue(unsupported, unsupported.contains("JDK 17.0.13+"))
        assertTrue(unsupported, unsupported.contains("21+"))
        assertNotEquals(
            captureAvailabilityMessage(CaptureAvailability.NEEDS_PERMISSION),
            captureAvailabilityMessage(CaptureAvailability.DENIED),
        )
    }

    @Test
    fun `the windows and x11 robot path is always ready`() {
        assertEquals(CaptureAvailability.READY, RobotScreenCapture.availability())
    }
}
