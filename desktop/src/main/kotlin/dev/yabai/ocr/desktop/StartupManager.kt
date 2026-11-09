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

    /**
     * Called once in [main], before any window: the logon entry has to match the preference even
     * when Windows starts the app with `--minimized` and no settings window is ever created.
     */
    fun reconcileStartOnBoot(enabled: Boolean): Boolean = PlatformServices.autostart.reconcile(enabled)
}
