package dev.yabai.ocr.desktop

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps the Compose version above the fix for CMP-9994.
 *
 * On Ubuntu 24.04+ the JVM crashes with `SIGSEGV` inside `libstdc++.so.6` when a native library
 * that formats text through `std::ostream` — PP-OCR's ONNX Runtime, for one — is called from a
 * Compose application. The crash hits hover, full-screen capture and region capture alike, because
 * all three run OCR. Compose **1.10** is the release that fixes it (upstream confirmed on
 * CMP-9994); 1.7 only had the `configure.swing.globals=false` workaround, which removes some crash
 * points and leaves the hover one in place.
 *
 * The version lives in a plugin line, so nothing else fails when it is lowered: the build stays
 * green and the crash comes back only on Linux. This guard makes a downgrade fail on every host.
 */
class ComposeLinuxCrashFixTest {

    private fun buildFileText(): String {
        val buildFile = sequenceOf(File("build.gradle.kts"), File("desktop/build.gradle.kts"))
            .firstOrNull { it.isFile }
            ?: error("desktop/build.gradle.kts not found from ${File(".").absolutePath}")
        return buildFile.readText()
    }

    @Test
    fun `the compose desktop plugin is at least 1_10 for the libstdc++ fix`() {
        val text = buildFileText()
        val match = Regex("""id\("org\.jetbrains\.compose"\)\s*version\s*"([0-9][0-9.]*)"""")
            .find(text)
        assertTrue("compose plugin version not found in desktop/build.gradle.kts", match != null)

        val version = match!!.groupValues[1]
        assertTrue(
            "Compose $version predates the CMP-9994 fix; use 1.10 or newer",
            compareVersions(version, "1.10.0") >= 0,
        )
    }

    private fun compareVersions(a: String, b: String): Int {
        val left = a.split('.').map { it.toIntOrNull() ?: 0 }
        val right = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(left.size, right.size)) {
            val difference = left.getOrElse(index) { 0 } - right.getOrElse(index) { 0 }
            if (difference != 0) return difference
        }
        return 0
    }
}
