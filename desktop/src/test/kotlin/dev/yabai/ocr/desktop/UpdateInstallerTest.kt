package dev.yabai.ocr.desktop

import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path

class UpdateInstallerTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `the running appimage is read from the runtime environment`() {
        val path = UpdateInstaller.appImagePath(mapOf("APPIMAGE" to "/home/me/Yabai_OCR.AppImage"))

        assertEquals(Path.of("/home/me/Yabai_OCR.AppImage"), path)
        assertNull(UpdateInstaller.appImagePath(emptyMap()))
        assertNull(UpdateInstaller.appImagePath(mapOf("APPIMAGE" to "  ")))
    }

    @Test
    fun `a download name loses the query and never comes back empty`() {
        assertEquals(
            "Yabai_OCR-1.1.0-x86_64.AppImage",
            UpdateInstaller.fileNameFor("https://github.com/x/y/releases/download/v1.1.0/Yabai_OCR-1.1.0-x86_64.AppImage"),
        )
        assertEquals("file.exe", UpdateInstaller.fileNameFor("https://example.com/a/file.exe?token=1"))
        assertEquals("update.bin", UpdateInstaller.fileNameFor("https://example.com/"))
    }

    @Test
    fun `only an appimage build replaces itself, the rest opens the release page`() {
        val appImage = "/home/me/Yabai.AppImage"

        assertEquals(
            UpdatePlan.AppImage(Path.of(appImage)),
            UpdateInstaller.plan(OsFamily.LINUX, "https://x/y.AppImage", mapOf("APPIMAGE" to appImage)),
        )
        // An unpacked build has no single file to swap, so it must not pretend to self-update.
        assertNull(UpdateInstaller.plan(OsFamily.LINUX, "https://x/y.AppImage", emptyMap()))
        // No asset at all is also a «open the release page» outcome.
        assertNull(UpdateInstaller.install(null, OsFamily.LINUX, mapOf("APPIMAGE" to appImage)))
        assertEquals(
            UpdatePlan.Installer(Path.of(System.getProperty("java.io.tmpdir"), "setup.exe")),
            UpdateInstaller.plan(OsFamily.WINDOWS, "https://x/setup.exe", emptyMap()),
        )
    }

    /**
     * R62: an MSI is not an executable, and the release can publish one instead of an EXE.
     *
     * `ProcessBuilder("…msi")` fails with error 193, so a downloaded update looked broken at the
     * moment it was supposed to install. The fallback command names `msiexec` (and `open` on macOS).
     */
    @Test
    fun `an msi installer is handed to msiexec and a dmg to the system opener`() {
        assertEquals(
            listOf("msiexec", "/i", "C:\\Temp\\Yabai_OCR-1.2.0.msi"),
            UpdateInstaller.installerCommand(
                OsFamily.WINDOWS,
                Path.of("C:\\Temp\\Yabai_OCR-1.2.0.msi"),
            ),
        )
        val dmg = Path.of("/tmp/Yabai_OCR-1.2.0.dmg")
        assertEquals(
            listOf("open", dmg.toString()),
            UpdateInstaller.installerCommand(OsFamily.MACOS, dmg),
        )
        assertEquals(
            listOf("C:\\Temp\\Yabai_OCR-1.2.0.exe"),
            UpdateInstaller.installerCommand(
                OsFamily.WINDOWS,
                Path.of("C:\\Temp\\Yabai_OCR-1.2.0.exe"),
            ),
        )
    }

    @Test
    fun `replacing keeps the old file as a backup and leaves the new one executable`() {
        val running = temp.newFile("Yabai_OCR.AppImage").toPath()
        Files.writeString(running, "old build")
        val downloaded = temp.newFile("Yabai_OCR.AppImage.update").toPath()
        Files.writeString(downloaded, "new build")

        val backup = UpdateInstaller.replaceAppImage(running, downloaded)

        assertEquals("new build", Files.readString(running))
        assertEquals("old build", Files.readString(backup))
        assertTrue("the replacement must be executable", running.toFile().canExecute())
        assertFalse("the temp file must not linger", Files.exists(downloaded))
    }

    /** A loopback server keeps the download path testable without touching the real network. */
    private fun withServer(
        path: String,
        declaredLength: Long,
        payload: ByteArray,
        body: (String) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext(path) { exchange ->
            exchange.sendResponseHeaders(200, declaredLength)
            exchange.responseBody.use { it.write(payload) }
        }
        server.start()
        try {
            body("http://127.0.0.1:${server.address.port}$path")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `a download writes the whole file and reports progress`() {
        val payload = ByteArray(200_000) { (it % 251).toByte() }

        withServer("/update", payload.size.toLong(), payload) { url ->
            val target = temp.root.toPath().resolve("downloaded.AppImage")
            val percents = mutableListOf<Int?>()

            UpdateInstaller.download(url, target) { percents += it }

            assertArrayEquals(payload, Files.readAllBytes(target))
            assertEquals(100, percents.filterNotNull().last())
            assertFalse("the partial file must be gone", Files.exists(target.resolveSibling("downloaded.AppImage.part")))
        }
    }

    @Test
    fun `a truncated download is refused and leaves nothing behind`() {
        // Declares 50 kB but sends 1 kB: what an interrupted transfer looks like.
        withServer("/broken", 50_000, ByteArray(1_000)) { url ->
            val target = temp.root.toPath().resolve("downloaded.AppImage")

            val error = runCatching { UpdateInstaller.download(url, target) }.exceptionOrNull()

            assertTrue("an incomplete download must fail", error != null)
            assertFalse(Files.exists(target))
            assertFalse(Files.exists(target.resolveSibling("downloaded.AppImage.part")))
        }
    }

    @Test
    fun `a failed swap puts the old build back`() {
        val running = temp.newFile("Yabai_OCR.AppImage").toPath()
        Files.writeString(running, "old build")
        val missing = temp.root.toPath().resolve("never-downloaded")

        val error = runCatching { UpdateInstaller.replaceAppImage(running, missing) }.exceptionOrNull()

        assertTrue(error != null)
        assertEquals("old build", Files.readString(running))
        assertFalse(Files.exists(running.resolveSibling("Yabai_OCR.AppImage.old")))
    }

    @Test
    fun `the update keeps the release file name, not the running one`() {
        val running = Path.of("/home/me/Yabai_OCR-1.0.7-x86_64.AppImage")

        assertEquals(
            Path.of("/home/me/Yabai_OCR-1.0.8-x86_64.AppImage"),
            UpdateInstaller.installedPath(running, "https://x/Yabai_OCR-1.0.8-x86_64.AppImage?tag=v1.0.8"),
        )
        // A re-download of the same tag cannot be written over the running file, so it goes beside it.
        assertEquals(
            Path.of("/home/me/Yabai_OCR-1.0.7-x86_64.AppImage.update"),
            UpdateInstaller.updateTarget(running, "https://x/Yabai_OCR-1.0.7-x86_64.AppImage"),
        )
        assertEquals(
            Path.of("/home/me/Yabai_OCR-1.0.8-x86_64.AppImage"),
            UpdateInstaller.updateTarget(running, "https://x/Yabai_OCR-1.0.8-x86_64.AppImage"),
        )
    }

    @Test
    fun `installing a newer release renames the file to the new version`() {
        val dir = temp.newFolder("release").toPath()
        val running = dir.resolve("Yabai_OCR-1.0.7-x86_64.AppImage")
        Files.writeString(running, "old build")
        val payload = "new build".toByteArray()

        withServer("/Yabai_OCR-1.0.8-x86_64.AppImage", payload.size.toLong(), payload) { url ->
            val outcome = UpdateInstaller.install(
                url = url,
                family = OsFamily.LINUX,
                environment = mapOf("APPIMAGE" to running.toString()),
            )

            val replaced = outcome as UpdateOutcome.Replaced
            assertEquals(dir.resolve("Yabai_OCR-1.0.8-x86_64.AppImage"), replaced.appImage)
            assertEquals("new build", Files.readString(replaced.appImage))
            assertTrue("the new build must be executable", replaced.appImage.toFile().canExecute())
            assertEquals(
                "old build",
                Files.readString(dir.resolve("Yabai_OCR-1.0.7-x86_64.AppImage.old")),
            )
            assertFalse("the old name must not come back", Files.exists(running))
        }
    }

    @Test
    fun `the relaunch does not inherit the running appimage mount`() {
        val clean = UpdateInstaller.relaunchEnvironment(
            mapOf(
                "APPIMAGE" to "/home/me/Yabai_OCR-1.0.7.AppImage",
                "APPDIR" to "/tmp/.mount_Yabai_abc",
                "APPDIR_LIBRARY_PATH" to "/tmp/.mount_Yabai_abc/lib",
                "ARGV0" to "/home/me/Yabai_OCR-1.0.7.AppImage",
                "LD_LIBRARY_PATH" to "/tmp/.mount_Yabai_abc/usr/lib",
                "DISPLAY" to ":0",
                "WAYLAND_DISPLAY" to "wayland-0",
            ),
        )

        assertEquals(setOf("DISPLAY", "WAYLAND_DISPLAY"), clean.keys)
        assertEquals(":0", clean["DISPLAY"])
    }
}
