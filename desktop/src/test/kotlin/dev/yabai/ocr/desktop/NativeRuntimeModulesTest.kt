package dev.yabai.ocr.desktop

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The packaged Linux runtime is a jlink image, not the developer JDK.
 *
 * dbus-java's unix-socket transport needs its jlink modules. When a module is missing, the D-Bus
 * service, the KWin bridge and the portal shortcuts all fail with `NoClassDefFoundError` at
 * runtime — visible only on Linux and only in the packaged build. This guard keeps the modules in
 * `nativeDistributions`.
 */
class NativeRuntimeModulesTest {

    private fun buildFileText(): String {
        val buildFile = sequenceOf(File("build.gradle.kts"), File("desktop/build.gradle.kts"))
            .firstOrNull { it.isFile }
            ?: error("desktop/build.gradle.kts not found from ${File(".").absolutePath}")
        return buildFile.readText()
    }

    @Test
    fun `the packaged runtime declares jdk net for the dbus unix socket`() {
        assertTrue(
            "nativeDistributions must include modules(\"jdk.net\")",
            Regex("""modules\s*\(\s*"jdk\.net"\s*\)""").containsMatchIn(buildFileText()),
        )
    }

    @Test
    fun `the packaged runtime declares jdk security auth for unix sasl`() {
        // dbus-java's SASL EXTERNAL auth calls com.sun.security.auth.module.UnixSystem. Without
        // jdk.security.auth the packaged app fails with NoClassDefFoundError before connecting.
        assertTrue(
            "nativeDistributions must include modules(\"jdk.security.auth\")",
            Regex("""modules\s*\(\s*"jdk\.security\.auth"\s*\)""").containsMatchIn(buildFileText()),
        )
    }
}
