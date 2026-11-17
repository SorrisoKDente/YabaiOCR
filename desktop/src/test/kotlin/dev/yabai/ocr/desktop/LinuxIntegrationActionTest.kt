package dev.yabai.ocr.desktop

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The exact path behind «Integrar ao sistema»: [integrateLinuxSystem] is what the button calls, so the
 * two entries it owns are asserted here instead of only through the UI.
 */
class LinuxIntegrationActionTest {

    private fun tempDir(): File =
        Files.createTempDirectory("yabai-integrate").toFile().apply { deleteOnExit() }

    private class RecordingAutostart : Autostart {
        val reconciled = mutableListOf<Boolean>()

        override fun setEnabled(enabled: Boolean) = Unit

        override fun isEnabled(): Boolean = false

        override fun reconcile(enabled: Boolean): Boolean {
            reconciled += enabled
            return true
        }
    }

    @Test
    fun `the button installs the menu entry and then reconciles autostart`() {
        // The button used to write only the menu entry. Autostart stayed on the previous AppImage,
        // started that build at login, and that build rewrote the menu entry back to itself: the
        // update looked ignored until the next reboot.
        val dataHome = tempDir()
        val integration = LinuxSystemIntegration(
            dataHome = dataHome,
            executableResolver = { "/home/deck/Applications/Yabai_OCR-1.0.9.AppImage" },
            wmClass = { "dev-yabai-ocr-desktop-MainKt" },
            iconBytes = { byteArrayOf(0x89.toByte(), 0x50) },
        )
        val autostart = RecordingAutostart()

        assertTrue(integrateLinuxSystem(integration, autostart, startOnBoot = true))

        val entry = integration.desktopFile.readText()
        assertTrue(entry.contains("Exec=\"/home/deck/Applications/Yabai_OCR-1.0.9.AppImage\""))
        assertTrue(entry.contains("StartupWMClass=dev-yabai-ocr-desktop-MainKt"))
        assertTrue(integration.iconFile.isFile)
        assertEquals("autostart must be reconciled once, with the switch state", listOf(true), autostart.reconciled)
    }

    @Test
    fun `the switch state is what autostart is reconciled with`() {
        val integration = LinuxSystemIntegration(
            dataHome = tempDir(),
            executableResolver = { "/home/deck/Applications/Yabai OCR.AppImage" },
            iconBytes = { byteArrayOf(2) },
        )
        val autostart = RecordingAutostart()

        assertTrue(integrateLinuxSystem(integration, autostart, startOnBoot = false))

        // Reconciling with the stored preference is what removes an entry the user turned off.
        assertEquals(listOf(false), autostart.reconciled)
    }

    @Test
    fun `a failed install leaves autostart alone`() {
        val integration = LinuxSystemIntegration(
            dataHome = tempDir(),
            executableResolver = { null },
            iconBytes = { byteArrayOf(1) },
        )
        val autostart = RecordingAutostart()

        assertFalse(integrateLinuxSystem(integration, autostart, startOnBoot = true))

        assertFalse(integration.desktopFile.exists())
        assertTrue("a failed install must not leave a half-integration", autostart.reconciled.isEmpty())
    }
}
