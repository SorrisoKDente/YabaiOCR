package dev.yabai.ocr.desktop

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor

class ClipboardMonitor(
    private val onJapaneseTextCopied: (String) -> Unit
) {
    private var running = false
    private var lastText = ""
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread({
            while (running) {
                try {
                    val clip = Toolkit.getDefaultToolkit().systemClipboard
                    val text = runCatching {
                        clip.getData(DataFlavor.stringFlavor) as? String
                    }.getOrNull()

                    if (!text.isNullOrBlank() && text != lastText) {
                        val hasJapanese = text.any { ch ->
                            val code = ch.code
                            code in 0x3040..0x309F || // Hiragana
                            code in 0x30A0..0x30FF || // Katakana
                            code in 0x4E00..0x9FAF    // CJK Unified Ideographs (Kanji)
                        }
                        if (hasJapanese) {
                            lastText = text
                            onJapaneseTextCopied(text.trim())
                        }
                    }
                } catch (_: Exception) {}
                Thread.sleep(800)
            }
        }, "Yabai-ClipboardMonitor").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
    }
}
