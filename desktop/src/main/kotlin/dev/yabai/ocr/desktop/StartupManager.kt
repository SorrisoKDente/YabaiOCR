package dev.yabai.ocr.desktop

object StartupManager {
    private const val APP_NAME = "YabaiOCR"

    fun setStartOnBoot(enable: Boolean) {
        val os = System.getProperty("os.name").lowercase()
        if (os.contains("win")) {
            setWindowsStartOnBoot(enable)
        }
    }

    private fun setWindowsStartOnBoot(enable: Boolean) {
        try {
            val command = ProcessHandle.current().info().command().orElse(null) ?: return
            val regKey = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
            if (enable) {
                val dVal = "\"$command\" --minimized"
                ProcessBuilder("reg", "add", regKey, "/v", APP_NAME, "/t", "REG_SZ", "/d", dVal, "/f")
                    .start()
                    .waitFor()
            } else {
                ProcessBuilder("reg", "delete", regKey, "/v", APP_NAME, "/f")
                    .start()
                    .waitFor()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
