package dev.yabai.ocr.desktop

/**
 * Public facade kept for [Main] and [SettingsWindow]. The implementation is chosen once by
 * [PlatformServices]: registry on Windows, XDG autostart on Linux, no-op elsewhere.
 */
object StartupManager {
    fun setStartOnBoot(enable: Boolean) {
        PlatformServices.autostart.setEnabled(enable)
    }

    fun isStartOnBootEnabled(): Boolean = PlatformServices.autostart.isEnabled()
}
