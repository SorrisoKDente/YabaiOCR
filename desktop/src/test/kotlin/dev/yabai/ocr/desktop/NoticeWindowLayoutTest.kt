package dev.yabai.ocr.desktop

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the shape of the two notice windows and of the release-notes stack.
 *
 * A Compose `Window` without an explicit state gets the 800×600 dp default frame. On a display
 * scaled at 150–200% that left the content in one quarter of the window and three quarters blank,
 * which is exactly what the capture notice and the update notice looked like on Ubuntu. Drawing
 * needs a display, so the guard reads the sources — the same approach `ThemeWindowColorsTest` uses.
 */
class NoticeWindowLayoutTest {

    private fun sourceFile(name: String): File =
        sequenceOf(
            File("src/main/kotlin/dev/yabai/ocr/desktop/$name"),
            File("desktop/src/main/kotlin/dev/yabai/ocr/desktop/$name"),
        ).firstOrNull { it.isFile }
            ?: error("$name not found from ${File("").absolutePath}")

    @Test
    fun `notice windows fit their content instead of the default 800x600 frame`() {
        for (name in listOf("CaptureNoticeWindow.kt", "UpdatesNoticeWindow.kt")) {
            val source = sourceFile(name).readText()
            assertTrue(
                "$name must pack its window to the content",
                source.contains("rememberWindowState(size = DpSize.Unspecified)"),
            )
        }
    }

    @Test
    fun `the update notice has no scroll container an auto-sized window cannot measure`() {
        val source = sourceFile("UpdatesNoticeWindow.kt").readText()

        assertFalse("verticalScroll without a bounded height throws when the window packs", source.contains("verticalScroll"))
    }

    @Test
    fun `the notice surface wraps its content instead of filling the packed frame`() {
        // `fillMaxSize` made the first measure report the frame instead of the content, so the packed
        // window ended above the button: the pill appeared cut and without its label.
        for (name in listOf("CaptureNoticeWindow.kt", "UpdatesNoticeWindow.kt")) {
            val source = sourceFile(name).readText()
            assertFalse("$name must wrap its content", source.contains("Modifier.fillMaxSize()"))
        }
    }

    /**
     * The packed frame was measured before the download existed, so the progress bar, the installer
     * notice and the error line grew the column past it and were cut off at the bottom.
     */
    @Test
    fun `the update notice re-fits when a state arrives after it was packed`() {
        val source = sourceFile("UpdatesNoticeWindow.kt").readText()

        assertTrue(
            "the notice must re-pack when the download adds lines to the column",
            source.contains("noticeWindow.pack()"),
        )
        assertTrue(
            "the re-fit must follow the download states, not run once",
            Regex("""LaunchedEffect\([^)]*isDownloading""", RegexOption.DOT_MATCHES_ALL)
                .containsMatchIn(source),
        )
    }

    @Test
    fun `release notes open above the always-on-top update notice`() {
        val notes = sourceFile("ReleaseNotesWindow.kt").readText()
        assertTrue("the window must accept the flag", notes.contains("alwaysOnTop: Boolean = false"))
        assertTrue("and actually pass it to Window", notes.contains("alwaysOnTop = alwaysOnTop"))

        val main = sourceFile("Main.kt").readText()
        assertTrue(
            "the notes opened by the notice must be on top",
            Regex("""ReleaseNotesWindow\([^)]*alwaysOnTop = true""", RegexOption.DOT_MATCHES_ALL)
                .containsMatchIn(main),
        )
    }
}
