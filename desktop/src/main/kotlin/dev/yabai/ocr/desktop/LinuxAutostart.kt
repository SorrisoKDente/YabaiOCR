package dev.yabai.ocr.desktop

import java.io.File

/**
 * XDG autostart for Linux. Writes `$XDG_CONFIG_HOME/autostart/yabai-ocr.desktop` with the real
 * executable and `--minimized`.
 *
 * A missing executable must not produce a broken entry: the file is only written when the resolver
 * finds something that exists, otherwise the preference is kept and retried on the next launch.
 */
internal class LinuxAutostart(
    private val configHome: File = defaultConfigHome(),
    private val executableResolver: () -> String? = { resolveLinuxExecutable() },
) : Autostart {

    internal val entryFile: File
        get() = File(File(configHome, "autostart"), "yabai-ocr.desktop")

    override fun setEnabled(enabled: Boolean) {
        if (!enabled) {
            runCatching { entryFile.delete() }
            return
        }

        val executable = executableResolver()
        if (executable.isNullOrBlank()) {
            System.err.println("[Autostart] executable not found; keeping the preference for later")
            return
        }

        runCatching {
            entryFile.parentFile?.mkdirs()
            entryFile.writeText(autostartDesktopEntry(executable))
        }.onFailure {
            System.err.println("[Autostart] could not write ${entryFile.path}: ${it.message}")
        }
    }

    override fun isEnabled(): Boolean = entryFile.isFile
}
