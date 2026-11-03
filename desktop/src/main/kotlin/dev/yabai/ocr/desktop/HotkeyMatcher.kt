package dev.yabai.ocr.desktop

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent

/**
 * A hotkey as the settings file stores it, e.g. `Ctrl + Shift + H`.
 *
 * The string is parsed once instead of being split for every key press: the global listener runs on
 * the native hook thread, and a malformed or half-typed value must never make it throw.
 */
data class HotkeySpec(
    val ctrl: Boolean,
    val alt: Boolean,
    val shift: Boolean,
    val meta: Boolean,
    val key: String,
)

/** The relevant part of a native key event, separated from JNativeHook so the matcher can be tested. */
data class KeyStroke(
    val keyCode: Int,
    val keyText: String,
    val ctrl: Boolean,
    val alt: Boolean,
    val shift: Boolean,
    val meta: Boolean,
)

/**
 * Parses and matches the global shortcuts.
 *
 * JNativeHook virtual-key codes are **not** AWT codes (`VC_A` is 30, not 65), so a shortcut is
 * matched by its printed name first and by a small explicit code table second. That table is what
 * makes `Ctrl + Shift + H` work even when the key text arrives localised ("Page Up" vs "PageUp").
 * Modifiers are compared exactly: a shortcut with Shift must not fire without it.
 */
object HotkeyMatcher {

    private val LETTERS = mapOf(
        'a' to NativeKeyEvent.VC_A, 'b' to NativeKeyEvent.VC_B, 'c' to NativeKeyEvent.VC_C,
        'd' to NativeKeyEvent.VC_D, 'e' to NativeKeyEvent.VC_E, 'f' to NativeKeyEvent.VC_F,
        'g' to NativeKeyEvent.VC_G, 'h' to NativeKeyEvent.VC_H, 'i' to NativeKeyEvent.VC_I,
        'j' to NativeKeyEvent.VC_J, 'k' to NativeKeyEvent.VC_K, 'l' to NativeKeyEvent.VC_L,
        'm' to NativeKeyEvent.VC_M, 'n' to NativeKeyEvent.VC_N, 'o' to NativeKeyEvent.VC_O,
        'p' to NativeKeyEvent.VC_P, 'q' to NativeKeyEvent.VC_Q, 'r' to NativeKeyEvent.VC_R,
        's' to NativeKeyEvent.VC_S, 't' to NativeKeyEvent.VC_T, 'u' to NativeKeyEvent.VC_U,
        'v' to NativeKeyEvent.VC_V, 'w' to NativeKeyEvent.VC_W, 'x' to NativeKeyEvent.VC_X,
        'y' to NativeKeyEvent.VC_Y, 'z' to NativeKeyEvent.VC_Z,
    )

    private val DIGITS = mapOf(
        '0' to NativeKeyEvent.VC_0, '1' to NativeKeyEvent.VC_1, '2' to NativeKeyEvent.VC_2,
        '3' to NativeKeyEvent.VC_3, '4' to NativeKeyEvent.VC_4, '5' to NativeKeyEvent.VC_5,
        '6' to NativeKeyEvent.VC_6, '7' to NativeKeyEvent.VC_7, '8' to NativeKeyEvent.VC_8,
        '9' to NativeKeyEvent.VC_9,
    )

    private val FUNCTION_KEYS = listOf(
        NativeKeyEvent.VC_F1, NativeKeyEvent.VC_F2, NativeKeyEvent.VC_F3,
        NativeKeyEvent.VC_F4, NativeKeyEvent.VC_F5, NativeKeyEvent.VC_F6,
        NativeKeyEvent.VC_F7, NativeKeyEvent.VC_F8, NativeKeyEvent.VC_F9,
        NativeKeyEvent.VC_F10, NativeKeyEvent.VC_F11, NativeKeyEvent.VC_F12,
        NativeKeyEvent.VC_F13, NativeKeyEvent.VC_F14, NativeKeyEvent.VC_F15,
        NativeKeyEvent.VC_F16, NativeKeyEvent.VC_F17, NativeKeyEvent.VC_F18,
        NativeKeyEvent.VC_F19, NativeKeyEvent.VC_F20, NativeKeyEvent.VC_F21,
        NativeKeyEvent.VC_F22, NativeKeyEvent.VC_F23, NativeKeyEvent.VC_F24,
    )

    private val NAMED_KEYS = mapOf(
        "space" to NativeKeyEvent.VC_SPACE,
        "espaço" to NativeKeyEvent.VC_SPACE,
        "enter" to NativeKeyEvent.VC_ENTER,
        "return" to NativeKeyEvent.VC_ENTER,
        "tab" to NativeKeyEvent.VC_TAB,
        "esc" to NativeKeyEvent.VC_ESCAPE,
        "escape" to NativeKeyEvent.VC_ESCAPE,
        "backspace" to NativeKeyEvent.VC_BACKSPACE,
        "delete" to NativeKeyEvent.VC_DELETE,
        "insert" to NativeKeyEvent.VC_INSERT,
        "home" to NativeKeyEvent.VC_HOME,
        "end" to NativeKeyEvent.VC_END,
        "pageup" to NativeKeyEvent.VC_PAGE_UP,
        "pagedown" to NativeKeyEvent.VC_PAGE_DOWN,
        "up" to NativeKeyEvent.VC_UP,
        "down" to NativeKeyEvent.VC_DOWN,
        "left" to NativeKeyEvent.VC_LEFT,
        "right" to NativeKeyEvent.VC_RIGHT,
        "printscreen" to NativeKeyEvent.VC_PRINTSCREEN,
        "pause" to NativeKeyEvent.VC_PAUSE,
    )

    /** Parses a stored shortcut; null means the string could not be understood. */
    fun parse(raw: String): HotkeySpec? {
        val parts = raw.split('+').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null

        var ctrl = false
        var alt = false
        var shift = false
        var meta = false
        var key: String? = null

        for (part in parts) {
            when (normalize(part)) {
                "ctrl", "control" -> ctrl = true
                "alt", "option" -> alt = true
                "shift" -> shift = true
                "meta", "win", "windows", "cmd", "command", "super" -> meta = true
                else -> {
                    // A shortcut has exactly one non-modifier key. "Ctrl + Shift + H + J" is not a
                    // shortcut we can match; refusing it is better than silently using J.
                    if (key != null) return null
                    key = part
                }
            }
        }

        return key?.let {
            HotkeySpec(ctrl = ctrl, alt = alt, shift = shift, meta = meta, key = normalize(it))
        }
    }

    /** True when [stroke] is exactly the shortcut [spec] describes. */
    fun matches(spec: HotkeySpec?, stroke: KeyStroke): Boolean {
        if (spec == null) return false
        if (spec.ctrl != stroke.ctrl) return false
        if (spec.alt != stroke.alt) return false
        if (spec.shift != stroke.shift) return false
        if (spec.meta != stroke.meta) return false

        val wanted = normalize(spec.key)
        val code = keyCodeFor(wanted)
        if (code != null && stroke.keyCode == code) return true

        // Fallback for keys the table does not name (layout-specific punctuation, media keys).
        // Both sides are normalised, so "Page Up" and "PageUp" are the same key.
        return normalize(stroke.keyText) == wanted
    }

    /** JNativeHook virtual-key code for a shortcut's key name, or null when it is not in the table. */
    fun keyCodeFor(name: String): Int? {
        val key = normalize(name)
        if (key.length == 1) {
            LETTERS[key[0]]?.let { return it }
            DIGITS[key[0]]?.let { return it }
        }
        NAMED_KEYS[key]?.let { return it }
        if (key.startsWith("f")) {
            val number = key.drop(1).toIntOrNull() ?: return null
            if (number in 1..FUNCTION_KEYS.size) return FUNCTION_KEYS[number - 1]
        }
        return null
    }

    private fun normalize(text: String): String =
        text.trim().lowercase().replace(" ", "").replace("-", "").replace("_", "")
}
