package dev.yabai.ocr.desktop

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KdeCapturePermissionTest {

    private fun tempConfig(): File = Files.createTempDirectory("yabai-envd").toFile()

    @Test
    fun `the file turns the KWin check off with the documented variable`() {
        val file = kdeCapturePermissionFile(tempConfig())

        assertTrue(setKdeCapturePermission(true, file))

        assertEquals("yabai-ocr.conf", file.name)
        assertEquals("environment.d", file.parentFile.name)
        val text = file.readText()
        assertTrue(text.contains("$KWIN_SCREENSHOT_ENV_KEY=1"))
        assertTrue(isKdeCapturePermissionEnabled(file))
    }

    @Test
    fun `turning it off removes the file instead of leaving a disabled value behind`() {
        val file = kdeCapturePermissionFile(tempConfig())
        setKdeCapturePermission(true, file)

        assertTrue(setKdeCapturePermission(false, file))

        assertFalse(file.exists())
        assertFalse(isKdeCapturePermissionEnabled(file))
    }

    @Test
    fun `an unrelated environment file does not count as enabled`() {
        val file = kdeCapturePermissionFile(tempConfig())
        file.parentFile.mkdirs()
        file.writeText("# nada aqui\nOTHER=1\n")

        assertFalse(isKdeCapturePermissionEnabled(file))
    }

    @Test
    fun `a value of zero is not enabled`() {
        val file = kdeCapturePermissionFile(tempConfig())
        file.parentFile.mkdirs()
        file.writeText("$KWIN_SCREENSHOT_ENV_KEY=0\n")

        assertFalse(isKdeCapturePermissionEnabled(file))
    }

    @Test
    fun `writing twice keeps the same content and does not fail`() {
        val file = kdeCapturePermissionFile(tempConfig())
        assertTrue(setKdeCapturePermission(true, file))
        val first = file.readText()

        assertTrue(setKdeCapturePermission(true, file))

        assertEquals(first, file.readText())
    }

    @Test
    fun `the card says a relogin is pending until the session carries the variable`() {
        assertTrue(kdeCapturePermissionNeedsRelogin(emptyMap()))
        assertFalse(kdeCapturePermissionNeedsRelogin(mapOf(KWIN_SCREENSHOT_ENV_KEY to "1")))
    }
}
