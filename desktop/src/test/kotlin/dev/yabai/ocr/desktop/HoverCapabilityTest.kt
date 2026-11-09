package dev.yabai.ocr.desktop

import java.awt.Rectangle
import java.awt.image.BufferedImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HoverCapabilityTest {

    @Test
    fun `windows and x11 keep the awt pointer`() {
        assertEquals(
            HoverBackendKind.AWT,
            hoverBackendKindFor(OsFamily.WINDOWS, SessionType.UNKNOWN, DesktopFamily.UNKNOWN),
        )
        assertEquals(
            HoverBackendKind.AWT,
            hoverBackendKindFor(OsFamily.LINUX, SessionType.X11, DesktopFamily.KDE),
        )
        assertEquals(
            HoverBackendKind.AWT,
            hoverBackendKindFor(OsFamily.MACOS, SessionType.UNKNOWN, DesktopFamily.UNKNOWN),
        )
    }

    @Test
    fun `kde wayland uses the kwin bridge`() {
        assertEquals(
            HoverBackendKind.KWIN,
            hoverBackendKindFor(OsFamily.LINUX, SessionType.WAYLAND, DesktopFamily.KDE),
        )
    }

    @Test
    fun `wayland without kwin is unsupported instead of polling a dead pointer`() {
        assertEquals(
            HoverBackendKind.UNSUPPORTED,
            hoverBackendKindFor(OsFamily.LINUX, SessionType.WAYLAND, DesktopFamily.GNOME),
        )
        assertEquals(
            HoverBackendKind.UNSUPPORTED,
            hoverBackendKindFor(OsFamily.LINUX, SessionType.WAYLAND, DesktopFamily.OTHER),
        )
        assertEquals(
            HoverBackendKind.UNSUPPORTED,
            hoverBackendKindFor(OsFamily.LINUX, SessionType.WAYLAND, DesktopFamily.UNKNOWN),
        )
    }

    @Test
    fun `every backend has a distinct non-empty message`() {
        val labels = HoverBackendKind.entries.map(::hoverBackendLabel)

        assertTrue(labels.all { it.isNotBlank() })
        assertEquals(labels.size, labels.toSet().size)
        assertTrue(hoverBackendLabel(HoverBackendKind.UNSUPPORTED).contains("indisponível"))
    }

    @Test
    fun `only kwin replaces the robot with the screencast capture`() {
        val capture: (Rectangle) -> BufferedImage = { BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB) }

        assertNull(hoverCaptureProvider(HoverBackendKind.AWT, capture))
        assertNull(hoverCaptureProvider(HoverBackendKind.UNSUPPORTED, capture))
        assertNotNull(hoverCaptureProvider(HoverBackendKind.KWIN, capture))
    }
}
