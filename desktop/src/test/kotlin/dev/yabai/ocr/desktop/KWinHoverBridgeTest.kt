package dev.yabai.ocr.desktop

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KWinHoverBridgeTest {

    private class FakeHost(
        private val available: Boolean = true,
        private val loadResult: Boolean = true,
        private val startResult: Boolean = true,
        private val loadedAfterStart: Boolean = true,
        loadedInitially: Boolean = false,
    ) : KWinScriptHost {
        val calls = mutableListOf<String>()
        var closed = false
        private var loaded = loadedInitially

        override fun isAvailable(): Boolean = available

        override fun loadScript(path: String, pluginName: String): Boolean {
            calls.add("load:$pluginName:${File(path).name}")
            // KWin answers -1 when a script with this plugin name is already loaded.
            if (loaded) return false
            loaded = loadResult
            return loadResult
        }

        override fun start(): Boolean {
            calls.add("start")
            if (startResult) loaded = loadedAfterStart
            return startResult
        }

        override fun isLoaded(pluginName: String): Boolean {
            calls.add("isLoaded:$pluginName")
            return loaded
        }

        override fun unload(pluginName: String) {
            calls.add("unload:$pluginName")
            loaded = false
        }

        override fun close() {
            closed = true
        }
    }

    private fun tempDir(): File = Files.createTempDirectory("yabai-kwin-bridge").toFile()

    private class FakeAccel : KGlobalAccelShortcuts {
        val forgotten = mutableListOf<Pair<String, List<String>>>()
        var closed = false

        override fun forget(component: String, actionNames: List<String>): Boolean {
            forgotten.add(component to actionNames)
            return true
        }

        override fun close() {
            closed = true
        }
    }

    @Test
    fun `starting installs the script and loads it in kwin`() {
        val dir = tempDir()
        val host = FakeHost()
        val bridge = KWinHoverBridge({ host }, dir, { FakeAccel() })
        try {
            assertTrue(bridge.start(emptyList()))
            assertTrue(bridge.running)
            assertTrue(host.calls.contains("load:yabai-ocr:main.js"))
            assertTrue(host.calls.contains("start"))
            assertTrue(host.calls.contains("isLoaded:yabai-ocr"))
            assertTrue(File(dir, "metadata.json").isFile)
            assertTrue(File(dir, "code/main.js").isFile)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `stopping unloads the script and closes the host`() {
        val dir = tempDir()
        val host = FakeHost()
        val bridge = KWinHoverBridge({ host }, dir, { FakeAccel() })
        try {
            bridge.start(emptyList())
            bridge.stop()

            assertFalse(bridge.running)
            assertTrue(host.calls.contains("unload:yabai-ocr"))
            assertTrue(host.closed)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a script left loaded by a previous run is unloaded before loading again`() {
        val dir = tempDir()
        val host = FakeHost(loadedInitially = true)
        val bridge = KWinHoverBridge({ host }, dir, { FakeAccel() })
        try {
            assertTrue(bridge.start(emptyList()))

            assertEquals("isLoaded:yabai-ocr", host.calls.first())
            assertEquals("unload:yabai-ocr", host.calls[1])
            assertTrue(host.calls.contains("load:yabai-ocr:main.js"))
            assertTrue(bridge.running)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `starting with fallback shortcuts forgets the previous kglobalaccel bindings`() {
        val dir = tempDir()
        val accel = FakeAccel()
        val bridge = KWinHoverBridge({ FakeHost() }, dir, { accel })
        try {
            assertTrue(bridge.start(listOf(HotkeyBinding(HotkeyIds.QUICK_DICT, "Busca rápida", "Ctrl + Shift + D"))))

            assertEquals(
                listOf("kwin" to listOf("yabaiOcrQuickDict")),
                accel.forgotten,
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `editing a shortcut clears the stored kglobalaccel binding before the reload`() {
        val dir = tempDir()
        val host = FakeHost()
        val accel = FakeAccel()
        val bridge = KWinHoverBridge({ host }, dir, { accel })
        try {
            // The failing state reported by the user: the script file already has the new
            // combination, but KGlobalAccel still holds the old one. Editing must still act.
            val edited = HotkeyBinding(HotkeyIds.QUICK_DICT, "Busca rápida", "Ctrl + Shift + D")
            assertTrue(bridge.start(listOf(edited)))
            accel.forgotten.clear()
            host.calls.clear()

            assertTrue(bridge.updateShortcuts(listOf(edited)))

            assertEquals(listOf("kwin" to listOf("yabaiOcrQuickDict")), accel.forgotten)
            assertTrue(host.calls.contains("load:yabai-ocr:main.js"))
            assertTrue(host.calls.contains("start"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `kwin missing fails without writing or loading anything`() {
        val dir = tempDir()
        val host = FakeHost(available = false)
        val bridge = KWinHoverBridge({ host }, dir, { FakeAccel() })
        try {
            assertFalse(bridge.start(emptyList()))
            assertFalse(bridge.running)
            assertTrue(host.calls.isEmpty())
            assertTrue(host.closed)
            assertFalse(File(dir, "code/main.js").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a script that fails to load is reported and cleaned up`() {
        val dir = tempDir()
        val host = FakeHost(loadResult = false)
        val bridge = KWinHoverBridge({ host }, dir, { FakeAccel() })
        try {
            assertFalse(bridge.start(emptyList()))
            assertFalse(bridge.running)
            assertFalse(host.calls.contains("start"))
            assertTrue(host.closed)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a script that does not start or stay loaded is unloaded`() {
        val dir = tempDir()
        val notStarting = FakeHost(startResult = false)
        val notLoaded = FakeHost(loadedAfterStart = false)
        try {
            assertFalse(KWinHoverBridge({ notStarting }, dir, { FakeAccel() }).start(emptyList()))
            assertTrue(notStarting.calls.contains("unload:yabai-ocr"))
            assertTrue(notStarting.closed)

            assertFalse(KWinHoverBridge({ notLoaded }, dir, { FakeAccel() }).start(emptyList()))
            assertTrue(notLoaded.calls.contains("unload:yabai-ocr"))
            assertTrue(notLoaded.closed)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `fallback shortcuts are written into the installed script`() {
        val dir = tempDir()
        val bridge = KWinHoverBridge({ FakeHost() }, dir, { FakeAccel() })
        try {
            val binding = HotkeyBinding(HotkeyIds.REGION, "Recortar", "Ctrl + Shift + A")
            assertTrue(bridge.start(listOf(binding)))

            val script = File(dir, "code/main.js").readText()
            assertTrue(script.contains("registerShortcut"))
            assertTrue(script.contains("Ctrl+Shift+A"))
            assertTrue(script.contains("notifyShortcut(\"${HotkeyIds.REGION}\")"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `updating shortcuts rewrites and reloads the running kwin script`() {
        val dir = tempDir()
        val host = FakeHost()
        val bridge = KWinHoverBridge({ host }, dir, { FakeAccel() })
        try {
            assertTrue(bridge.start(emptyList()))
            host.calls.clear()

            val binding = HotkeyBinding(HotkeyIds.QUICK_DICT, "Busca rápida", "Ctrl + Shift + D")
            assertTrue(bridge.updateShortcuts(listOf(binding)))

            val script = File(dir, "code/main.js").readText()
            assertTrue(script.contains("Ctrl+Shift+D"))
            assertEquals(
                listOf(
                    "unload:yabai-ocr",
                    "load:yabai-ocr:main.js",
                    "start",
                    "isLoaded:yabai-ocr",
                ),
                host.calls,
            )
            assertTrue(bridge.running)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `updating with the same shortcuts does not reload the script`() {
        val dir = tempDir()
        val host = FakeHost()
        val bridge = KWinHoverBridge({ host }, dir, { FakeAccel() })
        try {
            // With no fallback shortcuts there is nothing to register or forget, so an unchanged
            // script does not need another reload.
            assertTrue(bridge.start(emptyList()))
            host.calls.clear()

            assertTrue(bridge.updateShortcuts(emptyList()))
            assertTrue(host.calls.isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }
}
