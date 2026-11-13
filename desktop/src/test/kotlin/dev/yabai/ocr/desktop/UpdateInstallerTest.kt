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
            UpdateOutcome.Replaced(Path.of(appImage)),
            UpdateInstaller.plan(OsFamily.LINUX, "https://x/y.AppImage", mapOf("APPIMAGE" to appImage)),
        )
        // An unpacked build has no single file to swap, so it must not pretend to self-update.
        assertNull(UpdateInstaller.plan(OsFamily.LINUX, "https://x/y.AppImage", emptyMap()))
        assertNull(UpdateInstaller.plan(OsFamily.LINUX, null, mapOf("APPIMAGE" to appImage)))
        assertTrue(UpdateInstaller.plan(OsFamily.WINDOWS, "https://x/setup.exe", emptyMap()) != null)
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
}
