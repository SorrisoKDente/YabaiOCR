package dev.yabai.ocr.desktop

import dev.yabai.ocr.desktop.i18n.DesktopStrings
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R60: a Windows update ends in a separate installer, and the download has to say where it stands.
 *
 * The percentage used to live only inside the button label; the end of the download read like the
 * update was already applied. Both the notice window and the About tab download the same package, so
 * both must draw the bar and the «continue in the installer» notice.
 *
 * Drawing a Compose window needs a display, so the wiring guard reads the source — the approach
 * [BackBarIconTest] uses — and the text assertions cover both interface languages.
 */
class UpdateDownloadNoticeTest {

    @Test
    fun `both update entrances draw the progress bar and the installer notice`() {
        listOf("UpdatesNoticeWindow.kt", "SettingsWindow.kt").forEach { name ->
            val source = sourceFile(name).readText()

            assertTrue(
                "$name no longer shows the download progress",
                Regex("""UpdateDownloadProgress\(\s*(progress|installProgress)\s*\)""")
                    .containsMatchIn(source),
            )
            assertTrue(
                "$name no longer shows the «continue in the installer» notice",
                Regex("""UpdateInstallerNotice\(it\)""").containsMatchIn(source),
            )
        }
    }

    /**
     * R62: once the installer is open the update is out of the app's hands. Leaving the button
     * enabled let a second click download and launch a second installer while the first one ran.
     */
    @Test
    fun `an open installer does not offer a second download`() {
        listOf(
            "UpdatesNoticeWindow.kt" to "isDownloading",
            "SettingsWindow.kt" to "isInstallingUpdate",
        ).forEach { (name, busy) ->
            val source = sourceFile(name).readText()

            assertTrue(
                "$name still offers a download while the installer is open",
                Regex("""enabled = !$busy && readyToRestart == null && installerNotice == null""")
                    .containsMatchIn(source),
            )
        }
    }

    @Test
    fun `the download percentage is shown while it runs`() {
        val previous = DesktopStrings.currentChoice
        try {
            DesktopStrings.updateLanguage(DesktopLanguageChoice.PT_BR)
            assertTrue(
                "the Portuguese download line lost the percentage",
                DesktopStrings.updateDownloading(37).contains("37%"),
            )
            assertTrue(
                "the Portuguese download line lost its meaning",
                DesktopStrings.updateDownloading(null).contains("Baixando"),
            )

            DesktopStrings.updateLanguage(DesktopLanguageChoice.EN_US)
            assertTrue(
                "the English download line lost the percentage",
                DesktopStrings.updateDownloading(37).contains("37%"),
            )
            assertTrue(
                "the English download line lost its meaning",
                DesktopStrings.updateDownloading(null).contains("Downloading"),
            )
        } finally {
            DesktopStrings.updateLanguage(previous)
        }
    }

    @Test
    fun `a finished download tells the user to continue in the installer`() {
        val previous = DesktopStrings.currentChoice
        try {
            DesktopStrings.updateLanguage(DesktopLanguageChoice.PT_BR)
            assertTrue(
                "the Portuguese notice does not send the user to the installer",
                DesktopStrings.updateInstallerLaunched("1.2.0").contains("Continue no instalador"),
            )

            DesktopStrings.updateLanguage(DesktopLanguageChoice.EN_US)
            assertTrue(
                "the English notice does not send the user to the installer",
                DesktopStrings.updateInstallerLaunched("1.2.0").contains("Continue in the"),
            )
        } finally {
            DesktopStrings.updateLanguage(previous)
        }
    }

    private fun sourceFile(name: String): File =
        sequenceOf(
            File("src/main/kotlin/dev/yabai/ocr/desktop/$name"),
            File("desktop/src/main/kotlin/dev/yabai/ocr/desktop/$name"),
        ).firstOrNull { it.isFile }
            ?: error("$name not found from ${File(".").absolutePath}")
}
