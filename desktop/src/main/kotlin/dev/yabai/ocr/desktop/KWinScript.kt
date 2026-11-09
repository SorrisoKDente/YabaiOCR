package dev.yabai.ocr.desktop

import java.io.File

/**
 * The KWin (KDE Wayland) helper script: source, generation of the fallback-shortcut block and
 * installation under `~/.local/share/kwin/scripts/yabai-ocr/`.
 *
 * The script lives in the app resources and is written to disk only when the content changed, so an
 * update ships a new version without asking the user to delete anything. The user's own files
 * outside that folder are never touched.
 */
internal object KWinScript {
    const val PLUGIN_NAME = "yabai-ocr"
    const val SERVICE_NAME = "dev.yabai.Ocr"
    const val HOVER_PATH = "/dev/yabai/Ocr/Hover"
    const val HOVER_INTERFACE = "dev.yabai.Ocr.Hover"
    const val SHORTCUTS_PATH = "/dev/yabai/Ocr/Shortcuts"
    const val SHORTCUTS_INTERFACE = "dev.yabai.Ocr.Shortcuts"
    const val MIN_INTERVAL_MS = 100

    private const val METADATA = "metadata.json"
    private const val MAIN_SCRIPT = "code/main.js"

    fun metadata(): String = readResource(METADATA)

    fun baseMainScript(): String = readResource(MAIN_SCRIPT)

    /**
     * Hover-only script when the portal owns the shortcuts; with [fallbackShortcuts] the script also
     * registers them in KWin, which is the only path when `GlobalShortcuts` is unavailable.
     */
    fun mainScript(fallbackShortcuts: List<HotkeyBinding>): String {
        val base = baseMainScript()
        val block = fallbackShortcutBlock(fallbackShortcuts)
        return if (block.isEmpty()) base else base + "\n" + block
    }

    internal fun fallbackShortcutBlock(bindings: List<HotkeyBinding>): String {
        val lines = bindings.mapNotNull { binding ->
            val trigger = toKWinShortcut(binding.configured) ?: return@mapNotNull null
            val functionName = "yabaiOcr" + binding.id.split('-', '_')
                .joinToString("") { part -> part.replaceFirstChar { it.uppercase() } }
            "registerShortcut(\"$functionName\", \"${escapeJs(binding.description)}\", " +
                "\"$trigger\", () => notifyShortcut(\"${binding.id}\"));"
        }
        if (lines.isEmpty()) return ""

        return buildString {
            appendLine("// Fallback shortcuts: used only when the XDG portal could not bind them.")
            appendLine("function notifyShortcut(id) {")
            appendLine("    callDBus(")
            appendLine("        \"$SERVICE_NAME\",")
            appendLine("        \"$SHORTCUTS_PATH\",")
            appendLine("        \"$SHORTCUTS_INTERFACE\",")
            appendLine("        \"ShortcutPressed\",")
            appendLine("        id")
            appendLine("    );")
            appendLine("}")
            appendLine()
            lines.forEach { appendLine(it) }
        }
    }

    /** Writes the script under [directory]; returns the two files actually written/compared. */
    fun install(directory: File, fallbackShortcuts: List<HotkeyBinding>): List<File> {
        val metadataFile = File(directory, METADATA)
        val scriptFile = File(directory, MAIN_SCRIPT)
        writeIfChanged(metadataFile, metadata())
        writeIfChanged(scriptFile, mainScript(fallbackShortcuts))
        return listOf(metadataFile, scriptFile)
    }

    private fun writeIfChanged(file: File, content: String) {
        if (file.isFile && file.readText() == content) return
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    private fun readResource(name: String): String {
        val stream = KWinScript::class.java.getResourceAsStream("/kwin/yabai-ocr/$name")
            ?: error("recurso KWin ausente: $name")
        return stream.bufferedReader().use { it.readText() }
    }

    private fun escapeJs(text: String): String =
        text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}

/**
 * Converts the stored shortcut to the QKeySequence string `registerShortcut` expects.
 *
 * Returns null for combinations KWin cannot represent; those entries are simply not registered.
 */
internal fun toKWinShortcut(raw: String): String? {
    val spec = HotkeyMatcher.parse(raw) ?: return null
    if (!spec.ctrl && !spec.alt && !spec.shift && !spec.meta) return null

    val key = kwinKeyName(spec.key) ?: return null
    return buildList {
        if (spec.ctrl) add("Ctrl")
        if (spec.alt) add("Alt")
        if (spec.shift) add("Shift")
        if (spec.meta) add("Meta")
        add(key)
    }.joinToString("+")
}

private fun kwinKeyName(key: String): String? {
    val normalized = key.trim().lowercase().replace(" ", "").replace("-", "").replace("_", "")
    if (normalized.length == 1) {
        val char = normalized[0]
        return when (char) {
            in 'a'..'z' -> char.uppercase()
            in '0'..'9' -> char.toString()
            else -> null
        }
    }
    KWIN_KEYS[normalized]?.let { return it }
    if (normalized.startsWith("f")) {
        val number = normalized.drop(1).toIntOrNull() ?: return null
        if (number in 1..24) return "F$number"
    }
    return null
}

private val KWIN_KEYS = mapOf(
    "space" to "Space",
    "enter" to "Return",
    "return" to "Return",
    "tab" to "Tab",
    "esc" to "Esc",
    "escape" to "Esc",
    "backspace" to "Backspace",
    "delete" to "Delete",
    "insert" to "Insert",
    "home" to "Home",
    "end" to "End",
    "pageup" to "PgUp",
    "pagedown" to "PgDown",
    "up" to "Up",
    "down" to "Down",
    "left" to "Left",
    "right" to "Right",
    "printscreen" to "Print",
    "pause" to "Pause",
)
