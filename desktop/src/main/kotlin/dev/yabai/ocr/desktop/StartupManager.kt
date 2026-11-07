package dev.yabai.ocr.desktop

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.io.File

object StartupManager {
    private const val APP_NAME = "YabaiOCR"
    private const val REG_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"

    fun setStartOnBoot(enable: Boolean) {
        val os = System.getProperty("os.name").lowercase()
        if (os.contains("win")) {
            setWindowsStartOnBoot(enable)
        }
    }

    fun isStartOnBootEnabled(): Boolean {
        val os = System.getProperty("os.name").lowercase()
        if (!os.contains("win")) return false
        return try {
            Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, REG_KEY, APP_NAME)
        } catch (_: Exception) {
            false
        }
    }

    private fun getExecutablePath(): String {
        val command = ProcessHandle.current().info().command().orElse(null)
        if (command != null) {
            val file = File(command)
            // Case 1: command is Yabai OCR.exe or any custom exe
            if (file.extension.equals("exe", ignoreCase = true) &&
                !file.name.equals("java.exe", ignoreCase = true) &&
                !file.name.equals("javaw.exe", ignoreCase = true)
            ) {
                return file.absolutePath
            }

            // Case 2: command is runtime/bin/javaw.exe inside jpackage app
            val jpackageParent = file.parentFile?.parentFile
            if (jpackageParent != null) {
                val jpackageExe = File(jpackageParent, "Yabai OCR.exe")
                if (jpackageExe.exists()) {
                    return jpackageExe.absolutePath
                }
            }
        }

        // Case 3: check current working directory
        val userDir = File(System.getProperty("user.dir"))
        val userDirExe = File(userDir, "Yabai OCR.exe")
        if (userDirExe.exists()) {
            return userDirExe.absolutePath
        }

        // Case 4: check standard installation locations
        val localAppData = System.getenv("LOCALAPPDATA")
        if (localAppData != null) {
            val localExe = File(localAppData, "Programs\\Yabai OCR\\Yabai OCR.exe")
            if (localExe.exists()) return localExe.absolutePath
        }

        val pf = System.getenv("ProgramFiles")
        if (pf != null) {
            val pfExe = File(pf, "Yabai OCR\\Yabai OCR.exe")
            if (pfExe.exists()) return pfExe.absolutePath
        }

        // Fallback: default expected executable in user directory
        return File(userDir, "Yabai OCR.exe").absolutePath
    }

    private fun setWindowsStartOnBoot(enable: Boolean) {
        try {
            if (enable) {
                val exePath = getExecutablePath()
                val formattedCmd = "\"$exePath\" --minimized"
                Advapi32Util.registrySetStringValue(
                    WinReg.HKEY_CURRENT_USER,
                    REG_KEY,
                    APP_NAME,
                    formattedCmd
                )
            } else {
                if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, REG_KEY, APP_NAME)) {
                    Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, REG_KEY, APP_NAME)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
