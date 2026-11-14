package dev.yabai.ocr.desktop

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An AppImage mounts its contents read-only, so JNativeHook cannot unpack its library next to the
 * jars and fails with `UnsatisfiedLinkError ... (Read-only file system)` before any window exists.
 * Pointing `jnativehook.lib.path` at a writable per-user cache directory is the fix; Windows (and an
 * explicit override) must keep the path they already had.
 */
class NativeLibraryPathTest {

    @Test
    fun `linux gets a writable cache directory`() {
        val home = Files.createTempDirectory("yabai-home").toFile()
        val properties = mutableMapOf<String, String>()

        val chosen = configureJNativeHookLibraryPath(
            isLinux = true,
            existing = null,
            directory = jNativeHookLibraryDirectory(home),
            setProperty = { key, value -> properties[key] = value },
        )

        val expected = File(File(home, ".cache"), "yabai-ocr/native")
        assertEquals(expected.absolutePath, chosen?.absolutePath)
        assertEquals(expected.absolutePath, properties[JNATIVEHOOK_LIB_PATH_PROPERTY])
        assertTrue("the cache directory was not created", expected.isDirectory)
    }

    @Test
    fun `windows keeps the default lookup`() {
        val home = Files.createTempDirectory("yabai-home").toFile()
        val properties = mutableMapOf<String, String>()

        val chosen = configureJNativeHookLibraryPath(
            isLinux = false,
            existing = null,
            directory = jNativeHookLibraryDirectory(home),
            setProperty = { key, value -> properties[key] = value },
        )

        assertNull(chosen)
        assertFalse(properties.containsKey(JNATIVEHOOK_LIB_PATH_PROPERTY))
        assertFalse("a Windows session should not create the Linux cache", File(home, ".cache").exists())
    }

    @Test
    fun `an explicit override is never replaced`() {
        val home = Files.createTempDirectory("yabai-home").toFile()
        val properties = mutableMapOf<String, String>()
        val explicit = File(home, "custom-native").absolutePath

        val chosen = configureJNativeHookLibraryPath(
            isLinux = true,
            existing = explicit,
            directory = jNativeHookLibraryDirectory(home),
            setProperty = { key, value -> properties[key] = value },
        )

        assertNull(chosen)
        assertFalse(properties.containsKey(JNATIVEHOOK_LIB_PATH_PROPERTY))
    }
}
