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
    fun `kde wayland uses the screenshot portal instead of the JDK screencast`() {
        val robot = fake("robot")
        val screencast = fake("screencast")
        val kdeScreenshot = fake("kde-screenshot")

        assertEquals(
            kdeScreenshot,
            captureBackendFor(
                family = OsFamily.LINUX,
                session = SessionType.WAYLAND,
                robot = robot,
                screencast = screencast,
                desktop = DesktopFamily.KDE,
                kdeScreenshot = kdeScreenshot,
            ),
        )
    }

    @Test
    fun `other wayland desktops keep the screencast backend`() {
        val robot = fake("robot")
        val screencast = fake("screencast")
        val kdeScreenshot = fake("kde-screenshot")

        assertEquals(
            screencast,
            captureBackendFor(
                family = OsFamily.LINUX,
                session = SessionType.WAYLAND,
                robot = robot,
                screencast = screencast,
                desktop = DesktopFamily.GNOME,
                kdeScreenshot = kdeScreenshot,
            ),
        )
    }

    @Test
    fun `linux x11 and windows keep the robot backend`() {
        val robot = fake("robot")
        val screencast = fake("screencast")
        val kdeScreenshot = fake("kde-screenshot")

        assertEquals(
            robot,
            captureBackendFor(
                OsFamily.LINUX,
                SessionType.X11,
                robot,
                screencast,
                DesktopFamily.KDE,
                kdeScreenshot,
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
        assertTrue(
            captureAvailabilityMessage(CaptureAvailability.UNSUPPORTED).contains("JDK 21"),
        )
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
