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
    ) : KWinScriptHost {
        val calls = mutableListOf<String>()
        var closed = false
        private var loaded = false

        override fun isAvailable(): Boolean = available

        override fun loadScript(path: String, pluginName: String): Boolean {
            calls.add("load:$pluginName:${File(path).name}")
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

    @Test
    fun `starting installs the script and loads it in kwin`() {
        val dir = tempDir()
        val host = FakeHost()
        val bridge = KWinHoverBridge({ host }, dir)
        try {
            assertTrue(bridge.start(emptyList()))
            assertTrue(bridge.running)
            assertEquals("load:yabai-ocr:main.js", host.calls.first())
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
        val bridge = KWinHoverBridge({ host }, dir)
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
    fun `kwin missing fails without writing or loading anything`() {
        val dir = tempDir()
        val host = FakeHost(available = false)
        val bridge = KWinHoverBridge({ host }, dir)
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
        val bridge = KWinHoverBridge({ host }, dir)
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
            assertFalse(KWinHoverBridge({ notStarting }, dir).start(emptyList()))
            assertTrue(notStarting.calls.contains("unload:yabai-ocr"))
            assertTrue(notStarting.closed)

            assertFalse(KWinHoverBridge({ notLoaded }, dir).start(emptyList()))
            assertTrue(notLoaded.calls.contains("unload:yabai-ocr"))
            assertTrue(notLoaded.closed)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `fallback shortcuts are written into the installed script`() {
        val dir = tempDir()
        val bridge = KWinHoverBridge({ FakeHost() }, dir)
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
}
