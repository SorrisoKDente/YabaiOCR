package dev.yabai.ocr.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HotkeyBackendSelectionTest {

    private class FakeBackend(
        override val kind: HotkeyBackendKind,
        var starts: Boolean = true,
        var updates: Boolean = true,
    ) : HotkeyBackend {
        var stopped = false
        var lastBindings: List<HotkeyBinding> = emptyList()

        override fun start(bindings: List<HotkeyBinding>): Boolean {
            lastBindings = bindings
            return starts
        }

        override fun update(bindings: List<HotkeyBinding>): Boolean {
            lastBindings = bindings
            return updates
        }

        override fun stop() {
            stopped = true
        }
    }

    @Test
    fun `windows and x11 keep jnativehook`() {
        assertEquals(
            HotkeyBackendKind.JNATIVEHOOK,
            hotkeyBackendKindFor(OsFamily.WINDOWS, SessionType.UNKNOWN, DesktopFamily.UNKNOWN),
        )
        assertEquals(
            HotkeyBackendKind.JNATIVEHOOK,
            hotkeyBackendKindFor(OsFamily.LINUX, SessionType.X11, DesktopFamily.KDE),
        )
        assertEquals(
            HotkeyBackendKind.JNATIVEHOOK,
            hotkeyBackendKindFor(OsFamily.MACOS, SessionType.UNKNOWN, DesktopFamily.UNKNOWN),
        )
    }

    @Test
    fun `linux wayland uses the portal instead of jnativehook`() {
        assertEquals(
            HotkeyBackendKind.PORTAL,
            hotkeyBackendKindFor(OsFamily.LINUX, SessionType.WAYLAND, DesktopFamily.KDE),
        )
        assertEquals(
            HotkeyBackendKind.PORTAL,
            hotkeyBackendKindFor(OsFamily.LINUX, SessionType.WAYLAND, DesktopFamily.GNOME),
        )
    }

    @Test
    fun `every backend has a distinct status message and a failure hint`() {
        val labels = HotkeyBackendKind.entries.map(::hotkeyBackendLabel)

        assertTrue(labels.all { it.isNotBlank() })
        assertEquals(labels.size, labels.toSet().size)
        val portalFailure = hotkeyBackendFailureMessage(HotkeyBackendKind.PORTAL).lowercase()
        assertTrue(portalFailure.contains("bandeja") || portalFailure.contains("tray"))
        assertNotEquals(
            hotkeyBackendFailureMessage(HotkeyBackendKind.PORTAL),
            hotkeyBackendFailureMessage(HotkeyBackendKind.JNATIVEHOOK),
        )
    }

    @Test
    fun `a started backend reports its own status and receives the bindings`() {
        val backend = FakeBackend(HotkeyBackendKind.PORTAL)
        val service = HotkeyService(HotkeyCallbacks(), HotkeyBackendKind.PORTAL, backend)

        val started = service.start("Ctrl + Shift + O", "Ctrl + Shift + A", "Ctrl + Shift + D", "Ctrl + Shift + H", "Ctrl + Shift + X")

        assertTrue(started)
        assertEquals(HotkeyBackendKind.PORTAL, service.activeKind)
        assertEquals(hotkeyBackendLabel(HotkeyBackendKind.PORTAL), service.statusMessage)
        assertEquals(
            listOf(
                HotkeyIds.FULL_SCREEN,
                HotkeyIds.REGION,
                HotkeyIds.QUICK_DICT,
                HotkeyIds.HOVER_TOGGLE,
                HotkeyIds.HOVER_CYCLE,
            ),
            backend.lastBindings.map { it.id },
        )
    }

    @Test
    fun `a backend that cannot start degrades to unavailable instead of crashing`() {
        val backend = FakeBackend(HotkeyBackendKind.PORTAL, starts = false)
        val service = HotkeyService(HotkeyCallbacks(), HotkeyBackendKind.PORTAL, backend)

        val started = service.start("Ctrl + Shift + O", "Ctrl + Shift + A", "Ctrl + Shift + D", "Ctrl + Shift + H", "Ctrl + Shift + X")

        assertFalse(started)
        assertEquals(HotkeyBackendKind.UNAVAILABLE, service.activeKind)
        assertEquals(hotkeyBackendFailureMessage(HotkeyBackendKind.PORTAL), service.statusMessage)
        assertTrue(backend.stopped)
    }

    @Test
    fun `editing a shortcut rebinds without restarting and exposes a failure`() {
        val backend = FakeBackend(HotkeyBackendKind.PORTAL, updates = false)
        val service = HotkeyService(HotkeyCallbacks(), HotkeyBackendKind.PORTAL, backend)
        service.start("Ctrl + Shift + O", "Ctrl + Shift + A", "Ctrl + Shift + D", "Ctrl + Shift + H", "Ctrl + Shift + X")

        val updated = service.updateHotkeys(
            "Ctrl + Alt + 1",
            "Ctrl + Shift + A",
            "Ctrl + Shift + D",
            "Ctrl + Shift + H",
            "Ctrl + Shift + X",
        )

        assertFalse(updated)
        assertEquals(HotkeyBackendKind.UNAVAILABLE, service.activeKind)
        assertEquals(hotkeyBackendFailureMessage(HotkeyBackendKind.PORTAL), service.statusMessage)
        assertTrue(backend.stopped)
        assertEquals(HotkeyIds.FULL_SCREEN, backend.lastBindings.first().id)
        assertEquals("Ctrl + Alt + 1", backend.lastBindings.first().configured)
    }

    @Test
    fun `editing after a failed start retries the backend instead of updating a dead session`() {
        val backend = FakeBackend(HotkeyBackendKind.PORTAL, starts = false)
        val service = HotkeyService(HotkeyCallbacks(), HotkeyBackendKind.PORTAL, backend)
        assertFalse(service.start("Ctrl + Shift + O", "Ctrl + Shift + A", "Ctrl + Shift + D", "Ctrl + Shift + H", "Ctrl + Shift + X"))
        assertEquals(HotkeyBackendKind.UNAVAILABLE, service.activeKind)

        backend.starts = true
        val updated = service.updateHotkeys(
            "Ctrl + Alt + 1",
            "Ctrl + Shift + A",
            "Ctrl + Shift + D",
            "Ctrl + Shift + H",
            "Ctrl + Shift + X",
        )

        assertTrue(updated)
        assertEquals(HotkeyBackendKind.PORTAL, service.activeKind)
        assertEquals(hotkeyBackendLabel(HotkeyBackendKind.PORTAL), service.statusMessage)
        assertEquals("Ctrl + Alt + 1", backend.lastBindings.first().configured)
    }

    @Test
    fun `the unavailable backend refuses to start and stays unavailable`() {
        val service = HotkeyService(
            HotkeyCallbacks(),
            HotkeyBackendKind.UNAVAILABLE,
            UnavailableHotkeys(),
        )

        assertFalse(service.start("Ctrl + Shift + O", "Ctrl + Shift + A", "Ctrl + Shift + D", "Ctrl + Shift + H", "Ctrl + Shift + X"))
        assertEquals(HotkeyBackendKind.UNAVAILABLE, service.activeKind)
    }
}
