package dev.yabai.ocr.desktop

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KWinScriptTest {

    private fun bindings() = hotkeyBindings(
        fullScreen = "Ctrl + Shift + O",
        region = "Ctrl + Shift + A",
        quickDict = "Ctrl + Shift + D",
        hover = "Ctrl + Shift + H",
        hoverCycle = "Ctrl + Shift + X",
    )

    @Test
    fun `the bundled script and metadata declare the expected plugin`() {
        assertTrue(KWinScript.metadata().contains("\"X-Plasma-API\""))
        assertTrue(KWinScript.metadata().contains("\"javascript\""))

        val script = KWinScript.baseMainScript()
        assertTrue(script.contains("cursorPosChanged"))
        assertTrue(script.contains("callDBus"))
        assertTrue(script.contains(KWinScript.HOVER_PATH))
        assertTrue(script.contains(KWinScript.HOVER_INTERFACE))
        assertFalse(script.contains("registerShortcut"))
    }

    @Test
    fun `fallback shortcuts generate registerShortcut and the dbus callback`() {
        val block = KWinScript.fallbackShortcutBlock(bindings())

        assertTrue(block.contains("function notifyShortcut(id)"))
        assertTrue(block.contains(KWinScript.SHORTCUTS_PATH))
        assertTrue(block.contains(KWinScript.SHORTCUTS_INTERFACE))
        assertTrue(block.contains("\"ShortcutPressed\""))
        assertTrue(block.contains("registerShortcut(\"yabaiOcrCaptureFullscreen\""))
        assertTrue(block.contains("\"Ctrl+Shift+O\""))
        assertTrue(block.contains("notifyShortcut(\"capture-fullscreen\")"))
        assertTrue(block.contains("\"Ctrl+Shift+X\""))
    }

    @Test
    fun `fallback block is empty when no shortcut can be represented`() {
        val block = KWinScript.fallbackShortcutBlock(
            hotkeyBindings(
                fullScreen = "O",
                region = "Ctrl + ;",
                quickDict = "",
                hover = "Shift",
                hoverCycle = "F1",
            ),
        )

        assertEquals("", block)
        assertEquals(KWinScript.baseMainScript(), KWinScript.mainScript(emptyList()))
    }

    @Test
    fun `descriptions are escaped before reaching javascript`() {
        val block = KWinScript.fallbackShortcutBlock(
            listOf(HotkeyBinding(HotkeyIds.FULL_SCREEN, "Tela \"inteira\" \\ teste", "Ctrl + Shift + O")),
        )

        assertTrue(block.contains("Tela \\\"inteira\\\" \\\\ teste"))
        assertFalse(block.contains("Tela \"inteira\""))
    }

    @Test
    fun `install writes both files and does not rewrite unchanged content`() {
        val dir = Files.createTempDirectory("yabai-kwin").toFile()
        try {
            val files = KWinScript.install(dir, bindings())

            assertEquals(listOf("metadata.json", "code/main.js"), files.map { it.relativeTo(dir).invariantSeparatorsPath })
            assertTrue(files[0].isFile)
            assertTrue(files[1].isFile)
            assertTrue(files[1].readText().contains("registerShortcut"))

            val firstStamp = files[1].lastModified()
            Thread.sleep(20)
            KWinScript.install(dir, bindings())
            assertEquals(firstStamp, files[1].lastModified())

            KWinScript.install(dir, emptyList())
            assertFalse(files[1].readText().contains("registerShortcut"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `shortcuts are converted to the qkeysequence kwin expects`() {
        assertEquals("Ctrl+Shift+O", toKWinShortcut("Ctrl + Shift + O"))
        assertEquals("Meta+Space", toKWinShortcut("Super + Space"))
        assertEquals("Alt+Return", toKWinShortcut("Alt + Enter"))
        assertEquals("Ctrl+PgUp", toKWinShortcut("Ctrl + Page Up"))
        assertEquals("Ctrl+F13", toKWinShortcut("Ctrl + F13"))
    }

    @Test
    fun `shortcuts kwin cannot represent are refused`() {
        assertNull(toKWinShortcut("O"))
        assertNull(toKWinShortcut("Space"))
        assertNull(toKWinShortcut("Ctrl + ;"))
        assertNull(toKWinShortcut("Ctrl + Shift"))
        assertNull(toKWinShortcut("Ctrl + Shift + O + J"))
        assertNull(toKWinShortcut(""))
    }

    @Test
    fun `the script folder follows the xdg data home`() {
        assertEquals(
            java.io.File("/home/deck/.local/share/kwin/scripts/yabai-ocr").invariantSeparatorsPath,
            kwinScriptDirectory(
                home = java.io.File("/home/deck"),
                xdgDataHome = null,
            ).invariantSeparatorsPath,
        )
        assertEquals(
            java.io.File("/tmp/data/kwin/scripts/yabai-ocr").invariantSeparatorsPath,
            kwinScriptDirectory(
                home = java.io.File("/home/deck"),
                xdgDataHome = "/tmp/data",
            ).invariantSeparatorsPath,
        )
        assertEquals(
            java.io.File("/home/deck/.local/share/kwin/scripts/yabai-ocr").invariantSeparatorsPath,
            kwinScriptDirectory(
                home = java.io.File("/home/deck"),
                xdgDataHome = "  ",
            ).invariantSeparatorsPath,
        )
    }
}
