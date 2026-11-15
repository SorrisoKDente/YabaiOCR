package dev.yabai.ocr.desktop

import java.io.File

/**
 * "Integrar ao sistema" on Linux: installs the application entry and the PNG icon inside the
 * user's XDG data folder. Nothing is written to `/usr`, so it works on the immutable SteamOS root.
 */
internal class LinuxSystemIntegration(
    private val dataHome: File = defaultDataHome(),
    private val executableResolver: () -> String? = { resolveLinuxExecutable() },
    private val runningExecutable: () -> String? = { runningExecutablePath() },
    private val iconBytes: () -> ByteArray? = {
        LinuxSystemIntegration::class.java
            .getResourceAsStream("/ic_scan_linux.png")
            ?.use { it.readBytes() }
    },
) {
    val desktopFile: File
        get() = File(File(dataHome, "applications"), "yabai-ocr.desktop")

    val iconFile: File
        get() = File(File(dataHome, "icons/hicolor/256x256/apps"), "yabai-ocr.png")

    val authorizationDesktopFile: File
        get() = File(File(dataHome, "applications"), "yabai-ocr-screenshot.desktop")

    /**
     * Rewrites the hidden entry KWin uses to authorize `org.kde.KWin.ScreenShot2`.
     *
     * This runs on every start because the entry must name the binary that is running right now: an
     * AppImage extracts itself to a new `/tmp/.mount_*` folder each launch, so a stale entry would
     * authorize nothing. Failure is not fatal — capture then goes through the portal, with its
     * permission window.
     */
    fun ensureScreenshotAuthorization(): Boolean {
        val executable = runningExecutable()?.takeIf { it.isNotBlank() } ?: return false
        return runCatching {
            authorizationDesktopFile.parentFile?.mkdirs()
            val content = screenshotAuthorizationDesktopEntry(executable)
            if (!authorizationDesktopFile.isFile || authorizationDesktopFile.readText() != content) {
                authorizationDesktopFile.writeText(content)
            }
            true
        }.getOrDefault(false)
    }

    /**
     * Writes [iconFile] on every start and keeps an existing menu entry current.
     *
     * The StatusNotifier item names the icon (`IconName`), and the AppIndicator host resolves that
     * name through the icon theme: with no file installed it fell back to its generic gear.
     *
     * The entry itself is only refreshed when it already exists — creating one is what "Integrar ao
     * sistema" is for — but a stale entry is why the dock kept the gear: it named an older AppImage
     * and predated `StartupWMClass`, so the shell could not link the window to it. The launcher path
     * stays the user-facing one ([executableResolver]); the `/tmp/.mount_*` path of the running
     * AppImage would stop existing when the app closes.
     */
    fun ensureTrayIcon(): Boolean {
        val icon = runCatching(iconBytes).getOrNull()?.takeIf { it.isNotEmpty() } ?: return false
        val iconWritten = runCatching {
            if (iconFile.isFile && iconFile.readBytes().contentEquals(icon)) return@runCatching true
            iconFile.parentFile?.mkdirs()
            iconFile.writeBytes(icon)
            true
        }.getOrDefault(false)

        val executable = executableResolver()?.takeIf { it.isNotBlank() }
        if (executable != null && desktopFile.isFile) {
            runCatching {
                val content = applicationDesktopEntry(executable)
                if (desktopFile.readText() != content) desktopFile.writeText(content)
            }
        }

        return iconWritten
    }

    /** Writes both files atomically enough for the menu to pick them up on its next refresh. */
    fun install(): Boolean {
        val executable = executableResolver()?.takeIf { it.isNotBlank() } ?: return false
        val icon = runCatching(iconBytes).getOrNull()?.takeIf { it.isNotEmpty() } ?: return false

        return runCatching {
            desktopFile.parentFile?.mkdirs()
            desktopFile.writeText(applicationDesktopEntry(executable))
            iconFile.parentFile?.mkdirs()
            iconFile.writeBytes(icon)
            true
        }.getOrDefault(false)
    }
}
