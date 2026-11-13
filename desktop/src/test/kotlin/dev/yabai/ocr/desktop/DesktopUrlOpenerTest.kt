package dev.yabai.ocr.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The «Ask AI» button only ever reported success by opening nothing: `Desktop.browse` throws inside
 * an AppImage on SteamOS and the exception was swallowed. These tests pin the launcher order and the
 * failure signal the result window needs to tell the user something happened.
 */
class DesktopUrlOpenerTest {

    private fun opener(
        calls: MutableList<List<String>> = mutableListOf(),
        successful: (List<String>) -> Boolean = { false },
        browse: (String) -> Boolean = { false },
        commands: List<List<String>> = listOf(listOf("xdg-open"), listOf("kde-open6")),
    ) = DesktopUrlOpener(
        isLinux = true,
        isWindows = false,
        browse = browse,
        runCommand = { command ->
            calls += command
            successful(command)
        },
        commands = commands,
    )

    @Test
    fun `the url is appended as the last argument of each launcher`() {
        val calls = mutableListOf<List<String>>()

        opener(calls = calls).open("https://chatgpt.com/?q=hello")

        assertEquals(
            listOf(
                listOf("xdg-open", "https://chatgpt.com/?q=hello"),
                listOf("kde-open6", "https://chatgpt.com/?q=hello"),
            ),
            calls,
        )
    }

    @Test
    fun `it stops at the first launcher that works`() {
        val calls = mutableListOf<List<String>>()

        val opened = opener(
            calls = calls,
            successful = { it.first() == "kde-open6" },
        ).open("https://example.com")

        assertTrue(opened)
        assertEquals(2, calls.size)
    }

    @Test
    fun `a missing launcher falls through to the next one`() {
        // `start()` throws for a binary that is not installed; the opener treats that as "try the
        // next", so the order above is what makes the button work on a bare SteamOS session.
        val calls = mutableListOf<List<String>>()
        val opened = opener(
            calls = calls,
            successful = { it.first() == "sensible-browser" },
            commands = listOf(listOf("xdg-open"), listOf("gio", "open"), listOf("sensible-browser")),
        ).open("https://example.com")

        assertTrue(opened)
        assertEquals(listOf("xdg-open", "gio", "sensible-browser"), calls.map { it.first() })
    }

    @Test
    fun `without any launcher the desktop browse is still tried`() {
        var browsed: String? = null

        val opened = opener(
            browse = { url -> browsed = url; true },
            commands = emptyList(),
        ).open("https://example.com")

        assertTrue(opened)
        assertEquals("https://example.com", browsed)
    }

    @Test
    fun `when nothing can open the url the caller is told`() {
        assertFalse(opener().open("https://example.com"))
    }

    @Test
    fun `windows keeps the native browse first and rundll32 as the fallback`() {
        assertEquals(
            listOf(listOf("rundll32", "url.dll,FileProtocolHandler")),
            launcherCommands(isLinux = false, isWindows = true),
        )

        val calls = mutableListOf<List<String>>()
        val opener = DesktopUrlOpener(
            isLinux = false,
            isWindows = true,
            browse = { false },
            runCommand = { command -> calls += command; true },
        )

        assertTrue(opener.open("https://example.com"))
        assertEquals(listOf(listOf("rundll32", "url.dll,FileProtocolHandler", "https://example.com")), calls)
    }
}
