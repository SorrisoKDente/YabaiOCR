package dev.yabai.ocr.ocr

import android.graphics.Bitmap

/** What the UI needs to know about one recognition run. */
data class OcrOutcome(
    val text: String,
    val lines: List<String>,
    val engineLabel: String,
    val language: String?,
    val elapsedMs: Long,
    /** True when the preferred engine failed and an offline one answered instead. */
    val fallback: Boolean = false,
    val warning: String? = null,
)

interface OcrEngine {
    val label: String
    val requiresNetwork: Boolean
    suspend fun recognize(bitmap: Bitmap): OcrOutcome
    fun close() = Unit
}
