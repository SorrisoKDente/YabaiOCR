package dev.yabai.ocr.desktop

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The GNOME bridge is the only way to read the global pointer on GNOME Wayland: a Shell extension
 * polls `global.get_pointer()` and calls the same D-Bus object the KWin script uses.
 *
 * The extension itself can only be exercised by a running Shell, so the guard is on the contract —
 * bus name, path, method and the log-out case — plus the install/enable flow with a fake host.
 */
class GnomeHoverBridgeTest {

    @get:Rule
    val temp = TemporaryFolder()

    private class FakeHost(
        var loaded: Boolean = false,
        private val enableSucceeds: Boolean = true,
        private val enableThrows: Boolean = false,
    ) : GnomePointerHost {
        val enabled = mutableListOf<String>()
        var closed = false

        override fun isBridgeLoaded(): Boolean = loaded

        override fun enableExtension(uuid: String): Boolean {
            enabled += uuid
            if (enableThrows) error("gnome-extensions is missing")
            return enableSucceeds
        }

        override fun close() {
            closed = true
        }
    }

    @Test
    fun `the bundled extension declares the contract the app listens on`() {
        val metadata = GnomeShellExtension.metadata()
        assertTrue(metadata.contains("\"uuid\": \"${GnomeShellExtension.UUID}\""))
        assertTrue(metadata.contains("\"46\""))

        val script = GnomeShellExtension.script()
        assertTrue("reads the global pointer", script.contains("global.get_pointer()"))
        assertTrue("calls the existing hover object", script.contains(KWinScript.HOVER_PATH))
        assertTrue(script.contains(KWinScript.HOVER_INTERFACE))
        assertTrue(script.contains("'CursorMoved'"))
        assertTrue("owns the readiness name", script.contains(GnomeShellExtension.BRIDGE_BUS_NAME))
        assertTrue("is an ES module extension", script.contains("resource:///org/gnome/shell/extensions/extension.js"))
    }

    @Test
    fun `install writes both files and does not rewrite unchanged content`() {
        val dir = File(temp.root, "yabai-ocr@yabai.dev")

        GnomeShellExtension.install(dir)

        val metadata = File(dir, "metadata.json")
        val script = File(dir, "extension.js")
        assertTrue(metadata.isFile)
        assertTrue(script.isFile)

        val stamp = script.lastModified()
        Thread.sleep(20)
        GnomeShellExtension.install(dir)

        assertEquals("an unchanged extension must not be rewritten", stamp, script.lastModified())
    }

    @Test
    fun `the extension folder follows the xdg data home`() {
        assertEquals(
            File("/home/deck/.local/share/gnome-shell/extensions/${GnomeShellExtension.UUID}")
                .invariantSeparatorsPath,
            gnomeExtensionDirectory(home = File("/home/deck"), xdgDataHome = null).invariantSeparatorsPath,
        )
        assertEquals(
            File("/tmp/data/gnome-shell/extensions/${GnomeShellExtension.UUID}").invariantSeparatorsPath,
            gnomeExtensionDirectory(home = File("/home/deck"), xdgDataHome = "/tmp/data")
                .invariantSeparatorsPath,
        )
        assertEquals(
            File("/home/deck/.local/share/gnome-shell/extensions/${GnomeShellExtension.UUID}")
                .invariantSeparatorsPath,
            gnomeExtensionDirectory(home = File("/home/deck"), xdgDataHome = "  ").invariantSeparatorsPath,
        )
    }

    @Test
    fun `a loaded extension makes the hover ready without touching the disk`() {
        val host = FakeHost(loaded = true)
        val directory = File(temp.root, "never-written")
        val bridge = GnomeHoverBridge({ host }, directory)

        assertTrue(bridge.start())
        assertTrue(bridge.running)
        assertFalse(bridge.needsRelogin)
        assertTrue("an already loaded bridge must not install anything", !directory.exists())
    }

    @Test
    fun `a fresh install is enabled and asks for one log out`() {
        val host = FakeHost(loaded = false)
        val directory = File(temp.root, "extension")
        val bridge = GnomeHoverBridge({ host }, directory)

        assertFalse("the running Shell cannot use it before a restart", bridge.start())

        assertTrue(bridge.needsRelogin)
        assertFalse(bridge.running)
        assertEquals(listOf(GnomeShellExtension.UUID), host.enabled)
        assertTrue(File(directory, "metadata.json").isFile)
        assertTrue(File(directory, "extension.js").isFile)
    }

    @Test
    fun `a refused enable reports the failure instead of pretending it worked`() {
        val host = FakeHost(loaded = false, enableSucceeds = false)
        val bridge = GnomeHoverBridge({ host }, File(temp.root, "extension"))

        assertFalse(bridge.start())
        assertFalse("a refused enable is not a pending relogin", bridge.needsRelogin)
        assertNotNull(bridge.lastError)
    }

    @Test
    fun `an enable that throws is a failure, not a pending log out`() {
        // A missing `gnome-extensions` CLI must never be reported as "enabled, log out to use it".
        val host = FakeHost(loaded = false, enableThrows = true)
        val bridge = GnomeHoverBridge({ host }, File(temp.root, "extension"))

        assertFalse(bridge.start())
        assertFalse(bridge.needsRelogin)
        assertNotNull(bridge.lastError)
    }

    @Test
    fun `an extension folder that cannot be written is reported`() {
        // The path below a regular file can never become a directory: the write throws and the user
        // must see why the hover is off, instead of a bridge that claims to be installed.
        val blocker = temp.newFile("not-a-folder")
        val bridge = GnomeHoverBridge({ FakeHost(loaded = false) }, File(blocker, "extension"))

        assertFalse(bridge.start())
        assertFalse(bridge.needsRelogin)
        assertNotNull(bridge.lastError)
    }

    @Test
    fun `an unavailable session bus is reported`() {
        val bridge = GnomeHoverBridge({ null }, File(temp.root, "extension"))

        assertFalse(bridge.start())
        assertNotNull(bridge.lastError)
    }
}
