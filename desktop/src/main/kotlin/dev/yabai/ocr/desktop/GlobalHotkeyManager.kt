package dev.yabai.ocr.desktop

import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import java.util.logging.Level
import java.util.logging.Logger

class GlobalHotkeyManager(
    private val onOcrTriggered: () -> Unit,
    private val onQuickDictTriggered: () -> Unit
) : NativeKeyListener {

    private var ocrHotkey: String = "Ctrl + Shift + O"
    private var quickDictHotkey: String = "Ctrl + Shift + D"

    fun updateHotkeys(ocr: String, quickDict: String) {
        ocrHotkey = ocr
        quickDictHotkey = quickDict
    }

    fun start() {
        try {
            val logger = Logger.getLogger(GlobalScreen::class.java.packageName)
            logger.level = Level.OFF
            logger.useParentHandlers = false

            if (!GlobalScreen.isNativeHookRegistered()) {
                GlobalScreen.registerNativeHook()
            }
            GlobalScreen.addNativeKeyListener(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        try {
            GlobalScreen.removeNativeKeyListener(this)
            if (GlobalScreen.isNativeHookRegistered()) {
                GlobalScreen.unregisterNativeHook()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun nativeKeyPressed(e: NativeKeyEvent) {
        if (matchesHotkey(e, ocrHotkey)) {
            onOcrTriggered()
        } else if (matchesHotkey(e, quickDictHotkey)) {
            onQuickDictTriggered()
        }
    }

    private fun matchesHotkey(e: NativeKeyEvent, targetHotkey: String): Boolean {
        val parts = targetHotkey.split(" + ").map { it.trim().lowercase() }
        if (parts.isEmpty()) return false

        val mainKeyPart = parts.last()
        val requiresCtrl = parts.contains("ctrl")
        val requiresAlt = parts.contains("alt")
        val requiresShift = parts.contains("shift")
        val requiresMeta = parts.contains("meta")

        val ctrlPressed = (e.modifiers and NativeKeyEvent.CTRL_MASK) != 0
        val altPressed = (e.modifiers and NativeKeyEvent.ALT_MASK) != 0
        val shiftPressed = (e.modifiers and NativeKeyEvent.SHIFT_MASK) != 0
        val metaPressed = (e.modifiers and NativeKeyEvent.META_MASK) != 0

        if (requiresCtrl != ctrlPressed) return false
        if (requiresAlt != altPressed) return false
        if (requiresShift != shiftPressed) return false
        if (requiresMeta != metaPressed) return false

        val pressedKeyText = NativeKeyEvent.getKeyText(e.keyCode).lowercase()
        return pressedKeyText == mainKeyPart || matchKeyName(e.keyCode, mainKeyPart)
    }

    private fun matchKeyName(keyCode: Int, target: String): Boolean {
        return when (target) {
            "space", "espaço" -> keyCode == NativeKeyEvent.VC_SPACE
            "enter" -> keyCode == NativeKeyEvent.VC_ENTER
            "esc", "escape" -> keyCode == NativeKeyEvent.VC_ESCAPE
            else -> false
        }
    }
}
