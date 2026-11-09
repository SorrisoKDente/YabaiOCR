package dev.yabai.ocr.desktop

import java.io.File

/**
 * "Integrar ao sistema" on Linux: installs the application entry and the PNG icon inside the
 * user's XDG data folder. Nothing is written to `/usr`, so it works on the immutable SteamOS root.
 */
internal class LinuxSystemIntegration(
    private val dataHome: File = defaultDataHome(),
    private val executableResolver: () -> String? = { resolveLinuxExecutable() },
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
