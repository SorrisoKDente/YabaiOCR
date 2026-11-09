package dev.yabai.ocr.desktop

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The packaged Linux runtime is a jlink image, not the developer JDK.
 *
 * dbus-java's native unix-socket transport requires `jdk.net` (it reads
 * `ExtendedSocketOptions.SO_PEERCRED`). When the module is missing, the D-Bus service, the KWin
 * bridge and the portal shortcuts all fail with `NoClassDefFoundError` at runtime — visible only
 * on Linux and only in the packaged build. This guard keeps the module in `nativeDistributions`.
 */
class NativeRuntimeModulesTest {

    @Test
    fun `the packaged runtime declares jdk net for the dbus unix socket`() {
        val buildFile = sequenceOf(File("build.gradle.kts"), File("desktop/build.gradle.kts"))
            .firstOrNull { it.isFile }
            ?: error("desktop/build.gradle.kts not found from ${File(".").absolutePath}")

        val text = buildFile.readText()

        assertTrue(
            "nativeDistributions must include modules(\"jdk.net\")",
            Regex("""modules\s*\(\s*"jdk\.net"\s*\)""").containsMatchIn(text),
        )
    }
}
