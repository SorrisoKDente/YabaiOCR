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
