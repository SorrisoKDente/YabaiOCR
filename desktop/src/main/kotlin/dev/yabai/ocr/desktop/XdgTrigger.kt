package dev.yabai.ocr.desktop

/**
 * Converts a stored shortcut (`Ctrl + Shift + O`) into the format the XDG
 * `org.freedesktop.portal.GlobalShortcuts` portal expects (`CTRL+SHIFT+O`).
 *
 * The portal only accepts shortcuts with at least one modifier and a keysym name it knows. When the
 * conversion is not possible the value comes back as null and the shortcut is **not** sent to the
 * portal: the UI then says it is unsupported on Wayland instead of silently binding nothing.
 */
internal fun toXdgTrigger(raw: String): String? {
    val spec = HotkeyMatcher.parse(raw) ?: return null
    if (!spec.ctrl && !spec.alt && !spec.shift && !spec.meta) return null

    val key = xdgKeysym(spec.key) ?: return null
    return buildList {
        if (spec.ctrl) add("CTRL")
        if (spec.alt) add("ALT")
        if (spec.shift) add("SHIFT")
        if (spec.meta) add("LOGO")
        add(key)
    }.joinToString("+")
}

/** Keysym name used by XDG shortcuts for a key already normalised by [HotkeyMatcher.parse]. */
internal fun xdgKeysym(key: String): String? {
    val normalized = key.trim().lowercase().replace(" ", "").replace("-", "").replace("_", "")
    if (normalized.length == 1) {
        val char = normalized[0]
        return when (char) {
            in 'a'..'z' -> char.uppercase()
            in '0'..'9' -> char.toString()
            else -> null
        }
    }

    XDG_KEYS[normalized]?.let { return it }
    if (normalized.startsWith("f")) {
        val number = normalized.drop(1).toIntOrNull() ?: return null
        if (number in 1..24) return "F$number"
    }
    return null
}

/**
 * Keys the portal can name. It mirrors the keys `HotkeyMatcher` understands; punctuation that only
 * works through the layout is deliberately absent, because the keysym would depend on the layout.
 */
private val XDG_KEYS = mapOf(
    "space" to "space",
    "enter" to "Return",
    "return" to "Return",
    "tab" to "Tab",
    "esc" to "Escape",
    "escape" to "Escape",
    "backspace" to "BackSpace",
    "delete" to "Delete",
    "insert" to "Insert",
    "home" to "Home",
    "end" to "End",
    "pageup" to "Page_Up",
    "pagedown" to "Page_Down",
    "up" to "Up",
    "down" to "Down",
    "left" to "Left",
    "right" to "Right",
    "printscreen" to "Print",
    "pause" to "Pause",
)
