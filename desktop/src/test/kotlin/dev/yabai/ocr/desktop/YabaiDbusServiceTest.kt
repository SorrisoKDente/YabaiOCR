package dev.yabai.ocr.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YabaiDbusServiceTest {

    private class FakeTransport(private val starts: Boolean = true) : YabaiDbusTransport {
        var startCalls = 0
        var stopCalls = 0
        var hover: ((Int, Int) -> Unit)? = null
        var shortcut: ((String) -> Unit)? = null

        override fun start(
            onCursorMoved: (Int, Int) -> Unit,
            onShortcutPressed: (String) -> Unit,
        ): Boolean {
            startCalls++
            hover = onCursorMoved
            shortcut = onShortcutPressed
            return starts
        }

        override fun stop() {
            stopCalls++
        }
    }

    @Test
    fun `starting exports both sinks and is idempotent`() {
        val transport = FakeTransport()
        val service = YabaiDbusService(
            onCursorMoved = { x, y -> received.add("hover:$x,$y") },
            onShortcutPressed = { received.add("shortcut:$it") },
            transportFactory = { transport },
        )

        assertTrue(service.start())
        assertTrue(service.running)
        assertTrue(service.start())

        assertEquals(1, transport.startCalls)
        transport.hover?.invoke(10, 20)
        transport.shortcut?.invoke(HotkeyIds.REGION)
        assertEquals(listOf("hover:10,20", "shortcut:${HotkeyIds.REGION}"), received)
    }

    @Test
    fun `a transport that cannot start is closed and the service stays down`() {
        val transport = FakeTransport(starts = false)
        val service = YabaiDbusService({ _, _ -> }, {}, { transport })

        assertFalse(service.start())
        assertFalse(service.running)
        assertEquals(1, transport.stopCalls)
    }

    @Test
    fun `a missing transport does not crash`() {
        val service = YabaiDbusService({ _, _ -> }, {}, { null })

        assertFalse(service.start())
        assertFalse(service.running)
    }

    @Test
    fun `stop closes once and allows a later start`() {
        val transport = FakeTransport()
        val service = YabaiDbusService({ _, _ -> }, {}, { transport })

        service.start()
        service.stop()
        service.stop()

        assertEquals(1, transport.stopCalls)
        assertFalse(service.running)

        assertTrue(service.start())
        assertTrue(service.running)
        assertEquals(2, transport.startCalls)
    }

    private val received = mutableListOf<String>()
}
