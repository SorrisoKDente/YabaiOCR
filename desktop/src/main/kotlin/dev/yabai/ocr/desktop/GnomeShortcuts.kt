package dev.yabai.ocr.desktop

/**
 * GNOME keybinding names for the shortcuts the Settings window stores.
 *
 * GNOME's portal has no `GlobalShortcuts` implementation (Ubuntu 24.04 / GNOME 46 answers
 * `UnknownMethod`), so on GNOME the Shell extension owns the accelerators — the same scheme the KWin
 * script already uses on KDE. The extension asks the app for `id → accelerator` and registers each
 * one with `Main.keybindingManager`, then calls `ShortcutPressed(id)` back over D-Bus.
 *
 * The stored form is what the user reads («Ctrl + Shift + O»); GNOME wants «<Control><Shift>o».
 */

private val FUNCTION_KEY = Regex("""^f([1-9]|1[0-2])$""")

private val NAMED_KEYS = setOf(
    "space", "comma", "period", "slash", "semicolon", "apostrophe", "bracketleft", "bracketright",
    "backslash", "minus", "equal", "grave", "tab", "return", "escape", "delete", "home", "end",
    "pageup", "pagedown", "insert", "up", "down", "left", "right",
)

/** GNOME accelerator for [binding], or null when it cannot be expressed (empty, unknown key). */
internal fun gnomeAccelerator(binding: String): String? {
    val parts = binding.split('+').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null

    val key = parts.last()
    val lower = key.lowercase()
    val keyName = when {
        // GNOME writes letters and digits lower case, but keeps function keys upper case (`F4`).
        key.length == 1 && key[0].isLetterOrDigit() -> lower
        lower in NAMED_KEYS -> lower
        FUNCTION_KEY.matches(lower) -> "F" + lower.drop(1)
        else -> return null
    }

    val modifiers = parts.dropLast(1).map { modifier ->
        when (modifier.lowercase()) {
            "ctrl", "control" -> "<Control>"
            "shift" -> "<Shift>"
            "alt", "option" -> "<Alt>"
            "super", "meta", "win", "windows", "cmd", "command" -> "<Super>"
            else -> return null
        }
    }.distinct()

    return modifiers.joinToString(separator = "") + keyName
}

/** Id → accelerator for every shortcut the Shell extension should own. */
internal fun gnomeShortcutBindings(bindings: List<HotkeyBinding>): Map<String, String> =
    bindings.mapNotNull { binding ->
        gnomeAccelerator(binding.configured)?.let { binding.id to it }
    }.toMap()
