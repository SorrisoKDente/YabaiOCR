package dev.yabai.ocr.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Drives [WindowsAutostart] through a fake registry: the real `HKCU\...\Run` is the user's logon
 * configuration and must not be written by a test run.
 */
class WindowsAutostartTest {

    private class FakeRegistry : AutostartRegistry {
        val values = linkedMapOf<String, String>()

        override fun read(name: String): String? = values[name]

        override fun write(name: String, command: String) {
            values[name] = command
        }

        override fun delete(name: String) {
            values.remove(name)
        }
    }

    private fun tempExe(): File {
        val dir = Files.createTempDirectory("yabai-autostart").toFile().apply { deleteOnExit() }
        return File(dir, "Yabai OCR.exe").apply {
            createNewFile()
            deleteOnExit()
        }
    }

    private fun command(exe: File): String = "\"${exe.absolutePath}\" --minimized"

    @Test
    fun `enabling writes the quoted executable with the minimized flag`() {
        val exe = tempExe()
        val registry = FakeRegistry()
        val autostart = WindowsAutostart(registry) { exe.absolutePath }

        autostart.setEnabled(true)

        assertEquals(command(exe), registry.read("YabaiOCR"))
        assertTrue(autostart.isEnabled())
    }

    @Test
    fun `enabling with an executable that does not exist does not write a broken entry`() {
        // The old resolver fell back to `<cwd>\Yabai OCR.exe`, a path that usually does not exist:
        // Windows listed the app as enabled and logon started nothing.
        val missing = File(tempExe().parentFile, "Yabai OCR.exe").apply { delete() }
        val registry = FakeRegistry()
        val autostart = WindowsAutostart(registry) { missing.absolutePath }

        autostart.setEnabled(true)

        assertNull(registry.read("YabaiOCR"))
        assertFalse(autostart.isEnabled())
    }

    @Test
    fun `enabling without a resolvable executable does not write any entry`() {
        val registry = FakeRegistry()
        val autostart = WindowsAutostart(registry) { null }

        autostart.setEnabled(true)

        assertNull(registry.read("YabaiOCR"))
        assertFalse(autostart.isEnabled())
    }

    @Test
    fun `an entry pointing at a removed executable is reported as disabled`() {
        val exe = tempExe()
        val registry = FakeRegistry().apply { write("YabaiOCR", command(exe)) }
        exe.delete()
        val autostart = WindowsAutostart(registry) { exe.absolutePath }

        assertFalse(autostart.isEnabled())
    }

    @Test
    fun `reconcile rewrites a stale entry to the current executable`() {
        val exe = tempExe()
        val registry = FakeRegistry().apply {
            write("YabaiOCR", "\"C:\\Users\\alguem\\Yabai OCR.exe\" --minimized")
        }
        val autostart = WindowsAutostart(registry) { exe.absolutePath }

        assertFalse(autostart.isEnabled())
        assertTrue(autostart.reconcile(true))

        assertEquals(command(exe), registry.read("YabaiOCR"))
        assertTrue(autostart.isEnabled())
    }

    @Test
    fun `reconcile removes the entry when the preference is off`() {
        val exe = tempExe()
        val registry = FakeRegistry().apply { write("YabaiOCR", command(exe)) }
        val autostart = WindowsAutostart(registry) { exe.absolutePath }

        assertTrue(autostart.reconcile(false))

        assertNull(registry.read("YabaiOCR"))
        assertFalse(autostart.isEnabled())
    }

    @Test
    fun `disabling removes the entry`() {
        val exe = tempExe()
        val registry = FakeRegistry()
        val autostart = WindowsAutostart(registry) { exe.absolutePath }
        autostart.setEnabled(true)

        autostart.setEnabled(false)

        assertNull(registry.read("YabaiOCR"))
        assertFalse(autostart.isEnabled())
    }
}
