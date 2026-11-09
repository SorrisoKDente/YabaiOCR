package dev.yabai.ocr.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalGlobalShortcutsTest {

    private class FakeTransport(
        private val available: Boolean = true,
        private val session: String? = "/org/freedesktop/portal/desktop/session/1/yabai_ocr_1",
        private val bindResult: Boolean = true,
    ) : PortalShortcutsTransport {
        var closed = false
        var createdSessions = 0
        var binds = mutableListOf<Pair<String, List<PortalShortcutRequest>>>()
        var listener: ((String) -> Unit)? = null

        override fun isAvailable(): Boolean = available

        override fun createSession(): String? {
            createdSessions++
            return session
        }

        override fun bindShortcuts(sessionHandle: String, shortcuts: List<PortalShortcutRequest>): Boolean {
            binds.add(sessionHandle to shortcuts)
            return bindResult
        }

        override fun setShortcutListener(listener: (String) -> Unit) {
            this.listener = listener
        }

        override fun close() {
            closed = true
        }
    }

    private fun bindings() = hotkeyBindings(
        fullScreen = "Ctrl + Shift + O",
        region = "Ctrl + Shift + A",
        quickDict = "Ctrl + Shift + D",
        hover = "Ctrl + Shift + H",
        hoverCycle = "Ctrl + Shift + X",
    )

    @Test
    fun `starting creates a session and binds every shortcut with its xdg trigger`() {
        val transport = FakeTransport()
        val backend = PortalGlobalShortcuts(HotkeyCallbacks(), { transport })

        assertTrue(backend.start(bindings()))

        assertEquals(1, transport.createdSessions)
        val (session, requests) = transport.binds.single()
        assertEquals("/org/freedesktop/portal/desktop/session/1/yabai_ocr_1", session)
        assertEquals(
            listOf(
                PortalShortcutRequest(HotkeyIds.FULL_SCREEN, "Capturar a tela inteira", "CTRL+SHIFT+O"),
                PortalShortcutRequest(HotkeyIds.REGION, "Recortar uma área da tela", "CTRL+SHIFT+A"),
                PortalShortcutRequest(HotkeyIds.QUICK_DICT, "Abrir a busca rápida de dicionário", "CTRL+SHIFT+D"),
                PortalShortcutRequest(HotkeyIds.HOVER_TOGGLE, "Ligar/desligar o modo hover", "CTRL+SHIFT+H"),
                PortalShortcutRequest(HotkeyIds.HOVER_CYCLE, "Alternar o dicionário do hover", "CTRL+SHIFT+X"),
            ),
            requests,
        )
    }

    @Test
    fun `the portal activation reaches the matching app action`() {
        val calls = mutableListOf<String>()
        val transport = FakeTransport()
        val backend = PortalGlobalShortcuts(
            HotkeyCallbacks(
                onFullScreen = { calls.add("full") },
                onRegion = { calls.add("region") },
            ),
            { transport },
        )

        backend.start(bindings())
        transport.listener?.invoke(HotkeyIds.REGION)
        transport.listener?.invoke(HotkeyIds.FULL_SCREEN)
        transport.listener?.invoke("desconhecido")

        assertEquals(listOf("region", "full"), calls)
    }

    @Test
    fun `a missing or refused portal fails instead of pretending to bind`() {
        val unavailable = FakeTransport(available = false)
        assertFalse(PortalGlobalShortcuts(HotkeyCallbacks(), { unavailable }).start(bindings()))
        assertTrue(unavailable.closed)
        assertEquals(0, unavailable.createdSessions)

        val noSession = FakeTransport(session = null)
        assertFalse(PortalGlobalShortcuts(HotkeyCallbacks(), { noSession }).start(bindings()))
        assertTrue(noSession.closed)

        val refusedBind = FakeTransport(bindResult = false)
        assertFalse(PortalGlobalShortcuts(HotkeyCallbacks(), { refusedBind }).start(bindings()))
        assertTrue(refusedBind.closed)
    }

    @Test
    fun `shortcuts the portal cannot represent never open a session`() {
        val transport = FakeTransport()
        val backend = PortalGlobalShortcuts(HotkeyCallbacks(), { transport })

        val started = backend.start(
            hotkeyBindings(
                fullScreen = "O",
                region = "Ctrl + ;",
                quickDict = "Space",
                hover = "F1",
                hoverCycle = "",
            ),
        )

        assertFalse(started)
        assertEquals(0, transport.createdSessions)
        assertFalse(transport.closed)
    }

    @Test
    fun `editing a shortcut rebinds on the same session`() {
        val transport = FakeTransport()
        val backend = PortalGlobalShortcuts(HotkeyCallbacks(), { transport })
        backend.start(bindings())

        val updated = backend.update(
            hotkeyBindings(
                fullScreen = "Ctrl + Alt + 1",
                region = "Ctrl + Shift + A",
                quickDict = "Ctrl + Shift + D",
                hover = "Ctrl + Shift + H",
                hoverCycle = "Ctrl + Shift + X",
            ),
        )

        assertTrue(updated)
        assertEquals(2, transport.binds.size)
        assertEquals("CTRL+ALT+1", transport.binds.last().second.first().preferredTrigger)
        assertEquals(1, transport.createdSessions)
    }

    @Test
    fun `update before start and stop close the transport only once`() {
        val transport = FakeTransport()
        val backend = PortalGlobalShortcuts(HotkeyCallbacks(), { transport })

        assertFalse(backend.update(bindings()))

        backend.start(bindings())
        backend.stop()

        assertTrue(transport.closed)
        assertFalse(backend.update(bindings()))
    }
}
