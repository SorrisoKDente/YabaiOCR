package dev.yabai.ocr.desktop

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import java.io.File

private const val APP_NAME = "YabaiOCR"
private const val REG_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run"
private const val LAUNCHER_NAME = "Yabai OCR.exe"
private const val MINIMIZED_FLAG = "--minimized"

/**
 * The `Run` key is the only thing Windows executes at logon. The "Startup apps" page also reads
 * `StartupApproved\Run`, so an entry that was deleted, or that points at an executable that moved,
 * still looks enabled there. That is why [WindowsAutostart.isEnabled] checks the command target and
 * [Autostart] reconciles the key with the stored preference instead of trusting the Windows UI.
 *
 * Kept behind this port so tests never write the real `HKCU\...\Run` (a test that writes and then
 * deletes the value leaves the orphan `StartupApproved` flag behind, which is exactly the state the
 * user sees as "enabled in Windows but nothing starts").
 */
internal interface AutostartRegistry {
    fun read(name: String): String?
    fun write(name: String, command: String)
    fun delete(name: String)
}

internal object HkcuRunRegistry : AutostartRegistry {
    override fun read(name: String): String? = try {
        if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, REG_KEY, name)) {
            Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, REG_KEY, name)
        } else {
            null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    override fun write(name: String, command: String) {
        Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, REG_KEY, name, command)
    }

    override fun delete(name: String) {
        if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, REG_KEY, name)) {
            Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, REG_KEY, name)
        }
    }
}

internal class WindowsAutostart(
    private val registry: AutostartRegistry = HkcuRunRegistry,
    private val executable: () -> String? = { currentExecutablePath() },
) : Autostart {
    override fun setEnabled(enabled: Boolean) {
        if (!enabled) {
            runCatching { registry.delete(APP_NAME) }.onFailure { it.printStackTrace() }
            return
        }
        val exe = executable()?.takeIf { File(it).isFile }
        if (exe == null) {
            // Never write a path that does not exist: Windows would list the app as enabled at
            // logon and start nothing at all.
            System.err.println("[Autostart] executable not found; keeping the preference for later")
            return
        }
        runCatching { registry.write(APP_NAME, "\"$exe\" $MINIMIZED_FLAG") }
            .onFailure { it.printStackTrace() }
    }

    /** Only a command that still resolves to an existing executable counts as enabled. */
    override fun isEnabled(): Boolean {
        val command = runCatching { registry.read(APP_NAME) }.getOrNull() ?: return false
        val target = registeredExecutable(command) ?: return false
        return File(target).isFile
    }
}

/** First token of a `Run` command, quoted when the path contains spaces. */
private fun registeredExecutable(command: String): String? {
    val trimmed = command.trim()
    if (trimmed.isEmpty()) return null
    if (!trimmed.startsWith('"')) return trimmed.substringBefore(' ')
    val end = trimmed.indexOf('"', startIndex = 1)
    if (end <= 1) return null
    return trimmed.substring(1, end)
}

/**
 * jpackage runs the JVM from `<App>/runtime/bin/java.exe`; the launcher worth registering is
 * `<App>/Yabai OCR.exe`, one directory above `runtime`.
 */
private fun jpackageLauncher(launcherProcess: File): File? {
    val appDir = launcherProcess.parentFile?.parentFile?.parentFile ?: return null
    return File(appDir, LAUNCHER_NAME).takeIf { it.isFile }
}

private fun isJavaLauncher(file: File): Boolean =
    file.name.equals("java.exe", ignoreCase = true) || file.name.equals("javaw.exe", ignoreCase = true)

internal fun currentExecutablePath(): String? {
    val command = ProcessHandle.current().info().command().orElse(null)
    if (command != null) {
        val file = File(command)
        // Case 1: the process is the application launcher itself.
        if (file.extension.equals("exe", ignoreCase = true) && !isJavaLauncher(file) && file.isFile) {
            return file.absolutePath
        }
        // Case 2: the process is the runtime embedded by jpackage.
        jpackageLauncher(file)?.let { return it.absolutePath }
    }

    // Case 3: known launcher locations, only when the file is really there.
    val userDir = File(System.getProperty("user.dir"))
    val candidates = listOfNotNull(
        File(userDir, LAUNCHER_NAME),
        System.getenv("LOCALAPPDATA")?.let { File(it, "Programs\\Yabai OCR\\$LAUNCHER_NAME") },
        System.getenv("ProgramFiles")?.let { File(it, "Yabai OCR\\$LAUNCHER_NAME") },
    )
    return candidates.firstOrNull { it.isFile }?.absolutePath
}
