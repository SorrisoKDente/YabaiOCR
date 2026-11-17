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
    // `wmClass` stays before `executableResolver`: callers pass the resolver as a trailing lambda.
    private val wmClass: () -> String? = { runningWmClass() },
    private val executableResolver: () -> String? = { resolveLinuxExecutable() },
) : Autostart {

    internal val entryFile: File
        get() = File(File(configHome, "autostart"), "yabai-ocr.desktop")

    override fun setEnabled(enabled: Boolean) {
        if (!enabled) {
            runCatching { entryFile.delete() }
            return
        }
        writeEntry()
    }

    override fun isEnabled(): Boolean = entryFile.isFile

    /**
     * Keeps an existing entry current, not just present.
     *
     * An entry that exists but names an older AppImage is as broken as a missing one: at login it
     * starts that older build, and that build then rewrites the menu entry back to itself — which is
     * why «Integrar ao sistema» looked like it had no effect after an update. [Autostart] already
     * documents this drift; the Linux side used to check only whether the file existed. Whether the
     * entry should exist at all stays with [setEnabled]: only the "start with the system" switch
     * creates or deletes it.
     */
    override fun reconcile(enabled: Boolean): Boolean {
        if (enabled) writeEntry()
        return super.reconcile(enabled)
    }

    /** Single writer: creates the entry and refreshes its contents, skipping an unchanged file. */
    private fun writeEntry() {
        val executable = executableResolver()
        if (executable.isNullOrBlank()) {
            System.err.println("[Autostart] executable not found; keeping the preference for later")
            return
        }

        runCatching {
            entryFile.parentFile?.mkdirs()
            val content = autostartDesktopEntry(executable, wmClass())
            if (!entryFile.isFile || entryFile.readText() != content) entryFile.writeText(content)
        }.onFailure {
            System.err.println("[Autostart] could not write ${entryFile.path}: ${it.message}")
        }
    }
}
