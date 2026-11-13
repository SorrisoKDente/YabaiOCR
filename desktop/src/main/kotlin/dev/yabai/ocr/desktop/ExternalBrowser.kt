package dev.yabai.ocr.desktop

import java.awt.Desktop
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Opens a URL in the user's browser.
 *
 * `java.awt.Desktop.browse` is the portable call, but it is exactly the piece that fails inside an
 * AppImage on SteamOS: the bundled JDK's X11 desktop peer needs a working `gio`/`xdg-open` bridge and
 * a launched-from-desktop context, and when it fails it throws — the old code swallowed that in
 * `runCatching`, so the «Ask AI» button simply did nothing. The launchers below are the desktop's own
 * tools; the AWT call stays as the last resort.
 */
internal class DesktopUrlOpener(
    private val isLinux: Boolean = DesktopPlatform.isLinux,
    private val isWindows: Boolean = DesktopPlatform.isWindows,
    private val browse: (String) -> Boolean = ::browseWithDesktop,
    private val runCommand: (List<String>) -> Boolean = ::runLauncher,
    private val commands: List<List<String>> = launcherCommands(isLinux, isWindows),
) {

    /** True when some launcher accepted the URL. */
    fun open(url: String): Boolean {
        // On Linux the desktop's own launcher is tried first: it is what the session actually uses,
        // and it does not depend on the JDK finding libgio. On Windows `Desktop.browse` is native.
        if (isLinux) {
            if (commands.any { runCommand(it + url) }) return true
            return browse(url)
        }
        if (browse(url)) return true
        return commands.any { runCommand(it + url) }
    }
}

/**
 * Launchers that exist on the platforms the desktop app targets, in the order they are tried.
 *
 * Each one is a full command prefix: the URL is appended as the last argument.
 */
internal fun launcherCommands(isLinux: Boolean, isWindows: Boolean): List<List<String>> = when {
    isLinux -> listOf(
        listOf("xdg-open"),
        listOf("gio", "open"),
        listOf("kde-open6"),
        listOf("kde-open5"),
        listOf("sensible-browser"),
    )
    isWindows -> listOf(listOf("rundll32", "url.dll,FileProtocolHandler"))
    else -> emptyList()
}

private fun browseWithDesktop(url: String): Boolean = try {
    if (!Desktop.isDesktopSupported()) {
        false
    } else {
        val desktop = Desktop.getDesktop()
        if (!desktop.isSupported(Desktop.Action.BROWSE)) {
            false
        } else {
            desktop.browse(URI.create(url))
            true
        }
    }
} catch (_: Throwable) {
    // A failed browse must fall through to the launchers, never reach the UI as an exception.
    false
}

/**
 * Runs one launcher and reports whether it exited successfully.
 *
 * A missing binary throws `IOException` from `start()`, which is the expected way to learn that this
 * launcher does not exist on the machine. Output is discarded and the wait is bounded: `xdg-open`
 * returns immediately, but a wrapper that never exits must not keep the caller blocked.
 */
private fun runLauncher(command: List<String>): Boolean {
    val process = try {
        ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
    } catch (_: Throwable) {
        return false
    }
    return try {
        if (process.waitFor(LAUNCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.exitValue() == 0
        } else {
            process.destroy()
            false
        }
    } catch (_: Throwable) {
        runCatching { process.destroy() }
        false
    }
}

private const val LAUNCH_TIMEOUT_SECONDS = 4L
