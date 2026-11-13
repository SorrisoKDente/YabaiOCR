package dev.yabai.ocr.desktop

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KWinIntegrationTest {

    private class FakeTransport(private val starts: Boolean = true) : YabaiDbusTransport {
        var stopCalls = 0
        override fun start(onCursorMoved: (Int, Int) -> Unit, onShortcutPressed: (String) -> Unit) = starts
        override fun stop() {
            stopCalls++
        }
    }

    private class FakeHost(private val available: Boolean = true) : KWinScriptHost {
        var closed = false
        override fun isAvailable() = available
        override fun loadScript(path: String, pluginName: String) = true
        override fun start() = true
        override fun isLoaded(pluginName: String) = true
        override fun unload(pluginName: String) = Unit
        override fun close() {
            closed = true
        }
    }

    @Test
    fun `a dbus failure stops before any kwin script is touched`() {
        val transport = FakeTransport(starts = false)
        var hostRequested = false
        val integration = KWinIntegration(
            service = YabaiDbusService({ _, _ -> }, {}, { transport }),
            bridge = KWinHoverBridge({ hostRequested = true; FakeHost() }, Files.createTempDirectory("yabai-int").toFile()),
            fallbackShortcuts = { emptyList() },
        )

        assertFalse(integration.start())
        assertFalse(hostRequested)
        assertTrue(integration.statusMessage.contains("D-Bus"))
        assertEquals(1, transport.stopCalls)
    }

    @Test
    fun `a kwin failure rolls the dbus service back`() {
        val transport = FakeTransport()
        val integration = KWinIntegration(
            service = YabaiDbusService({ _, _ -> }, {}, { transport }),
            bridge = KWinHoverBridge({ FakeHost(available = false) }, Files.createTempDirectory("yabai-int").toFile()),
            fallbackShortcuts = { emptyList() },
        )

        assertFalse(integration.start())
        assertTrue(integration.statusMessage.contains("KWin"))
        assertEquals(1, transport.stopCalls)
    }

    @Test
    fun `success starts both halves and stop tears them down`() {
        val dir = Files.createTempDirectory("yabai-int").toFile()
        val transport = FakeTransport()
        val host = FakeHost()
        var fallbackCalls = 0
        val integration = KWinIntegration(
            service = YabaiDbusService({ _, _ -> }, {}, { transport }),
            bridge = KWinHoverBridge({ host }, dir),
            fallbackShortcuts = {
                fallbackCalls++
                listOf(HotkeyBinding(HotkeyIds.REGION, "Recortar", "Ctrl + Shift + A"))
            },
        )

        try {
            assertTrue(integration.start())
            assertTrue(integration.start())
            assertEquals(1, fallbackCalls)
            assertTrue(host.closed.not())

            integration.stop()
            integration.stop()
            assertEquals(1, transport.stopCalls)
            assertTrue(host.closed)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `updating the fallback keeps the bridge running with the new shortcut`() {
        val dir = Files.createTempDirectory("yabai-int").toFile()
        val transport = FakeTransport()
        val integration = KWinIntegration(
            service = YabaiDbusService({ _, _ -> }, {}, { transport }),
            bridge = KWinHoverBridge({ FakeHost() }, dir),
            fallbackShortcuts = { emptyList() },
        )

        try {
            assertTrue(integration.start())
            val binding = HotkeyBinding(HotkeyIds.QUICK_DICT, "Busca rápida", "Ctrl + Shift + D")
            assertTrue(integration.updateShortcuts(listOf(binding)))
            assertTrue(integration.isRunning)
            assertTrue(java.io.File(dir, "code/main.js").readText().contains("Ctrl+Shift+D"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
