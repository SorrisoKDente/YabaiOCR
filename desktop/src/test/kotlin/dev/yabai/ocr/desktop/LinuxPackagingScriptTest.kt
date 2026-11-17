package dev.yabai.ocr.desktop

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Linux packaging path depends on two shell scripts, and both break before Gradle even starts
 * if a checkout hands them CRLF line endings: `gradlew` fails with
 * "cannot execute: required file not found" (the kernel looks for `/bin/sh\r`) and bash answers
 * `$'\r': command not found` / `set: pipefail\r: invalid option name`. Neither failure is visible
 * from Kotlin, so the scripts are asserted here instead of being discovered at packaging time.
 *
 * Paths are relative to the module directory, which is where Gradle runs the tests from.
 */
class LinuxPackagingScriptTest {
    private fun script(path: String): String {
        val file = File(path)
        assertTrue("$path not found (cwd ${File(".").absolutePath})", file.isFile)
        return file.readText()
    }

    @Test
    fun `the build scripts are lf only`() {
        for (path in listOf("../gradlew", "package/linux/build-appimage.sh")) {
            assertFalse(
                "$path must stay LF-only: a CRLF shebang makes it unrunnable on Linux",
                script(path).contains('\r'),
            )
        }
    }

    @Test
    fun `the appimage script makes the app dir readable before packaging`() {
        val text = script("package/linux/build-appimage.sh")
        // A shared folder (vboxsf/SMB) or a 007 umask leaves the copies at 0770, and appimagetool
        // refuses to package files that other users cannot read.
        val chmod = text.indexOf("chmod -R a+rX \"\$APPDIR\"")
        assertTrue("must chmod the AppDir before appimagetool", chmod >= 0)
        assertTrue(
            "the chmod has to run before appimagetool reads the AppDir",
            chmod < text.indexOf("ARCH=x86_64"),
        )
    }
}
