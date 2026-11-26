package dev.yabai.ocr.desktop

import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HoverCapabilityTest {

    @Test
    fun `windows and x11 keep the awt pointer`() {
        assertEquals(
            HoverBackendKind.AWT,
            hoverBackendKindFor(OsFamily.WINDOWS, SessionType.UNKNOWN, DesktopFamily.UNKNOWN),
        )
        assertEquals(
            HoverBackendKind.AWT,
            hoverBackendKindFor(OsFamily.LINUX, SessionType.X11, DesktopFamily.KDE),
        )
        assertEquals(
            HoverBackendKind.AWT,
            hoverBackendKindFor(OsFamily.MACOS, SessionType.UNKNOWN, DesktopFamily.UNKNOWN),
        )
    }

    @Test
    fun `kde wayland uses the kwin bridge`() {
        assertEquals(
            HoverBackendKind.KWIN,
            hoverBackendKindFor(OsFamily.LINUX, SessionType.WAYLAND, DesktopFamily.KDE),
        )
    }

    @Test
    fun `gnome wayland uses the shell extension bridge`() {
        assertEquals(
            HoverBackendKind.GNOME,
            hoverBackendKindFor(OsFamily.LINUX, SessionType.WAYLAND, DesktopFamily.GNOME),
        )
    }

    @Test
    fun `wayland without a bridge is unsupported instead of polling a dead pointer`() {
        assertEquals(
            HoverBackendKind.UNSUPPORTED,
            hoverBackendKindFor(OsFamily.LINUX, SessionType.WAYLAND, DesktopFamily.OTHER),
        )
        assertEquals(
            HoverBackendKind.UNSUPPORTED,
            hoverBackendKindFor(OsFamily.LINUX, SessionType.WAYLAND, DesktopFamily.UNKNOWN),
        )
    }

    @Test
    fun `every backend has a distinct non-empty message`() {
        val labels = HoverBackendKind.entries.map(::hoverBackendLabel)

        assertTrue(labels.all { it.isNotBlank() })
        assertEquals(labels.size, labels.toSet().size)
        assertTrue(hoverBackendLabel(HoverBackendKind.UNSUPPORTED).contains("indisponível"))
        assertTrue(hoverBackendLabel(HoverBackendKind.GNOME).contains("GNOME"))
    }

    /** R57: the hover answers with dictionaries, so it says so instead of coming up empty. */
    @Test
    fun `hover refuses to start with no dictionary and says why`() {
        val refusal = hoverDictionaryRefusal(emptyList())

        assertNotNull("the hover would start with nothing to answer with", refusal)
        assertTrue(refusal!!.contains("dicionário"))
        assertNull(hoverDictionaryRefusal(listOf("Jitendex")))
    }

    /** The refusal is only a notice if turning the switch on actually asks the question. */
    @Test
    fun `the hover asks for a dictionary before it starts`() {
        val source = listOf(
            File("src/main/kotlin/dev/yabai/ocr/desktop/HoverOcrManager.kt"),
            File("desktop/src/main/kotlin/dev/yabai/ocr/desktop/HoverOcrManager.kt"),
        ).first { it.exists() }.readText()

        assertTrue(
            "the hover starts with no dictionary to answer with",
            source.contains("hoverDictionaryRefusal(dictionaryTitles())"),
        )
        // Asking to keep the hover on must not re-read the dictionaries and refuse: that would
        // switch the UI off while the pointer is still being watched.
        assertTrue(
            "an already-enabled hover is re-checked against the dictionaries before anything else",
            source.indexOf("if (isEnabled == enabled) return isEnabled") in 0 until
                source.indexOf("hoverDictionaryRefusal(dictionaryTitles())"),
        )
        assertTrue(
            "the refusal reason is not kept synchronously for the notice",
            source.contains("var lastRefusal: String?") &&
                source.contains("lastRefusal = reason"),
        )

        val main = listOf(
            File("src/main/kotlin/dev/yabai/ocr/desktop/Main.kt"),
            File("desktop/src/main/kotlin/dev/yabai/ocr/desktop/Main.kt"),
        ).first { it.exists() }.readText()
        assertTrue(
            "the hover notice reads the asynchronous status line instead of the refusal reason",
            main.contains("message = hoverOcrManager.lastRefusal ?: hoverState.status"),
        )
    }

    /**
     * R59: the missing-dictionary notice is the one place the user is told what to download, so it
     * carries the way there instead of leaving them to find Ajustes → Dicionários.
     */
    @Test
    fun `the no-dictionary notice opens the installed dictionaries`() {
        fun source(name: String) = listOf(
            File("src/main/kotlin/dev/yabai/ocr/desktop/$name"),
            File("desktop/src/main/kotlin/dev/yabai/ocr/desktop/$name"),
        ).first { it.exists() }.readText()

        val manager = source("HoverOcrManager.kt")
        assertTrue(
            "the missing-dictionary refusal is not marked for the notice",
            manager.contains("lastRefusalNeedsDictionaries = true"),
        )

        val main = source("Main.kt")
        assertTrue(
            "the notice does not carry the refusal that offers the dictionary action",
            main.contains("mutableStateOf<HoverNotice?>(null)") &&
                main.contains("offersDictionaries = hoverOcrManager.lastRefusalNeedsDictionaries") &&
                main.contains("action = if (hoverMessage.offersDictionaries)") &&
                main.contains("NoticeAction(DesktopStrings.hoverOpenDictionaries)"),
        )
        assertTrue(
            "the notice's action does not open the installed dictionaries",
            main.contains("mainDictInitialTab = DICTIONARY_TAB_INSTALLED") &&
                main.contains("mainScreen = MainScreen.DICTIONARIES") &&
                main.contains("showMainWindow = true") &&
                main.contains("mainWindowRequest++"),
        )

        val notice = source("CaptureNoticeWindow.kt")
        assertTrue(
            "the notice window cannot draw an action button",
            notice.contains("action: NoticeAction?") &&
                notice.contains("OutlinedButton(onClick = action.onClick)"),
        )

        val dictionaryScreen = source("DictionaryManagerWindow.kt")
        assertTrue(
            "the dictionary screen cannot open on the installed tab",
            dictionaryScreen.contains("initialTab: Int = DICTIONARY_TAB_QUERY") &&
                dictionaryScreen.contains("remember(initialTab)"),
        )
    }

    @Test
    fun `only kwin replaces the robot with the screencast capture`() {
        val capture: (Rectangle) -> BufferedImage = { BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB) }

        assertNull(hoverCaptureProvider(HoverBackendKind.AWT, capture))
        assertNull(hoverCaptureProvider(HoverBackendKind.UNSUPPORTED, capture))
        assertNotNull(hoverCaptureProvider(HoverBackendKind.KWIN, capture))
        assertNotNull("GNOME also captures through the portal, not Robot", hoverCaptureProvider(HoverBackendKind.GNOME, capture))
    }
}
